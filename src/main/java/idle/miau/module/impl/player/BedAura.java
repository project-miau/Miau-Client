package idle.miau.module.impl.player;

import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.module.impl.render.HudStyle;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.GameTickEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.PreSlotScrollEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.RightClickMouseEvent;
import idle.miau.event.SlotUpdateEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.module.impl.render.BedESP;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.PlayerStateTracker;
import idle.miau.utility.PlayerUtils;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public class BedAura extends Module {
    private static final long WHITELIST_SCAN_DELAY_MS = 1000L;
    private static final float BREAK_THRESHOLD = 1.0F;
    private static final double OWN_BED_PROTECTION_DISTANCE_SQ = 1225.0D;
    private static final double HOME_TELEPORT_DISTANCE_SQ = 1600.0D;
    private static final double REACH_EPSILON = 1.0E-4D;
    private static final double LEGIT_PATH_RECHECK_DISTANCE_SQ = 0.25D;
    private static final double LEGIT_PATH_SWITCH_RATIO = 0.9D;
    private static final int LEGIT_PATH_RECHECK_TICKS = 5;
    private static final int BED_COMPLETION_GRACE_TICKS = 10;
    private static final float LEGIT_ROTATION_STEP = 45.0F;
    private static final float LEGIT_ROTATION_SMOOTHING = 0.15F;
    private static final int LEGIT_MODE = 0;
    private static final int HYPIXEL_MODE = 1;
    private static final String[] VISUAL_MODES = {"Text", "Bar"};
    private static final int BAR_VISUAL_MODE = 1;

    private final BedTimer timer = new BedTimer();
    private final ArrayList<BlockPos> bedWhitelist = new ArrayList<>();
    private final RenderUtils.ProgressBar progressBar = new RenderUtils.ProgressBar();
    private BlockPos targetBed;
    private Vec3 targetAim;
    private Block targetBlockType;
    private BlockPos pathBed;
    private Vec3 pathAim;
    private BlockPos trackedBed;
    private BlockPos trackedBedMate;
    private BlockPos recentlyBrokenBed;
    private BlockPos recentlyBrokenBedMate;
    private Vec3 completedBreakAim;
    private Vec3 lastPathCheckPosition;
    private int lastPathCheckTick;
    private int bedCompletionCooldownUntilTick;
    private Vec3 home;
    private int breakStage;
    private int tickCounter;
    private float breakProgress;
    private boolean isBed;
    private int savedSlot = -1;
    private boolean readyToBreak;
    private boolean breaking;
    private boolean waitingForStart;
    private boolean holdingAttackKey;
    private boolean attackSequenceStarted;
    private boolean legitSession;
    private long whitelistScanAt = -1L;
    private int aimLossTicks;
    private int attackLossTicks;
    private final ButtonSetting prioritizeKillAura = new ButtonSetting("Prioritize KillAura", false);
    private final SliderSetting deferEnemyRange = new SliderSetting(
            "Defer enemy range", "m", 4.5, 1.0, 8.0, 0.1);

    public final SliderSetting mode = new SliderSetting("Mode", 0, new String[]{"LEGIT", "HYPIXEL"});
    public final SliderSetting range = new SliderSetting("Range", 4.5, 3.0, 6.0, 0.1);
    public final ButtonSetting whiteListOwnBed = new ButtonSetting(
            "Whitelist Own Bed", true, "Whitelist", "Ignore own bed");
    public final ButtonSetting surroundings = new ButtonSetting("Surroundings", true);
    public final SliderSetting visualMode = new SliderSetting(
            "Visual Mode", 0, VISUAL_MODES);
    public final ButtonSetting swing = new ButtonSetting("Swing", true);

    public BedAura() {
        super("BedAura", category.player);
        setDescription("Fucker.");
        registerSetting(mode);
        registerSetting(range);
        registerSetting(whiteListOwnBed);
        registerSetting(surroundings);
        registerSetting(visualMode);
        registerSetting(prioritizeKillAura);
        registerSetting(deferEnemyRange);
        registerSetting(swing);
    }

    private void resetBreaking() {
        releaseAttackKey();
        clearBreakingState();
        clearLegitPath();
        clearTrackedBed();
    }

    private void clearLegitPath() {
        pathBed = null;
        pathAim = null;
        lastPathCheckPosition = null;
        lastPathCheckTick = 0;
    }

    private void clearTrackedBed() {
        trackedBed = null;
        trackedBedMate = null;
    }

    private void resetBedCompletionState() {
        recentlyBrokenBed = null;
        recentlyBrokenBedMate = null;
        completedBreakAim = null;
        bedCompletionCooldownUntilTick = 0;
    }

    private void trackBed(BlockPos bed) {
        trackedBed = bed;
        trackedBedMate = getOtherBedHalf(bed);
    }

    private BlockPos getOtherBedHalf(BlockPos bed) {
        if (bed == null || mc.theWorld == null) return null;
        IBlockState state = mc.theWorld.getBlockState(bed);
        if (!(state.getBlock() instanceof BlockBed)) return null;
        EnumPartType part = state.getValue(BlockBed.PART);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        return bed.offset(part == EnumPartType.HEAD ? facing.getOpposite() : facing);
    }

    private BlockPos findSourceBed(BlockPos target) {
        if (target == null || mc.theWorld == null) return null;
        if (mc.theWorld.getBlockState(target).getBlock() instanceof BlockBed) {
            return target;
        }
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos adjacent = target.offset(facing);
            if (mc.theWorld.getBlockState(adjacent).getBlock() instanceof BlockBed) {
                return adjacent;
            }
        }
        return null;
    }

    private boolean isBedCompletionCoolingDown() {
        return mc.thePlayer != null
                && mc.thePlayer.ticksExisted < bedCompletionCooldownUntilTick;
    }

    private boolean isRecentlyBrokenBed(BlockPos bed) {
        return bed.equals(recentlyBrokenBed) || bed.equals(recentlyBrokenBedMate);
    }

    private void refreshBedCompletionState() {
        if (isBedCompletionCoolingDown() || recentlyBrokenBed == null
                || mc.theWorld == null) {
            return;
        }
        boolean firstStillPresent = mc.theWorld.getBlockState(recentlyBrokenBed)
                .getBlock() instanceof BlockBed;
        boolean mateStillPresent = recentlyBrokenBedMate != null
                && mc.theWorld.getBlockState(recentlyBrokenBedMate)
                .getBlock() instanceof BlockBed;
        if (!firstStillPresent && !mateStillPresent) {
            resetBedCompletionState();
        }
    }

    private boolean trackedBedDisappeared() {
        return trackedBed != null
                && !(mc.theWorld.getBlockState(trackedBed).getBlock() instanceof BlockBed);
    }

    private void completeTrackedBed() {
        if (trackedBed == null || mc.thePlayer == null) return;
        recentlyBrokenBed = trackedBed;
        recentlyBrokenBedMate = trackedBedMate;
        completedBreakAim = pathAim != null
                ? pathAim
                : new Vec3(trackedBed.getX() + 0.5D,
                trackedBed.getY() + 0.5D, trackedBed.getZ() + 0.5D);
        bedCompletionCooldownUntilTick = mc.thePlayer.ticksExisted
                + BED_COMPLETION_GRACE_TICKS;
        resetBreaking();
        restoreSlot();
    }

    private void handleTrackedBedUpdate(BlockPos position, Block block) {
        if (isTrackedBedRemoval(
                position, block instanceof BlockBed, trackedBed, trackedBedMate)) {
            completeTrackedBed();
        }
    }

    static boolean isTrackedBedRemoval(
            BlockPos position,
            boolean updatedBlockIsBed,
            BlockPos trackedBed,
            BlockPos trackedBedMate) {
        return !updatedBlockIsBed && position != null
                && (position.equals(trackedBed) || position.equals(trackedBedMate));
    }

    private void clearBreakingState() {
        if (targetBed != null && mc.theWorld != null && mc.thePlayer != null) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), targetBed, -1);
        }
        targetBed = null;
        targetAim = null;
        targetBlockType = null;
        breakStage = 0;
        tickCounter = 0;
        breakProgress = 0.0F;
        isBed = false;
        readyToBreak = false;
        breaking = false;
        aimLossTicks = 0;
        attackLossTicks = 0;
    }

    private void scheduleWhitelistScan() {
        whitelistScanAt = System.currentTimeMillis() + WHITELIST_SCAN_DELAY_MS;
    }

    private void runPendingWhitelistScan() {
        if (whitelistScanAt < 0L || System.currentTimeMillis() < whitelistScanAt) return;
        whitelistScanAt = -1L;
        bedWhitelist.clear();
        if (!Utils.nullCheck()) return;

        int startX = MathHelper.floor_double(mc.thePlayer.posX);
        int startY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        int startZ = MathHelper.floor_double(mc.thePlayer.posZ);
        for (int x = startX - 25; x <= startX + 25; x++) {
            for (int y = startY - 25; y <= startY + 25; y++) {
                for (int z = startZ - 25; z <= startZ + 25; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed) {
                        bedWhitelist.add(pos);
                    }
                }
            }
        }
    }

    private float calcProgress() {
        if (targetBed == null) return 0.0F;
        if (isLegitMode()) {
            if (mc.playerController == null) return 0.0F;
            IAccessorPlayerControllerMP controller = (IAccessorPlayerControllerMP) mc.playerController;
            if (!targetBed.equals(controller.getCurrentBlock())) return 0.0F;
            return MathHelper.clamp_float(controller.getCurBlockDamageMP(), 0.0F, 1.0F);
        }
        IBlockState state = mc.theWorld.getBlockState(targetBed);
        int slot = AutoTool.findBestToolSlot(
                state.getBlock(), mc.thePlayer.inventory.currentItem);
        float progress = tickCounter * getBreakDelta(state, targetBed, slot, true);
        return Math.min(1.0F, progress / BREAK_THRESHOLD);
    }

    private void restoreSlot() {
        if (savedSlot == -1 || mc.thePlayer == null) return;
        mc.thePlayer.inventory.currentItem = savedSlot;
        syncHeldItem();
        savedSlot = -1;
    }

    private void syncHeldItem() {
        int serverSlot = ((IAccessorPlayerControllerMP) mc.playerController).getCurrentPlayerItem();
        if (mc.thePlayer.inventory.currentItem != serverSlot) {
            mc.thePlayer.stopUsingItem();
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }

    private void pressAttackKey() {
        if (mc.gameSettings == null) return;
        int keyCode = mc.gameSettings.keyBindAttack.getKeyCode();
        if (keyCode == 0) return;
        boolean startPress = !attackSequenceStarted
                && !Utils.isBindDown(mc.gameSettings.keyBindAttack);
        KeyBinding.setKeyBindState(keyCode, true);
        if (startPress) {
            KeyBinding.onTick(keyCode);
        }
        holdingAttackKey = true;
        attackSequenceStarted = true;
        readyToBreak = true;
    }

    private void releaseAttackKey() {
        attackSequenceStarted = false;
        if (!holdingAttackKey) return;
        holdingAttackKey = false;
        if (mc.gameSettings == null) return;
        boolean physicallyPressed = Utils.isBindDown(mc.gameSettings.keyBindAttack);
        KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindAttack.getKeyCode(), physicallyPressed);
        if (!physicallyPressed && mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock) {
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemPickaxe) return true;
        }
        return false;
    }

    private EnumFacing getHitFacing(BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.25D;
        double z = pos.getZ() + 0.5D;
        float[] rotations = getRotationsTo(x, y, z);
        net.minecraft.util.MovingObjectPosition hit =
                RotationUtils.rayCast(8.0D, rotations[0], rotations[1], false);
        return hit == null ? EnumFacing.UP : hit.sideHit;
    }

    private float getDigSpeed(IBlockState state, int slot, boolean onGround) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        float digSpeed = stack == null ? 1.0F : stack.getItem().getStrVsBlock(stack, state.getBlock());
        if (digSpeed > 1.0F) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            if (efficiency > 0) digSpeed += efficiency * efficiency + 1.0F;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            digSpeed *= 1.0F
                    + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                case 0:
                    digSpeed *= 0.3F;
                    break;
                case 1:
                    digSpeed *= 0.09F;
                    break;
                case 2:
                    digSpeed *= 0.0027F;
                    break;
                default:
                    digSpeed *= 8.1E-4F;
                    break;
            }
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water)
                && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
            digSpeed /= 5.0F;
        }
        if (!onGround) digSpeed /= 5.0F;
        return digSpeed;
    }

    private boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired()) return true;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.canHarvestBlock(block);
    }

    private float getBreakDelta(IBlockState state, BlockPos pos, int slot, boolean onGround) {
        Block block = state.getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, pos);
        float divisor = canHarvest(block, slot) ? 30.0F : 100.0F;
        return hardness < 0.0F ? 0.0F : getDigSpeed(state, slot, onGround) / hardness / divisor;
    }

    private float calcBlockStrength(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        int slot = AutoTool.findBestToolSlot(
                state.getBlock(), mc.thePlayer.inventory.currentItem);
        return getBreakDelta(state, pos, slot, mc.thePlayer.onGround);
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        IBlockState state = mc.theWorld.getBlockState(bedPosition);
        if (!(state.getBlock() instanceof BlockBed)) return null;

        ArrayList<BlockPos> candidates = new ArrayList<>();
        EnumPartType part = state.getValue(BlockBed.PART);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        BlockPos otherHalf = bedPosition.offset(part == EnumPartType.HEAD ? facing.getOpposite() : facing);
        for (BlockPos bedPos : Arrays.asList(bedPosition, otherHalf)) {
            for (EnumFacing side : Arrays.asList(
                    EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST,
                    EnumFacing.SOUTH, EnumFacing.WEST)) {
                BlockPos adjacent = bedPos.offset(side);
                Block block = mc.theWorld.getBlockState(adjacent).getBlock();
                if (BlockUtils.replaceable(adjacent)) return null;
                if (!(block instanceof BlockBed)) candidates.add(adjacent);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((first, second) -> {
            int strength = Float.compare(calcBlockStrength(second), calcBlockStrength(first));
            return strength != 0 ? strength : Double.compare(
                    first.distanceSqToCenter(mc.thePlayer.posX,
                            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                    second.distanceSqToCenter(mc.thePlayer.posX,
                            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ));
        });
        return candidates.get(0);
    }

    private BlockPos findNearestBed() {
        return findTargetBed(mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    private BlockPos findTargetBed(double x, double y, double z) {
        if (isOwnBedProtected()) return null;
        ArrayList<BlockPos> targets = new ArrayList<>();
        int startX = MathHelper.floor_double(x);
        int startY = MathHelper.floor_double(y);
        int startZ = MathHelper.floor_double(z);
        for (int blockX = startX - 6; blockX <= startX + 6; blockX++) {
            for (int blockY = startY - 6; blockY <= startY + 6; blockY++) {
                for (int blockZ = startZ - 6; blockZ <= startZ + 6; blockZ++) {
                    BlockPos pos = new BlockPos(blockX, blockY, blockZ);
                    if (whiteListOwnBed.isToggled() && bedWhitelist.contains(pos)) continue;
                    if (isRecentlyBrokenBed(pos)) continue;
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed
                            && PlayerUtils.isBlockWithinReach(pos, x, y, z, range.getInput())) {
                        targets.add(pos);
                    }
                }
            }
        }
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(pos -> pos.distanceSqToCenter(
                mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ)));
        for (BlockPos bedPos : targets) {
            if (surroundings.isToggled()) {
                BlockPos defense = validateBedPlacement(bedPos);
                if (defense != null) {
                    if (!hasProperTool(mc.theWorld.getBlockState(defense).getBlock())) {
                        continue;
                    }
                    return defense;
                }
            }
            return bedPos;
        }
        return null;
    }

    private void tickLegit() {
        if (isOwnBedProtected() || !canBreakBlocks()) {
            resetBreaking();
            restoreSlot();
            return;
        }

        if (trackedBedDisappeared()) {
            completeTrackedBed();
            return;
        }

        if (isBedCompletionCoolingDown()) {
            releaseAttackKey();
            restoreSlot();
            return;
        }

        if (shouldDeferToKillAura() || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.thePlayer.isUsingItem()) {
            releaseAttackKey();
            restoreSlot();
            readyToBreak = false;
            breaking = false;
            return;
        }

        double reach = getLegitReach();
        if (targetBed != null) {
            IBlockState state = mc.theWorld.getBlockState(targetBed);
            Block block = state.getBlock();
            if (block.getMaterial() == Material.air) {
                aimLossTicks = 0;
                clearBreakingState();
            } else {
                Vec3 aim = (targetBlockType != null && block != targetBlockType)
                        || block.getBlockHardness(mc.theWorld, targetBed) < 0.0F
                        ? null
                        : findVisibleAim(targetBed, reach);
                if (aim == null) {
                    Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
                    if (distanceToBlock(eyes, targetBed) <= reach + REACH_EPSILON
                            && ++aimLossTicks <= 4) {
                    } else {
                        aimLossTicks = 0;
                        resetBreaking();
                    }
                } else {
                    aimLossTicks = 0;
                    targetAim = aim;
                    isBed = block instanceof BlockBed;
                }
            }
        }

        if (targetBed != null) {
            revalidateLegitPath(reach);
        }

        if (targetBed == null) {
            LegitTarget target = findLockedLegitTarget(reach);
            if (target == null) {
                clearLegitPath();
                target = findLegitTarget(reach);
            }
            if (target == null) {
                releaseAttackKey();
                restoreSlot();
                return;
            }
            selectLegitTarget(target);
        }

        if (ModuleManager.blockIn != null && ModuleManager.blockIn.isPlacing()) {
            releaseAttackKey();
            readyToBreak = false;
            breaking = false;
            return;
        }

        IBlockState state = mc.theWorld.getBlockState(targetBed);
        int slot = AutoTool.findBestToolSlot(
                state.getBlock(), mc.thePlayer.inventory.currentItem);
        if (savedSlot == -1) {
            savedSlot = mc.thePlayer.inventory.currentItem;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            syncHeldItem();
        }
        readyToBreak = true;
        IAccessorPlayerControllerMP controller = (IAccessorPlayerControllerMP) mc.playerController;
        breaking = holdingAttackKey && controller.isHittingBlock()
                && targetBed.equals(controller.getCurrentBlock());
    }

    private void selectLegitTarget(LegitTarget target) {
        targetBed = target.position;
        targetAim = target.aim;
        targetBlockType = mc.theWorld.getBlockState(targetBed).getBlock();
        isBed = target.bed;
        pathBed = target.bedPosition;
        pathAim = target.pathAim;
        trackBed(target.bedPosition);
        breakStage = 0;
        tickCounter = 0;
        breakProgress = 0.0F;
        lastPathCheckPosition = mc.thePlayer.getPositionEyes(1.0F);
        lastPathCheckTick = mc.thePlayer.ticksExisted;
    }

    private void revalidateLegitPath(double reach) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        int tick = mc.thePlayer.ticksExisted;
        if (lastPathCheckPosition == null || tick < lastPathCheckTick) {
            lastPathCheckPosition = eyes;
            lastPathCheckTick = tick;
            return;
        }
        if (targetBed != null) {
            IAccessorPlayerControllerMP controller =
                    (IAccessorPlayerControllerMP) mc.playerController;
            if (controller != null && targetBed.equals(controller.getCurrentBlock())
                    && controller.getCurBlockDamageMP() > 0.05F
                    && !mc.theWorld.isAirBlock(targetBed)
                    && distanceToBlock(eyes, targetBed) <= reach + REACH_EPSILON) {
                return;
            }
        }
        if (tick - lastPathCheckTick < LEGIT_PATH_RECHECK_TICKS
                || lastPathCheckPosition.squareDistanceTo(eyes)
                < LEGIT_PATH_RECHECK_DISTANCE_SQ) {
            return;
        }
        lastPathCheckPosition = eyes;
        lastPathCheckTick = tick;
        LegitTarget current = findLockedLegitTarget(reach);
        if (current != null && !current.position.equals(targetBed)) {
            current = null;
        }
        LegitTarget best = findLegitTarget(reach);
        if (best == null) {
            if (current == null) resetBreaking();
            return;
        }
        if (best.position.equals(targetBed)) {
            pathBed = best.bedPosition;
            pathAim = best.pathAim;
            trackBed(best.bedPosition);
            return;
        }
        if (current != null) {
            float progress = 0.0F;
            IAccessorPlayerControllerMP controller =
                    (IAccessorPlayerControllerMP) mc.playerController;
            if (targetBed.equals(controller.getCurrentBlock())) {
                progress = MathHelper.clamp_float(
                        controller.getCurBlockDamageMP(), 0.0F, 1.0F);
            }
            double remainingTime = remainingPathTime(
                    current.breakTime, progress, calcBlockStrength(targetBed));
            if (!shouldSwitchPath(best.breakTime, remainingTime)) return;
        }
        releaseAttackKey();
        clearBreakingState();
        selectLegitTarget(best);
    }

    private LegitTarget findLegitTarget(double reach) {
        if (isOwnBedProtected()) return null;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        int radius = (int) Math.ceil(reach) + 1;
        int startX = MathHelper.floor_double(eyes.xCoord);
        int startY = MathHelper.floor_double(eyes.yCoord);
        int startZ = MathHelper.floor_double(eyes.zCoord);
        ArrayList<BlockPos> beds = new ArrayList<>();
        for (int x = startX - radius; x <= startX + radius; x++) {
            for (int y = startY - radius; y <= startY + radius; y++) {
                for (int z = startZ - radius; z <= startZ + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (whiteListOwnBed.isToggled() && bedWhitelist.contains(pos)) continue;
                    if (isRecentlyBrokenBed(pos)) continue;
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed
                            && distanceToBlock(eyes, pos) <= reach + REACH_EPSILON) {
                        beds.add(pos);
                    }
                }
            }
        }
        LegitTarget best = null;
        for (BlockPos bed : beds) {
            for (Vec3 aim : getBlockAimPoints(bed)) {
                LegitTarget candidate = buildLegitCandidate(bed, aim, eyes, reach);
                if (candidate != null && (best == null || candidate.isBetterThan(best))) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private LegitTarget findLockedLegitTarget(double reach) {
        if (pathBed == null || pathAim == null) return null;
        if (!(mc.theWorld.getBlockState(pathBed).getBlock() instanceof BlockBed)) return null;
        if (whiteListOwnBed.isToggled() && bedWhitelist.contains(pathBed)) return null;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        if (distanceToBlock(eyes, pathBed) > reach + REACH_EPSILON) return null;
        return buildLegitCandidate(pathBed, pathAim, eyes, reach);
    }

    private LegitTarget buildLegitCandidate(
            BlockPos bed, Vec3 aim, Vec3 eyes, double reach) {
        BlockPos firstBlock = null;
        boolean firstIsBed = false;
        double firstDistance = Double.MAX_VALUE;
        double breakTime = 0.0D;
        int blockCount = 0;
        for (BlockPos position : traceBlocks(eyes, aim)) {
            IBlockState state = mc.theWorld.getBlockState(position);
            Block block = state.getBlock();
            if (!block.canCollideCheck(state, false)) continue;
            MovingObjectPosition hit = block.collisionRayTrace(
                    mc.theWorld, position, eyes, aim);
            if (hit == null || hit.hitVec == null) continue;
            double distance = eyes.distanceTo(hit.hitVec);
            if (distance > reach + REACH_EPSILON) return null;
            boolean bedBlock = block instanceof BlockBed;
            if (bedBlock && whiteListOwnBed.isToggled() && bedWhitelist.contains(position)) {
                return null;
            }
            if (bedBlock && !position.equals(bed)) return null;
            if (!bedBlock && (!surroundings.isToggled()
                    || block.getBlockHardness(mc.theWorld, position) < 0.0F)) {
                return null;
            }
            if (firstBlock == null) {
                firstBlock = position;
                firstIsBed = bedBlock;
                firstDistance = distance;
            }
            float strength = calcBlockStrength(position);
            if (strength <= 0.0F) return null;
            breakTime += 1.0D / strength;
            blockCount++;
            if (bedBlock) {
                return new LegitTarget(
                        firstBlock, aim, firstIsBed, bed, aim,
                        breakTime, blockCount, firstDistance);
            }
            if (blockCount >= 64) return null;
        }
        return null;
    }

    private ArrayList<BlockPos> traceBlocks(Vec3 start, Vec3 end) {
        ArrayList<BlockPos> blocks = new ArrayList<>();
        int x = MathHelper.floor_double(start.xCoord);
        int y = MathHelper.floor_double(start.yCoord);
        int z = MathHelper.floor_double(start.zCoord);
        int endX = MathHelper.floor_double(end.xCoord);
        int endY = MathHelper.floor_double(end.yCoord);
        int endZ = MathHelper.floor_double(end.zCoord);
        double deltaX = end.xCoord - start.xCoord;
        double deltaY = end.yCoord - start.yCoord;
        double deltaZ = end.zCoord - start.zCoord;
        int stepX = Integer.compare(endX, x);
        int stepY = Integer.compare(endY, y);
        int stepZ = Integer.compare(endZ, z);
        double stepSizeX = stepX == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaX);
        double stepSizeY = stepY == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaY);
        double stepSizeZ = stepZ == 0 ? Double.MAX_VALUE : 1.0D / Math.abs(deltaZ);
        double nextX = stepX == 0 ? Double.MAX_VALUE
                : (stepX > 0 ? x + 1.0D - start.xCoord : start.xCoord - x)
                / Math.abs(deltaX);
        double nextY = stepY == 0 ? Double.MAX_VALUE
                : (stepY > 0 ? y + 1.0D - start.yCoord : start.yCoord - y)
                / Math.abs(deltaY);
        double nextZ = stepZ == 0 ? Double.MAX_VALUE
                : (stepZ > 0 ? z + 1.0D - start.zCoord : start.zCoord - z)
                / Math.abs(deltaZ);
        blocks.add(new BlockPos(x, y, z));
        while ((x != endX || y != endY || z != endZ) && blocks.size() < 64) {
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX;
                nextX += stepSizeX;
            } else if (nextY <= nextZ) {
                y += stepY;
                nextY += stepSizeY;
            } else {
                z += stepZ;
                nextZ += stepSizeZ;
            }
            blocks.add(new BlockPos(x, y, z));
        }
        return blocks;
    }

    private Vec3 findVisibleAim(BlockPos position, double reach) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        for (Vec3 aim : getBlockAimPoints(position)) {
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(
                    eyes, aim, false, false, true);
            if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK
                    || !position.equals(hit.getBlockPos()) || hit.hitVec == null) {
                continue;
            }
            double distance = eyes.distanceTo(hit.hitVec);
            if (distance <= reach + REACH_EPSILON) return aim;
        }
        return null;
    }

    private Vec3[] getBlockAimPoints(BlockPos position) {
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(position);
        if (box == null) {
            box = new AxisAlignedBB(position, position.add(1, 1, 1));
        }
        double insetX = Math.min(0.1D, (box.maxX - box.minX) * 0.2D);
        double insetY = Math.min(0.1D, (box.maxY - box.minY) * 0.2D);
        double insetZ = Math.min(0.1D, (box.maxZ - box.minZ) * 0.2D);
        double minX = box.minX + insetX;
        double minY = box.minY + insetY;
        double minZ = box.minZ + insetZ;
        double maxX = box.maxX - insetX;
        double maxY = box.maxY - insetY;
        double maxZ = box.maxZ - insetZ;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return new Vec3[]{
                new Vec3(centerX, centerY, centerZ),
                new Vec3(minX, minY, minZ),
                new Vec3(minX, minY, maxZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ)
        };
    }

    private double distanceToBlock(Vec3 point, BlockPos position) {
        AxisAlignedBB box = BlockUtils.getBlockSelectionBox(position);
        if (box == null) {
            box = new AxisAlignedBB(position, position.add(1, 1, 1));
        }
        double x = Math.max(
                box.minX - point.xCoord, Math.max(0.0D, point.xCoord - box.maxX));
        double y = Math.max(
                box.minY - point.yCoord, Math.max(0.0D, point.yCoord - box.maxY));
        double z = Math.max(
                box.minZ - point.zCoord, Math.max(0.0D, point.zCoord - box.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }

    private double getLegitReach() {
        double vanillaReach = mc.playerController.extendedReach()
                ? 6.0D : mc.playerController.getBlockReachDistance();
        return limitLegitReach(range.getInput(), vanillaReach);
    }

    static double limitLegitReach(double configuredReach, double vanillaReach) {
        return Math.max(0.0D, Math.min(configuredReach, vanillaReach));
    }

    static boolean isBetterPath(
            double breakTime,
            int blockCount,
            double distance,
            double otherBreakTime,
            int otherBlockCount,
            double otherDistance) {
        int timeComparison = Double.compare(breakTime, otherBreakTime);
        if (timeComparison != 0) return timeComparison < 0;
        int countComparison = Integer.compare(blockCount, otherBlockCount);
        return countComparison < 0
                || countComparison == 0 && distance < otherDistance;
    }

    static double remainingPathTime(
            double totalTime, double progress, double strength) {
        if (strength <= 0.0D) return totalTime;
        double clampedProgress = Math.max(0.0D, Math.min(1.0D, progress));
        return Math.max(0.0D, totalTime - clampedProgress / strength);
    }

    static boolean shouldSwitchPath(double newTime, double currentRemainingTime) {
        return newTime < currentRemainingTime * LEGIT_PATH_SWITCH_RATIO;
    }

    private void doSwing() {
        if (swing.isToggled()) mc.thePlayer.swingItem();
        else mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
    }

    private Color getHudColor() {
        return new Color(ArrayListModule.getHudColor(0.0D));
    }

    private boolean isLegitMode() {
        return (int) mode.getInput() == LEGIT_MODE;
    }

    private boolean canBreakBlocks() {
        return mc.playerController != null && !mc.thePlayer.isSpectator();
    }

    public boolean isReady() {
        return targetBed != null && readyToBreak
                && (!isLegitMode() || !shouldDeferToKillAura());
    }

    public boolean isBreaking() {
        if (isLegitMode()) {
            if (targetBed == null || mc.playerController == null) return false;
            IAccessorPlayerControllerMP controller =
                    (IAccessorPlayerControllerMP) mc.playerController;
            return holdingAttackKey || controller.isHittingBlock()
                    && targetBed.equals(controller.getCurrentBlock());
        }
        return targetBed != null && breaking;
    }

    public boolean hasActiveRotationIntent() {
        if (!isEnabled() || !Utils.nullCheck() || targetBed == null) {
            return false;
        }
        if (ModuleManager.blockIn != null && ModuleManager.blockIn.isPlacing()) {
            return false;
        }
        if (mc.theWorld.isAirBlock(targetBed)
                || (isLegitMode() && distanceToBlock(
                mc.thePlayer.getPositionEyes(1.0F), targetBed)
                > getLegitReach() + REACH_EPSILON)
                || (!isLegitMode()
                && !PlayerUtils.canReach(targetBed, range.getInput()))) {
            return false;
        }
        if (isLegitMode()) {
            return shouldClaimLegitRotation(readyToBreak, mc.thePlayer.isUsingItem());
        }
        return shouldClaimRotation(breakStage, readyToBreak, mc.thePlayer.isUsingItem());
    }

    static boolean shouldClaimRotation(int breakStage, boolean readyToBreak, boolean usingItem) {
        return breakStage == 1 || breakStage == 0 && readyToBreak && !usingItem;
    }

    static boolean shouldClaimLegitRotation(boolean readyToBreak, boolean usingItem) {
        return readyToBreak && !usingItem;
    }

    private boolean shouldDeferToKillAura() {
        if (!isPrioritizingKillAura()
                || ModuleManager.killAura == null
                || !ModuleManager.killAura.isEnabled()) {
            return false;
        }
        EntityLivingBase killAuraTarget = ModuleManager.killAura.getTarget();
        if (killAuraTarget == null || mc.thePlayer == null) {
            return false;
        }
        double deferRange = Math.max(
                ModuleManager.killAura.getAttackRangeSetting().getInput(),
                deferEnemyRange.getInput());
        return killAuraTarget.getDistanceToEntity(mc.thePlayer) <= deferRange;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onGameTick(GameTickEvent event) {
        if (!isEnabled() || !Utils.nullCheck()) {
            if (legitSession) {
                resetBreaking();
                restoreSlot();
                legitSession = false;
            }
            return;
        }
        runPendingWhitelistScan();
        refreshBedCompletionState();
        if (trackedBedDisappeared()) {
            completeTrackedBed();
        }
        if (isBedCompletionCoolingDown()) {
            releaseAttackKey();
            restoreSlot();
            return;
        }
        if (isLegitMode()) {
            if (!legitSession) {
                resetBreaking();
                restoreSlot();
                legitSession = true;
            }
            tickLegit();
        } else if (legitSession) {
            resetBreaking();
            restoreSlot();
            legitSession = false;
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreMotion(PreMotionEvent event) {
        runPendingWhitelistScan();
        if (!isEnabled() || !Utils.nullCheck()) return;
        if (isLegitMode()) return;
        if (isBedCompletionCoolingDown()) return;

        if (isOwnBedProtected()) {
            restoreSlot();
            resetBreaking();
            return;
        }

        if (trackedBedDisappeared()) {
            completeTrackedBed();
            return;
        }

        if (targetBed != null) {
            if (mc.theWorld.isAirBlock(targetBed) || !PlayerUtils.canReach(targetBed, range.getInput())) {
                restoreSlot();
                resetBreaking();
            } else if (!isBed) {
                BlockPos nearestBed = findNearestBed();
                if (nearestBed != null && mc.theWorld.getBlockState(nearestBed).getBlock() instanceof BlockBed) {
                    resetBreaking();
                }
            }
        }

        if (targetBed == null && canBreakBlocks()
                && timer.hasTimeElapsed(500.0D)) {
            targetBed = findNearestBed();
            breakStage = 0;
            tickCounter = 0;
            breakProgress = 0.0F;
            isBed = targetBed != null
                    && mc.theWorld.getBlockState(targetBed).getBlock() instanceof BlockBed;
            restoreSlot();
            if (targetBed != null) {
                trackBed(findSourceBed(targetBed));
                readyToBreak = true;
            }
        }

        if (targetBed == null
                || ModuleManager.blockIn != null && ModuleManager.blockIn.isPlacing()) {
            return;
        }

        IBlockState targetState = mc.theWorld.getBlockState(targetBed);
        int slot = AutoTool.findBestToolSlot(
                targetState.getBlock(), mc.thePlayer.inventory.currentItem);
        switch (breakStage) {
            case 0:
                if (!mc.thePlayer.isUsingItem()) {
                    doSwing();
                    mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                            Action.START_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                    doSwing();
                    mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                    breakStage = 1;
                }
                break;
            case 1:
                if ((int) mode.getInput() == HYPIXEL_MODE) readyToBreak = false;
                breaking = true;
                tickCounter++;
                breakProgress += getBreakDelta(
                        targetState, targetBed, slot, mc.thePlayer.onGround);
                float groundProgress = tickCounter * getBreakDelta(
                        targetState, targetBed, slot, mc.thePlayer.onGround);
                mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                if (breakProgress >= BREAK_THRESHOLD || groundProgress >= BREAK_THRESHOLD) {
                    if ((int) mode.getInput() == HYPIXEL_MODE) {
                        readyToBreak = true;
                        savedSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = slot;
                        syncHeldItem();
                        if (mc.thePlayer.isUsingItem()) {
                            savedSlot = mc.thePlayer.inventory.currentItem;
                            mc.thePlayer.inventory.currentItem =
                                    (mc.thePlayer.inventory.currentItem + 1) % 9;
                            syncHeldItem();
                        }
                    }
                    breaking = false;
                    mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                            Action.STOP_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                    doSwing();
                    IBlockState oldState = mc.theWorld.getBlockState(targetBed);
                    Block oldBlock = oldState.getBlock();
                    if (oldBlock.getMaterial() != Material.air) {
                        mc.theWorld.playAuxSFX(2001, targetBed, Block.getStateId(oldState));
                        mc.theWorld.setBlockToAir(targetBed);
                    }
                    if (oldBlock instanceof BlockBed) {
                        timer.reset();
                        completeTrackedBed();
                        return;
                    }
                    breakStage = 2;
                }
                break;
            case 2:
                restoreSlot();
                resetBreaking();
                break;
            default:
                breakStage = 0;
                break;
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onClientRotation(ClientRotationEvent event) {
        if (!hasActiveRotationIntent() || shouldDeferToKillAura()) return;
        if (event.isRotationClaimed() && event.getRotationOwner() == null) return;
        int priority = isPrioritizingKillAura()
                ? RotationPriority.BED_AURA_DEFERRED
                : RotationPriority.BED_AURA;
        if (!event.canClaimRotation(this, priority)) return;
        Vec3 aim = isLegitMode() && targetAim != null
                ? targetAim
                : new Vec3(targetBed.getX() + 0.5D,
                targetBed.getY() + 0.5D, targetBed.getZ() + 0.5D);
        net.minecraft.util.Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Float initialYaw = event.getInitialYaw();
        Float initialPitch = event.getInitialPitch();
        float[] rotations = RotationUtils.getRelativeRotations(
                aim.xCoord - eyes.xCoord,
                aim.yCoord - eyes.yCoord,
                aim.zCoord - eyes.zCoord,
                initialYaw != null ? initialYaw : RotationUtils.serverRotations[0],
                initialPitch != null ? initialPitch : RotationUtils.serverRotations[1],
                LEGIT_ROTATION_STEP, LEGIT_ROTATION_SMOOTHING);
        event.trySetRotation(
                this,
                priority,
                rotations[0],
                rotations[1]
        );
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        updateLegitAttackInput();
    }

    private void updateLegitAttackInput() {
        if (!isEnabled() || !legitSession || !isLegitMode()
                || !Utils.nullCheck() || targetBed == null
                || shouldDeferToKillAura() || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.thePlayer.isUsingItem()) {
            attackLossTicks = 0;
            releaseAttackKey();
            return;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        boolean targetHit = hit != null
                && hit.typeOfHit == MovingObjectType.BLOCK
                && targetBed.equals(hit.getBlockPos())
                && hit.hitVec != null
                && eyes.distanceTo(hit.hitVec) <= getLegitReach() + REACH_EPSILON;
        if (targetHit) {
            attackLossTicks = 0;
            pressAttackKey();
        } else {
            IAccessorPlayerControllerMP controller = (IAccessorPlayerControllerMP) mc.playerController;
            boolean hasProgress = controller != null && targetBed.equals(controller.getCurrentBlock())
                    && controller.getCurBlockDamageMP() > 0.0F;
            boolean inReach = distanceToBlock(eyes, targetBed) <= getLegitReach() + REACH_EPSILON
                    && !mc.theWorld.isAirBlock(targetBed);
            if ((hasProgress || holdingAttackKey) && inReach && ++attackLossTicks <= 4) {
                pressAttackKey();
            } else {
                attackLossTicks = 0;
                releaseAttackKey();
            }
        }
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent event) {
        if (isEnabled() && !isLegitMode() && isBreaking()
                && !PlayerStateTracker.attacking && !PlayerStateTracker.digging
                && !PlayerStateTracker.placing && !PlayerStateTracker.swinging) {
            doSwing();
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.isCanceled()) return;
        Packet<?> packet = event.getPacket();
        if (isEnabled() && packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange change = (S23PacketBlockChange) packet;
            if (change.getBlockState() != null) {
                handleTrackedBedUpdate(
                        change.getBlockPosition(), change.getBlockState().getBlock());
            }
        }
        if (isEnabled() && packet instanceof S22PacketMultiBlockChange) {
            for (S22PacketMultiBlockChange.BlockUpdateData change
                    : ((S22PacketMultiBlockChange) packet).getChangedBlocks()) {
                if (change.getBlockState() != null) {
                    handleTrackedBedUpdate(
                            change.getPos(), change.getBlockState().getBlock());
                }
            }
        }
        if (packet instanceof S02PacketChat) {
            String text = ((S02PacketChat) packet).getChatComponent().getFormattedText();
            if (text.contains("\u00a7e\u00a7lProtect your bed and destroy the enemy bed")
                    || text.contains("\u00a7e\u00a7lDestroy the enemy bed and then eliminate them")) {
                waitingForStart = true;
            }
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            trackHome((S08PacketPlayerPosLook) packet);
            if (waitingForStart) {
                waitingForStart = false;
                bedWhitelist.clear();
                scheduleWhitelistScan();
            }
        }
    }

    @Subscribe
    public void onPreAttack(PreAttackEvent event) {
        if (isLegitMode()) return;
        if (isReady() || targetBed != null && event.objectMouseOver != null
                && event.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onRightClick(RightClickMouseEvent event) {
        if (isReady()) event.setCanceled(true);
    }

    @Subscribe
    public void onSlotScroll(PreSlotScrollEvent event) {
        if (savedSlot != -1) event.setCanceled(true);
    }

    @Subscribe
    public void onSlotUpdate(SlotUpdateEvent event) {
        if (savedSlot != -1) event.setCanceled(true);
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) return;
        boolean hudVisible = (mc.currentScreen == null
                || mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)
                && !mc.gameSettings.showDebugInfo;
        boolean progressVisible = isEnabled() && hudVisible && shouldShowBreakVisual();

        if ((int) visualMode.getInput() == BAR_VISUAL_MODE) {
            if (progressVisible) progressBar.show(calcProgress());
            else progressBar.hide();
            if (hudVisible) progressBar.render();
            return;
        }

        progressBar.hide();
        if (hudVisible) progressBar.render();
        if (!progressVisible) return;

        String text = String.format("%d%%", (int) (calcProgress() * 100.0F));
        ScaledResolution resolution = new ScaledResolution(mc);
        MiauFontRenderer font = HudStyle.font();
        float width = font.getStringWidth(text);
        float x = resolution.getScaledWidth() / 2f - width / 2;
        float y = resolution.getScaledHeight() / 5f * 2;
        float paddingX = HudStyle.paddingX();
        float paddingY = HudStyle.paddingY();
        HudStyle.panelWithoutShaders(x - paddingX, y - paddingY,
                width + paddingX * 2, font.getFontHeight() + paddingY * 2,
                () -> font.drawString(text, x, y, 0xFFFFFFFF, false));
    }

    @Subscribe(priority = Priority.LOW)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled() || targetBed == null || mc.theWorld.isAirBlock(targetBed)) return;
        if (!isLegitMode()) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), targetBed,
                    (int) (calcProgress() * 10.0F) - 1);
        }
        drawBlockBox(targetBed, isBed ? getBedEspHeight() : 1.0D, getHudColor());
    }

    @Subscribe
    public void onWorldChange(idle.miau.mcp.event.entity.EntityJoinWorldEvent event) {
        if (event.entity != mc.thePlayer) return;
        waitingForStart = false;
        whitelistScanAt = -1L;
        bedWhitelist.clear();
        home = null;
        legitSession = false;
        progressBar.reset();
        resetBedCompletionState();
        resetBreaking();
        restoreSlot();
    }

    @Subscribe
    public void onChatReceived(idle.miau.mcp.event.client.ClientChatReceivedEvent event) {
        String text = event.message.getUnformattedText();
        if (text.contains("Protect your bed and destroy the enemy bed")
                || text.contains("Destroy the enemy bed and then eliminate them")) {
            waitingForStart = true;
        }
    }

    @Override
    public void onEnable() {
        legitSession = false;
        progressBar.reset();
        resetBedCompletionState();
        resetBreaking();
    }

    @Override
    public void onDisable() {
        progressBar.reset();
        resetBedCompletionState();
        resetBreaking();
        restoreSlot();
        legitSession = false;
        waitingForStart = false;
        whitelistScanAt = -1L;
    }

    @Override
    public void guiUpdate() {
        deferEnemyRange.setVisible(prioritizeKillAura.isToggled(), this);
    }

    @Override
    public String getInfo() {
        String selected = mode.getOptions()[(int) mode.getInput()].toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(selected.charAt(0)) + selected.substring(1);
    }

    private double getBedEspHeight() {
        BedESP bedEsp = (BedESP) ModuleManager.getModule(BedESP.class);
        if (bedEsp != null) {
            return bedEsp.getBlockHeight();
        }
        return 0.5625D;
    }

    private void drawBlockBox(BlockPos pos, double height, Color color) {
        double x = pos.getX() - mc.getRenderManager().viewerPosX;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = new AxisAlignedBB(x, y, z, x + 1.0D, y + height, z + 1.0D);
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        float alpha = color.getAlpha() / 255.0F;

        GL11.glPushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.drawBoundingBox(box, red, green, blue, alpha * 0.25F);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(red, green, blue, alpha);
        RenderGlobal.drawSelectionBoundingBox(box);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glPopMatrix();
    }

    private float[] getRotationsTo(double x, double y, double z) {
        double diffX = x - mc.thePlayer.posX;
        double diffY = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double diffZ = z - mc.thePlayer.posZ;
        double horizontal = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ);
        return new float[]{
                (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F,
                (float) -(Math.atan2(diffY, horizontal) * 180.0D / Math.PI)
        };
    }

    public boolean shouldOverrideFastMine() {
        return isEnabled() && isLegitMode() && targetBed != null;
    }

    public boolean isPrioritizingKillAura() {
        return prioritizeKillAura.isToggled();
    }

    public float getBreakSpeedMultiplier() {
        return 1.0F;
    }

    public int getBreakDelayTicks() {
        return 5;
    }

    public boolean shouldOverrideMouseOver() {
        return hasActiveRotationIntent() && !shouldDeferToKillAura();
    }

    private boolean shouldShowBreakVisual() {
        return targetBed != null && (!isBed || !surroundings.isToggled());
    }

    private boolean isOwnBedProtected() {
        return home != null && shouldProtectOwnBed(
                whiteListOwnBed.isToggled(),
                mc.thePlayer.getDistanceSq(home.xCoord, home.yCoord, home.zCoord));
    }

    private void trackHome(S08PacketPlayerPosLook packet) {
        if (!Utils.nullCheck()) return;
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        Set<S08PacketPlayerPosLook.EnumFlags> flags = packet.func_179834_f();
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X)) x += mc.thePlayer.posX;
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y)) y += mc.thePlayer.posY;
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Z)) z += mc.thePlayer.posZ;
        double deltaX = x - mc.thePlayer.posX;
        double deltaY = y - mc.thePlayer.posY;
        double deltaZ = z - mc.thePlayer.posZ;
        if (isHomeTeleport(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)) {
            home = new Vec3(x, y, z);
        }
    }

    static boolean shouldProtectOwnBed(boolean enabled, double distanceSq) {
        return enabled && distanceSq < OWN_BED_PROTECTION_DISTANCE_SQ;
    }

    static boolean isHomeTeleport(double distanceSq) {
        return distanceSq > HOME_TELEPORT_DISTANCE_SQ;
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
    }

    public boolean isActivelyMining() {
        return isBreaking();
    }

    public BlockPos getAuraTargetPos() {
        return targetBed;
    }

    public Vec3 getProtectedBreakAim() {
        if (!isEnabled() || mc.thePlayer == null) return null;
        if (targetBed != null) {
            if (pathAim != null) return pathAim;
            BlockPos aimBed = trackedBed != null ? trackedBed : targetBed;
            return new Vec3(aimBed.getX() + 0.5D,
                    aimBed.getY() + 0.5D, aimBed.getZ() + 0.5D);
        }
        return isBedCompletionCoolingDown() ? completedBreakAim : null;
    }

    public float getAuraBreakProgress() {
        return calcProgress();
    }

    private static final class BedTimer {
        private long time = System.currentTimeMillis();

        private boolean hasTimeElapsed(double delay) {
            return System.currentTimeMillis() - time >= delay;
        }

        private void reset() {
            time = System.currentTimeMillis();
        }
    }

    private static final class LegitTarget {
        private final BlockPos position;
        private final Vec3 aim;
        private final boolean bed;
        private final BlockPos bedPosition;
        private final Vec3 pathAim;
        private final double breakTime;
        private final int blockCount;
        private final double distance;

        private LegitTarget(
                BlockPos position,
                Vec3 aim,
                boolean bed,
                BlockPos bedPosition,
                Vec3 pathAim,
                double breakTime,
                int blockCount,
                double distance) {
            this.position = position;
            this.aim = aim;
            this.bed = bed;
            this.bedPosition = bedPosition;
            this.pathAim = pathAim;
            this.breakTime = breakTime;
            this.blockCount = blockCount;
            this.distance = distance;
        }

        private boolean isBetterThan(LegitTarget other) {
            return isBetterPath(
                    breakTime, blockCount, distance,
                    other.breakTime, other.blockCount, other.distance);
        }
    }
}
