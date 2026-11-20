package idle.miau.module.impl.player;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PostPlayerInputEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.bridgeassist.TellyMode;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.script.api.MiauScriptMode;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import idle.miau.module.setting.impl.SliderSetting;

import java.util.ArrayList;

public class BridgeAssist extends Module {
    private static final String[] MODES = {"Silent", "Telly"};
    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    private final SliderSetting mode;
    private final GroupSetting conditions;
    private final ButtonSetting lookingDown;
    private final ButtonSetting notMovingForward;
    private final TellyMode tellyMode;
    private MiauScriptMode activeScriptMode;
    private int visibleMode = -1;

    public BridgeAssist() {
        super("BridgeAssist", category.player);
        setDescription("Help you bridge automatically.");
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        conditions = new GroupSetting("Conditions");
        registerSetting(conditions);
        registerSetting(lookingDown = new ButtonSetting(conditions, "Looking down", false));
        registerSetting(notMovingForward = new ButtonSetting(conditions, "Not moving forward", false));
        tellyMode = new TellyMode(this);
        updateSettingVisibility();
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        synchronizeScriptMode();
    }

    @Override
    public void onDisable() {
        if (activeScriptMode != null) {
            activeScriptMode.deactivate();
            activeScriptMode = null;
        }
    }

    @Override
    public void guiUpdate() {
        synchronizeScriptMode();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) synchronizeScriptMode();
    }

    @Override
    public void onCommandSettingChanged(idle.miau.module.setting.Setting setting) {
        if (setting == mode) synchronizeScriptMode();
    }

    @Subscribe
    public void onClientRotation(ClientRotationEvent e) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) {
            scriptMode.miau$rotations(e);
            return;
        }
        if (!Utils.nullCheck() || mc.currentScreen != null
                || mc.thePlayer.capabilities.isFlying) return;
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        if (lookingDown.isToggled() && mc.thePlayer.rotationPitch < 70.0F) return;
        if (notMovingForward.isToggled() && mc.thePlayer.movementInput.moveForward > 0.0F) return;

        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        double reach = mc.playerController.getBlockReachDistance();
        TargetResult target = findTarget(basePitch, reach);
        if (target == null) return;

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float[] smooth = RotationUtils.smoothRotation(baseYaw, basePitch,
                target.yaw, target.pitch, 15, 20.0F);
        e.setYaw(smooth[0]);
        e.setPitch(smooth[1]);
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$preUpdate(event);
    }

    @Subscribe
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$postPlayerInput(event);
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$preMotion(event);
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$postMotion(event);
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$sendPacket(event);
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$receivePacket(event);
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$renderWorld(event);
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$renderTick(event);
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$worldJoin(event);
    }

    @Subscribe
    public void onMouse(MouseEvent event) {
        MiauScriptMode scriptMode = scriptMode();
        if (scriptMode != null) scriptMode.miau$mouse(event);
    }

    private MiauScriptMode scriptMode() {
        synchronizeScriptMode();
        return activeScriptMode;
    }

    private void synchronizeScriptMode() {
        updateSettingVisibility();
        MiauScriptMode selected = null;
        int selectedMode = (int) mode.getInput();
        if (selectedMode == 1) selected = tellyMode;
        if (selected == activeScriptMode) return;
        if (activeScriptMode != null) activeScriptMode.deactivate();
        activeScriptMode = selected;
        if (activeScriptMode != null && isEnabled()) activeScriptMode.activate();
    }

    private void updateSettingVisibility() {
        int selectedMode = (int) mode.getInput();
        if (selectedMode == visibleMode) return;
        boolean silent = selectedMode == 0;
        lookingDown.visible = silent;
        notMovingForward.visible = silent;
        conditions.setVisible(silent, this);
        tellyMode.setSettingsVisible(selectedMode == 1);
        visibleMode = selectedMode;
    }

    private TargetResult findTarget(float currentPitch, double reach) {
        float yaw = mc.thePlayer.rotationYaw;
        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox();
        int standY = MathHelper.floor_double(bbox.minY) - 1;
        int minX = MathHelper.floor_double(bbox.minX);
        int maxX = MathHelper.floor_double(bbox.maxX);
        int minZ = MathHelper.floor_double(bbox.minZ);
        int maxZ = MathHelper.floor_double(bbox.maxZ);

        ArrayList<FaceTarget> targets = new ArrayList<FaceTarget>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos support = new BlockPos(x, standY, z);
                if (BlockUtils.replaceable(support)) continue;
                for (EnumFacing face : SIDES) {
                    if (BlockUtils.replaceable(support.offset(face))) {
                        targets.add(new FaceTarget(support, face));
                    }
                }
            }
        }
        if (targets.isEmpty()) return null;

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        EnumFacing bestFace = null;
        float randScale = 0.2F;
        float pitch = 60.0F;
        while (pitch <= 90.0F) {
            float samplePitch = Math.min(pitch, 90.0F);
            MovingObjectPosition mop = RotationUtils.rayCastBlock(reach, yaw, samplePitch);
            if (mop != null && mop.sideHit != EnumFacing.UP && mop.sideHit != EnumFacing.DOWN) {
                for (FaceTarget target : targets) {
                    if (mop.getBlockPos().equals(target.block) && mop.sideHit == target.face) {
                        float delta = Math.abs(samplePitch - currentPitch);
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            bestPitch = samplePitch;
                            bestSupport = target.block;
                            bestFace = target.face;
                        }
                        break;
                    }
                }
            }
            float step = 1.0F + (float) (Math.random() * 2.0D - 1.0D)
                    * (0.3F + randScale * 0.4F);
            pitch += Math.max(0.4F, Math.min(1.8F, step));
        }
        return bestSupport == null || bestFace == null || Float.isNaN(bestPitch)
                ? null : new TargetResult(yaw, bestPitch, bestSupport, bestFace);
    }

    private static final class FaceTarget {
        final BlockPos block;
        final EnumFacing face;
        FaceTarget(BlockPos block, EnumFacing face) {
            this.block = block;
            this.face = face;
        }
    }

    private static final class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final EnumFacing face;
        TargetResult(float yaw, float pitch, BlockPos support, EnumFacing face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
