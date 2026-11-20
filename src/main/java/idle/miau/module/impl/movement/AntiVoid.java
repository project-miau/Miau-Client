package idle.miau.module.impl.movement;

import idle.miau.Miau;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.RightClickMouseEvent;
import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.timeout.ModuleBackedTimeout;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.PlayerUtils;
import idle.miau.utility.Utils;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

public final class AntiVoid extends Module {
    private static final String[] MODES = {"Blink", "Packet"};

    private final SliderSetting mode;
    private final SliderSetting distance;
    private final SliderSetting minimumFallDistance;

    private boolean inVoid;
    private boolean wasInVoid;
    private double[] lastSafePosition;
    private LagRequest lagRequest;

    public AntiVoid() {
        super("AntiVoid", category.movement);
        setDescription("Tries to recover your position when falling into void.");
        this.registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(distance = new SliderSetting(
                "Distance", " blocks", 5.0D, 0.0D, 16.0D, 0.1D));
        this.registerSetting(minimumFallDistance = new SliderSetting(
                "Min fall distance", 3.0D, 0.0D, 10.0D, 1.0D));
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        releaseBlink();
        inVoid = false;
        wasInVoid = false;
    }

    @Override
    public void guiUpdate() {
        boolean packet = (int) mode.getInput() == 1;
        distance.setVisible(!packet, this);
        minimumFallDistance.setVisible(packet, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        guiUpdate();
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            resetState();
            return;
        }
        if ((int) mode.getInput() == 1) {
            resetState();
            return;
        }

        inVoid = !mc.thePlayer.capabilities.allowFlying
                && isAboveVoid(mc.thePlayer.getEntityBoundingBox()
                .expand(-1.0E-6D, 0.0D, -1.0E-6D));

        if (!inVoid) {
            releaseBlink();
        }

        if (lastSafePosition != null && isAboveVoid(createSafePositionBox())) {
            releaseBlink();
        }

        if (!wasInVoid && inVoid && !LongJump.function) {
            startBlink();
        }

        if (lagRequest != null
                && lastSafePosition != null
                && mc.thePlayer.posY < lastSafePosition[1] - distance.getInput()) {
            double setbackY = lastSafePosition[1]
                    - ThreadLocalRandom.current().nextDouble(10.0D, 20.0D);
            PacketUtils.sendPacketNoEvent(new C04PacketPlayerPosition(
                    lastSafePosition[0], setbackY, lastSafePosition[2], false
            ));
            releaseBlink();
        }

        wasInVoid = inVoid;
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent event) {
        if ((int) mode.getInput() == 1 && Utils.nullCheck()
                && PlayerUtils.overVoid()
                && mc.thePlayer.fallDistance >= minimumFallDistance.getInput()) {
            PacketUtils.sendPacket(new C04PacketPlayerPosition());
        }
    }

    @Subscribe
    public void onRightClick(RightClickMouseEvent event) {
        if ((int) mode.getInput() == 1 || !Utils.nullCheck()) {
            return;
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem != null && heldItem.getItem() instanceof ItemEnderPearl) {
            releaseBlink();
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetState();
    }

    private void startBlink() {
        releaseBlink();
        if (Miau.lagHandler == null) {
            return;
        }
        lastSafePosition = new double[]{
                mc.thePlayer.prevPosX,
                mc.thePlayer.prevPosY,
                mc.thePlayer.prevPosZ
        };
        lagRequest = new LagRequest(
                EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Miau.lagHandler.requestLag(lagRequest);
    }

    private void releaseBlink() {
        if (lagRequest != null) {
            lagRequest.getTimeout().forceTimeOut();
            lagRequest = null;
        }
        lastSafePosition = null;
    }

    private void resetState() {
        releaseBlink();
        inVoid = false;
        wasInVoid = false;
    }

    private AxisAlignedBB createSafePositionBox() {
        double halfWidth = mc.thePlayer.width / 2.0D;
        return new AxisAlignedBB(
                lastSafePosition[0] - halfWidth,
                lastSafePosition[1],
                lastSafePosition[2] - halfWidth,
                lastSafePosition[0] + halfWidth,
                lastSafePosition[1] + mc.thePlayer.height,
                lastSafePosition[2] + halfWidth
        );
    }

    private boolean isAboveVoid(AxisAlignedBB boundingBox) {
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return false;
        }

        int minimumY = MathHelper.floor_double(boundingBox.minY);
        if (minimumY < 0) {
            return true;
        }

        int minimumX = MathHelper.floor_double(boundingBox.minX);
        int maximumX = MathHelper.floor_double(boundingBox.maxX + 1.0D);
        int minimumZ = MathHelper.floor_double(boundingBox.minZ);
        int maximumZ = MathHelper.floor_double(boundingBox.maxZ + 1.0D);
        for (int x = minimumX; x < maximumX; x++) {
            for (int z = minimumZ; z < maximumZ; z++) {
                for (int y = minimumY; y >= 0; y--) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (!mc.theWorld.getBlockState(position).getBlock()
                            .isReplaceable(mc.theWorld, position)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
