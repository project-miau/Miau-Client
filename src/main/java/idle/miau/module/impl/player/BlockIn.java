package idle.miau.module.impl.player;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.MouseEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.PlayerStateTracker;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockLever;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BlockIn extends Module {
    private static final EnumFacing[] HORIZONTALS = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH
    };
    private static final EnumFacing[] SUPPORT_ORDER = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST,
            EnumFacing.EAST, EnumFacing.UP, EnumFacing.DOWN
    };
    private static final EnumFacing[] SUPPORT_ORDER_UP = {
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN
    };
    private static final int MANUAL_OPENING_GRACE_TICKS = 20;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_TARGET_ATTEMPTS = 5;
    private static final long PERCENT_FADE_MS = 125L;

    private final SliderSetting delay;
    private final KeySetting selectKeybind;
    private final ButtonSetting disableWhenComplete;
    private final ButtonSetting showPercentage;
    private final ButtonSetting skipNearBed;
    private final ButtonSetting ignoreBlocksToggle;
    private final ItemListSetting ignoredBlocks;
    private final Deque<BlockPos> retry = new ArrayDeque<>();
    private final Set<BlockPos> retrySet = new HashSet<>();
    private final Set<BlockPos> failed = new HashSet<>();

    private boolean placing;
    private boolean waitRelease;
    private EntityPlayerSP sessionPlayer;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private BlockPos anchor;
    private EnumFacing[] facings;
    private List<BlockPos> path;
    private AimResult target;
    private boolean placeQueued;
    private int queuedTick = -1;
    private float fromYaw;
    private float fromPitch;
    private int steps;
    private int step;
    private boolean roundUp = true;
    private Vec3 manualBreakAim;
    private BlockPos protectedOpening;
    private int manualOpeningUntilTick;
    private String percentText;
    private int percentColor;
    private long startedAt;
    private long stoppedAt;

    public BlockIn() {
        super("BlockIn", category.player);
        setDescription("Automatically enclose yourself with blocks.");
        registerSetting(delay = new SliderSetting("Delay", "ticks", 1, 0, 20, 0.5));
        registerSetting(selectKeybind = new KeySetting("Select Keybind", 0));
        registerSetting(disableWhenComplete = new ButtonSetting("Disable when complete", false));
        registerSetting(showPercentage = new ButtonSetting("Show percentage", true));
        registerSetting(skipNearBed = new ButtonSetting("Skip near bed", true));
        registerSetting(ignoreBlocksToggle = new ButtonSetting("Ignore blocks", false));
        registerSetting(ignoredBlocks = new ItemListSetting("Items"));
        closetModule = true;
    }

    @Override
    public void onDisable() {
        stopPlacing();
        waitRelease = false;
        percentText = null;
        manualBreakAim = null;
        protectedOpening = null;
        manualOpeningUntilTick = 0;
    }

    @Override
    public void guiUpdate() {
        ignoredBlocks.setVisible(ignoreBlocksToggle.isToggled(), this);
    }

    @Subscribe(priority = Priority.HIGH)
    public void onClientRotation(ClientRotationEvent event) {
        placeQueued = false;
        if (!canRun()) {
            stopPlacing();
            return;
        }
        refreshProtectedOpening(mc.gameSettings.keyBindAttack.isKeyDown());
        if (waitRelease || isMining() || PlayerStateTracker.attacking || PlayerStateTracker.digging
                || !event.canClaimRotation(this, RotationPriority.BLOCK_PLACEMENT)) {
            stopPlacing();
            return;
        }
        if (placing && sessionPlayer != mc.thePlayer) stopPlacing();
        if (!placing && !startPlacing()) return;
        if (!equipBlock()) {
            stopPlacing();
            return;
        }
        BlockPos feet = playerPos();
        if (!feet.equals(anchor)) {
            clearTargets();
            anchor = feet;
        }
        failed.clear();
        pruneRetries();
        ItemStack held = mc.thePlayer.getHeldItem();
        for (int attempt = 0; attempt < MAX_TARGET_ATTEMPTS; attempt++) {
            if (target == null || !canTarget(target.destination)) {
                target = null;
                if (!findTarget(held) && !retryTarget(held)) return;
            }
            AimResult aim = aimAtFace(target.destination, target.support, target.face, held);
            if (aim == null) {
                markFailed();
                continue;
            }
            target = aim;
            float aimYaw = RotationHelper.unwrapYaw(aim.yaw, fromYaw);
            boolean waiting = step < steps;
            float progress = waiting ? (float) (step + 1) / steps : 1.0F;
            float[] rotation = RotationUtils.fixRotation(
                    fromYaw + (aimYaw - fromYaw) * progress,
                    fromPitch + (aim.pitch - fromPitch) * progress,
                    RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
            if (!event.trySetRotation(this, RotationPriority.BLOCK_PLACEMENT, rotation[0], rotation[1])) {
                stopPlacing();
                return;
            }
            event.setRotationAlreadyQuantized(true);
            if (waiting) step++;
            if (!waiting && matchesTarget(rayCast(rotation[0], rotation[1]))) {
                placeQueued = true;
                queuedTick = mc.thePlayer.ticksExisted;
            }
            return;
        }
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (!canRun()) {
            stopPlacing();
            return;
        }
        refreshProtectedOpening(mc.gameSettings.keyBindAttack.isKeyDown());
        if (isMining()) {
            stopPlacing();
            return;
        }
        if (placing) updatePercentage();
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        if (!placeQueued) return;
        placeQueued = false;
        if (!canRun() || waitRelease || isMining() || sessionPlayer != mc.thePlayer) {
            stopPlacing();
            return;
        }
        if (!placing || target == null || queuedTick != mc.thePlayer.ticksExisted
                || !RotationHelper.get().isRotationOwner(this)
                || PlayerStateTracker.attacking || PlayerStateTracker.digging
                || PlayerStateTracker.placing || mc.thePlayer.inventory.currentItem != plannedSlot) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (!usable(held) || !canTarget(target.destination) || !isSupport(target.support)) {
            target = null;
            return;
        }
        MovingObjectPosition hit = rayCast(
                RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
        if (!matchesTarget(hit) || !canPlace(held, target.support, target.face)) {
            markFailed();
            return;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                held, target.support, target.face, hit.hitVec)) {
            mc.thePlayer.swingItem();
            if (path != null && !path.isEmpty() && path.get(0).equals(target.destination)) path.remove(0);
            target = null;
            updatePercentage();
        } else {
            markFailed();
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (placing && canRun() && !isMining()) event.suppressAll();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (placing && isActive() && event.buttonstate && event.button > 0) event.setCanceled(true);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        onDisable();
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck() || mc.currentScreen != null
                || !showPercentage.isToggled() || percentText == null) return;
        long elapsed = System.currentTimeMillis() - (placing ? startedAt : stoppedAt);
        float fade = MathHelper.clamp_float((float) elapsed / PERCENT_FADE_MS, 0, 1);
        int alpha = (int) (240 * (placing ? fade : 1 - fade));
        if (alpha <= 0) {
            if (!placing) percentText = null;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float x = resolution.getScaledWidth() / 2.0F - mc.fontRendererObj.getStringWidth(percentText) / 2.0F;
        float y = resolution.getScaledHeight() / 2.0F + 11.0F;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.fontRendererObj.drawString(percentText, x, y, alpha << 24 | percentColor & 0xFFFFFF, true);
        GlStateManager.disableBlend();
    }

    boolean hasPercentageText() {
        return percentText != null;
    }

    public boolean isPlacing() {
        return placing;
    }

    public boolean isActive() {
        return isEnabled() && Utils.nullCheck() && mc.currentScreen == null && selectKeybind.isPressed();
    }

    private boolean canRun() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return false;
        if (!selectKeybind.isPressed()) {
            waitRelease = false;
            return false;
        }
        return isEnabled() && mc.currentScreen == null && mc.inGameHasFocus
                && !mc.playerController.isSpectatorMode()
                && mc.playerController.getCurrentGameType() != WorldSettings.GameType.ADVENTURE
                && !(ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled());
    }

    private boolean isMining() {
        return mc.gameSettings.keyBindAttack.isKeyDown()
                || ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()
                || mc.playerController instanceof IAccessorPlayerControllerMP
                && ((IAccessorPlayerControllerMP) mc.playerController).isHittingBlock();
    }

    private boolean startPlacing() {
        int slot = pickBlockSlot();
        if (slot < 0) return false;
        sessionPlayer = mc.thePlayer;
        prevSlot = sessionPlayer.inventory.currentItem;
        plannedSlot = slot;
        placing = true;
        startedAt = System.currentTimeMillis();
        anchor = playerPos();
        EnumFacing facing = sessionPlayer.getHorizontalFacing();
        facings = new EnumFacing[]{facing, facing.rotateY(), facing.getOpposite(), facing.rotateYCCW()};
        return true;
    }

    private boolean equipBlock() {
        if (plannedSlot < 0 || !usable(mc.thePlayer.inventory.getStackInSlot(plannedSlot))) {
            int slot = pickBlockSlot();
            if (slot < 0) return false;
            plannedSlot = slot;
        }
        mc.thePlayer.inventory.currentItem = plannedSlot;
        return true;
    }

    private void stopPlacing() {
        if (placing) stoppedAt = System.currentTimeMillis();
        if (sessionPlayer != null && sessionPlayer.inventory != null && prevSlot >= 0
                && sessionPlayer.inventory.currentItem == plannedSlot) sessionPlayer.inventory.currentItem = prevSlot;
        placing = false;
        sessionPlayer = null;
        prevSlot = -1;
        plannedSlot = -1;
        anchor = null;
        facings = null;
        roundUp = true;
        clearTargets();
    }

    private void clearTargets() {
        target = null;
        path = null;
        retry.clear();
        retrySet.clear();
        failed.clear();
        placeQueued = false;
        queuedTick = -1;
        step = 0;
    }

    private int pickBlockSlot() {
        int bestSlot = -1;
        int most = 0;
        float bestStrength = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!usable(stack)) continue;
            float strength = BlockUtils.getFistBreakTicks(((ItemBlock) stack.getItem()).getBlock());
            if (strength > bestStrength || strength == bestStrength && stack.stackSize > most) {
                bestSlot = slot;
                bestStrength = strength;
                most = stack.stackSize;
            }
        }
        return bestSlot;
    }

    private boolean usable(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        return BlockUtils.isNormalBlock(block) && block != Blocks.pumpkin && block != Blocks.lit_pumpkin
                && block.getBlockHardness(mc.theWorld, null) >= 0
                && block != Blocks.obsidian
                && !(ignoreBlocksToggle.isToggled() && ignoredBlocks.matches(stack));
    }

    private boolean findTarget(ItemStack held) {
        if (followPath(held)) return true;
        BlockPos feet = playerPos();
        BlockPos roof = feet.up(2);
        if (tryTarget(roof, held, SUPPORT_ORDER) || pathTo(roof, feet, 8, -2, 6, held)) return true;
        for (EnumFacing facing : facings) {
            BlockPos below = feet.down().offset(facing);
            BlockPos side = feet.offset(facing);
            BlockPos head = side.up();
            if (tryTarget(below, held, SUPPORT_ORDER_UP)
                    || pathTo(below, feet, 4, -6, 0, held)
                    || tryTarget(side, held, SUPPORT_ORDER_UP)
                    || pathTo(side, feet, 4, -3, 1, held)
                    || tryTarget(head, held, SUPPORT_ORDER)
                    || pathTo(head, feet, 4, -2, 2, held)) return true;
        }
        return false;
    }

    private boolean tryTarget(BlockPos destination, ItemStack held, EnumFacing[] order) {
        if (!canTarget(destination) || failed.contains(destination) || retrySet.contains(destination)) return false;
        BlockPos feet = playerPos();
        for (EnumFacing face : order) {
            BlockPos support = destination.offset(face.getOpposite());
            if ((support.equals(feet) && face.getAxis() == EnumFacing.Axis.Y) || !isSupport(support)) continue;
            AimResult aim = aimAtFace(destination, support, face, held);
            if (aim == null) continue;
            target = aim;
            fromYaw = RotationUtils.serverRotations[0];
            fromPitch = RotationUtils.serverRotations[1];
            double ticks = delay.getInput();
            steps = (int) ticks;
            if (ticks - steps >= 0.5) {
                if (roundUp) steps++;
                roundUp = !roundUp;
            }
            step = 0;
            return true;
        }
        return false;
    }

    private boolean followPath(ItemStack held) {
        if (path == null) return false;
        while (!path.isEmpty() && !isReplaceable(path.get(0))) path.remove(0);
        if (!path.isEmpty()) {
            BlockPos next = path.get(0);
            if (tryTarget(next, held, SUPPORT_ORDER)) return true;
            if (canTarget(next)) addFailed(next);
        }
        path = null;
        return false;
    }

    private boolean pathTo(BlockPos destination, BlockPos feet, int radius, int minY, int maxY, ItemStack held) {
        if (!canTarget(destination) || failed.contains(destination) || retrySet.contains(destination)) return false;
        for (EnumFacing face : SUPPORT_ORDER) {
            if (isSupport(destination.offset(face.getOpposite()))) return false;
        }
        Set<BlockPos> open = new HashSet<>();
        List<BlockPos> starts = new ArrayList<>();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double reachSquared = reach() * reach();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos position = feet.add(x, y, z);
                    if (!canTarget(position) || retrySet.contains(position) || failed.contains(position)) continue;
                    open.add(position);
                    if (Math.max(Math.abs(x), Math.abs(z)) > 3) continue;
                    for (EnumFacing face : SUPPORT_ORDER) {
                        BlockPos support = position.offset(face.getOpposite());
                        if (isSupport(support) && BlockUtils.dist2PointAABB(eyes, support) <= reachSquared) {
                            starts.add(position);
                            break;
                        }
                    }
                }
            }
        }
        path = BlockInPathfinder.findPath(destination, starts, open);
        return followPath(held);
    }

    private boolean retryTarget(ItemStack held) {
        int count = retry.size();
        while (count-- > 0) {
            BlockPos position = retry.removeFirst();
            retrySet.remove(position);
            if (!canTarget(position)) continue;
            if (!failed.contains(position) && tryTarget(position, held, SUPPORT_ORDER)) return true;
            retry.addLast(position);
            retrySet.add(position);
        }
        return false;
    }

    private void pruneRetries() {
        Iterator<BlockPos> iterator = retry.iterator();
        while (iterator.hasNext()) {
            BlockPos position = iterator.next();
            if (!canTarget(position)) {
                iterator.remove();
                retrySet.remove(position);
            }
        }
    }

    private void markFailed() {
        if (target == null) return;
        addFailed(target.destination);
        target = null;
        placeQueued = false;
    }

    private void addFailed(BlockPos position) {
        failed.add(position);
        if (retrySet.add(position)) {
            if (retry.size() >= MAX_RETRIES) retrySet.remove(retry.removeFirst());
            retry.addLast(position);
        }
        if (path != null && !path.isEmpty() && path.get(0).equals(position)) path = null;
    }

    private AimResult aimAtFace(BlockPos destination, BlockPos support, EnumFacing face, ItemStack held) {
        if (!isSupport(support) || !canPlace(held, support, face)) return null;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        for (int index = -1; index < 25; index++) {
            double u = index < 0 ? 0.5 : 0.01 + (index % 5) * 0.245;
            double v = index < 0 ? 0.5 : 0.01 + (index / 5) * 0.245;
            double x = support.getX() + (face.getAxis() == EnumFacing.Axis.X
                    ? face == EnumFacing.EAST ? 0.999 : 0.001 : u);
            double y = support.getY() + (face.getAxis() == EnumFacing.Axis.Y
                    ? face == EnumFacing.UP ? 0.999 : 0.001 : v);
            double z = support.getZ() + (face.getAxis() == EnumFacing.Axis.Z
                    ? face == EnumFacing.SOUTH ? 0.999 : 0.001
                    : face.getAxis() == EnumFacing.Axis.X ? u : v);
            float[] rotation = RotationUtils.getRotationsFromEye(eyes, x, y, z);
            rotation = RotationUtils.fixRotation(rotation[0], rotation[1],
                    RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
            MovingObjectPosition hit = rayCast(rotation[0], rotation[1]);
            if (hit != null && support.equals(hit.getBlockPos()) && face == hit.sideHit) {
                return new AimResult(destination, support, face, rotation[0], rotation[1]);
            }
        }
        return null;
    }

    private boolean matchesTarget(MovingObjectPosition hit) {
        return target != null && hit != null && hit.hitVec != null
                && target.support.equals(hit.getBlockPos()) && target.face == hit.sideHit
                && target.destination.equals(hit.getBlockPos().offset(hit.sideHit));
    }

    private boolean canTarget(BlockPos position) {
        BlockPos feet = playerPos();
        if (position.getY() < 0 || position.getY() >= 256
                || position.equals(feet) || position.equals(feet.up()) || isProtectedOpening(position)
                || !isReplaceable(position)) return false;
        if (mc.thePlayer.getEntityBoundingBox().intersectsWith(
                new AxisAlignedBB(position, position.add(1, 1, 1)))) return false;
        if (skipNearBed.isToggled()) {
            if (blockAt(position.down()) == Blocks.bed) return false;
            for (EnumFacing facing : HORIZONTALS) {
                if (blockAt(position.offset(facing)) == Blocks.bed) return false;
            }
        }
        return true;
    }

    private boolean isSupport(BlockPos position) {
        Block block = blockAt(position);
        return block != Blocks.air && !BlockUtils.isFluid(block) && !block.isReplaceable(mc.theWorld, position)
                && !BlockUtils.isInteractable(block) && !(block instanceof BlockLever)
                && !(block instanceof BlockButton);
    }

    private Block blockAt(BlockPos position) {
        return mc.theWorld.getBlockState(position).getBlock();
    }

    private boolean isReplaceable(BlockPos position) {
        return blockAt(position).isReplaceable(mc.theWorld, position);
    }

    private boolean canPlace(ItemStack stack, BlockPos support, EnumFacing face) {
        return stack != null && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(
                mc.theWorld, support, face, mc.thePlayer, stack);
    }

    private MovingObjectPosition rayCast(float yaw, float pitch) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = Utils.getLookVec(yaw, pitch);
        double reach = reach();
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes,
                eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach),
                false, false, false);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? hit : null;
    }

    private double reach() {
        return mc.playerController.getBlockReachDistance();
    }

    private BlockPos playerPos() {
        return new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
    }

    private void updatePercentage() {
        int enclosed = 0;
        int total = 0;
        for (BlockPos position : enclosurePositions(playerPos())) {
            if (isProtectedOpening(position)) continue;
            total++;
            if (isSupport(position)) enclosed++;
        }
        int percent = total == 0 ? 100 : Math.round(enclosed * 100.0F / total);
        percentText = percent + "%";
        percentColor = percent == 100 ? 0x00FF00 : 0xFFFFFF;
        if (percent == 100 && disableWhenComplete.isToggled()) {
            stopPlacing();
            waitRelease = true;
        }
    }

    private void refreshProtectedOpening(boolean manualBreaking) {
        if (manualBreaking) {
            Vec3 aim = getManualBreakAim();
            if (aim != null) {
                manualBreakAim = aim;
                manualOpeningUntilTick = mc.thePlayer.ticksExisted + MANUAL_OPENING_GRACE_TICKS;
            }
        }
        Vec3 aim = ModuleManager.bedAura != null ? ModuleManager.bedAura.getProtectedBreakAim() : null;
        if (aim == null && mc.thePlayer.ticksExisted <= manualOpeningUntilTick) aim = manualBreakAim;
        protectedOpening = selectProtectedOpening(playerPos(), mc.thePlayer.getPositionEyes(1.0F), aim);
    }

    private Vec3 getManualBreakAim() {
        MovingObjectPosition hit = rayCast(
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return hit.hitVec != null ? hit.hitVec : center(hit.getBlockPos());
        }
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            IAccessorPlayerControllerMP controller = (IAccessorPlayerControllerMP) mc.playerController;
            if (controller.isHittingBlock() && controller.getCurrentBlock() != null) {
                return center(controller.getCurrentBlock());
            }
        }
        return null;
    }

    private boolean isProtectedOpening(BlockPos position) {
        return position != null && position.equals(protectedOpening);
    }

    private static Vec3 center(BlockPos position) {
        return new Vec3(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }

    private static List<BlockPos> enclosurePositions(BlockPos feet) {
        List<BlockPos> positions = new ArrayList<>(9);
        positions.add(feet.up(2));
        for (EnumFacing facing : HORIZONTALS) {
            BlockPos side = feet.offset(facing);
            positions.add(side);
            positions.add(side.up());
        }
        return positions;
    }

    static BlockPos selectProtectedOpening(BlockPos feet, Vec3 eyes, Vec3 aim) {
        if (feet == null || eyes == null || aim == null) return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos opening : enclosurePositions(feet)) {
            MovingObjectPosition intercept = new AxisAlignedBB(opening, opening.add(1, 1, 1))
                    .calculateIntercept(eyes, aim);
            if (intercept == null || intercept.hitVec == null) continue;
            double distance = eyes.squareDistanceTo(intercept.hitVec);
            if (distance < bestDistance) {
                best = opening;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static final class AimResult {
        private final BlockPos destination;
        private final BlockPos support;
        private final EnumFacing face;
        private final float yaw;
        private final float pitch;

        private AimResult(BlockPos destination, BlockPos support, EnumFacing face, float yaw, float pitch) {
            this.destination = destination;
            this.support = support;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
