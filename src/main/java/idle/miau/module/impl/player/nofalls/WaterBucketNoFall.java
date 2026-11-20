package idle.miau.module.impl.player.nofalls;

import idle.miau.event.ClientRotationEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.player.Scaffold;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BlockUtils;
import idle.miau.utility.PredictedMotion;
import idle.miau.utility.PredictionUtility;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEnchantmentTable;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WaterBucketNoFall extends NoFallMode {
    private static final int NO_SLOT = -1;
    private static final int PREDICTION_STEPS = 20;
    private static final int MAX_WATER_TICKS = 60;

    private final SliderSetting minHeight;
    private final SliderSetting pickupDelay;
    private final ButtonSetting switchBack;
    private final SliderSetting rotationSpeed;

    private boolean waterPickupActive;
    private boolean placementActive;
    private BlockPos waterPosition;
    private int previousSlot = NO_SLOT;
    private int waterTicks;
    private int restoreDelayTicks;

    public WaterBucketNoFall(NoFallModule owner) {
        super("MLG", owner);
        register(minHeight = new SliderSetting(
                "Min height", "", 2.5D, 1.0D, 5.0D, 0.1D, "Min Fall"));
        register(pickupDelay = new SliderSetting(
                "PickupDelay", 3.0D, 0.0D, 20.0D, 1.0D));
        register(switchBack = new ButtonSetting("SwitchBack", true));
        register(rotationSpeed = new SliderSetting(
                "RotationSpeed", 6.0D, 2.0D, 10.0D, 0.1D));
    }

    @Override
    public void updateVisibility() {
        boolean active = owner.isMode("MLG");
        minHeight.setVisible(active, owner);
        pickupDelay.setVisible(active, owner);
        switchBack.setVisible(active, owner);
        rotationSpeed.setVisible(active, owner);
    }

    @Override
    public void onEnable() {
        reset(false);
    }

    @Override
    public void onDisable() {
        reset(true);
    }

    @Override
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck()) {
            reset(false);
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        if (restoreDelayTicks > 0) {
            restoreDelayTicks--;
            if (restoreDelayTicks == 0) {
                restorePreviousSlot();
            }
            return;
        }

        if (waterPickupActive) {
            processWaterPickup(event);
            return;
        }

        if (player.onGround) {
            placementActive = false;
            restorePreviousSlot();
            return;
        }

        if (placementActive) {
            player.setSneaking(mc.gameSettings.keyBindSneak.isKeyDown());
            return;
        }

        if (player.motionY >= 0.0D || player.fallDistance < minHeight.getInput()) {
            return;
        }

        boolean scaffoldControlStateActive = isScaffoldActive();
        PredictedMotion[] predictions = PredictionUtility.predict(player, PREDICTION_STEPS);
        PredictedMotion landing = findLandingPrediction(
                predictions, scaffoldControlStateActive);
        if (landing == null) {
            return;
        }

        if (tryWaterPlacement(event, landing)) {
            return;
        }
        tryLadderPlacement(event, landing, scaffoldControlStateActive);
    }

    @Override
    public void onWorldUnload(WorldEvent.Unload event) {
        reset(false);
    }

    private PredictedMotion findLandingPrediction(
            PredictedMotion[] predictions, boolean scaffoldControlStateActive) {
        for (int index = 1; index < predictions.length; index++) {
            PredictedMotion prediction = predictions[index];
            if (prediction.isInWater()
                    || prediction.isInLava() && !scaffoldControlStateActive) {
                break;
            }
            if (prediction.isOnGround()) {
                return prediction;
            }
        }
        return null;
    }

    private void processWaterPickup(ClientRotationEvent event) {
        EntityPlayerSP player = mc.thePlayer;
        if (!player.onGround) {
            return;
        }

        waterTicks++;
        if (waterTicks > MAX_WATER_TICKS) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        if (waterPosition == null) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        Block waterBlock = mc.theWorld.getBlockState(waterPosition).getBlock();
        if (waterBlock != Blocks.water && waterBlock != Blocks.flowing_water) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        int emptyBucketSlot = findBucket(false);
        if (emptyBucketSlot == NO_SLOT) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        Vec3 eye = player.getPositionEyes(1.0F);
        double bestScore = Double.MAX_VALUE;
        float[] bestRotations = null;
        for (float x = 0.1F; x <= 0.9F; x += 0.2F) {
            for (float z = 0.1F; z <= 0.9F; z += 0.2F) {
                Vec3 point = new Vec3(
                        waterPosition.getX() + x,
                        waterPosition.getY() + 0.5D,
                        waterPosition.getZ() + z);
                MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
                if (hit == null
                        || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                        || !waterPosition.equals(hit.getBlockPos())) {
                    continue;
                }

                float[] rotations = RotationUtils.getRotationsFromEye(
                        eye, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
                double score = Math.abs(MathHelper.wrapAngleTo180_float(
                        rotations[0] - currentYaw(event))) * 3.0D
                        + Math.abs(rotations[1] - currentPitch(event));
                if (score < bestScore) {
                    bestScore = score;
                    bestRotations = rotations;
                }
            }
        }

        if (bestRotations == null) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        if (!applyRotation(event, bestRotations)) {
            return;
        }
        if (waterTicks <= (int) pickupDelay.getInput()) {
            return;
        }

        selectSlot(emptyBucketSlot);
        MovingObjectPosition hit = rayTrace(
                5.0D, currentYaw(event), currentPitch(event));
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        Block hitBlock = mc.theWorld.getBlockState(hit.getBlockPos()).getBlock();
        if ((hitBlock != Blocks.water && hitBlock != Blocks.flowing_water)
                || !waterPosition.equals(hit.getBlockPos())) {
            restorePreviousSlot();
            resetWaterState();
            return;
        }

        if (mc.playerController.sendUseItem(player, mc.theWorld, player.getHeldItem())) {
            restoreDelayTicks = switchBack.isToggled() ? 5 : 0;
            resetWaterState();
        }
    }

    private boolean tryWaterPlacement(
            ClientRotationEvent event, PredictedMotion landing) {
        EntityPlayerSP player = mc.thePlayer;
        int waterBucketSlot = findBucket(true);
        if (waterBucketSlot == NO_SLOT) {
            return false;
        }

        BlockPos blockBelow = new BlockPos(
                landing.getCenterX(), landing.getMinY(), landing.getCenterZ()).down();
        IBlockState state = mc.theWorld.getBlockState(blockBelow);
        AxisAlignedBB collisionBox = state.getBlock().getCollisionBoundingBox(
                mc.theWorld, blockBelow, state);
        double waterY = collisionBox != null
                ? collisionBox.maxY - 0.1D
                : blockBelow.getY() + 0.9D;
        Vec3 eye = player.getPositionEyes(1.0F);
        double bestScore = Double.MAX_VALUE;
        Vec3 bestHitVector = null;
        float[] bestRotations = null;

        for (float x = 0.05F; x <= 0.95F; x += 0.1F) {
            for (float z = 0.05F; z <= 0.95F; z += 0.1F) {
                Vec3 point = new Vec3(
                        blockBelow.getX() + x,
                        waterY,
                        blockBelow.getZ() + z);
                MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
                if (hit == null
                        || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                        || hit.sideHit != EnumFacing.UP
                        || !blockBelow.equals(hit.getBlockPos())) {
                    continue;
                }

                float[] rotations = RotationUtils.getRotationsFromEye(
                        eye, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
                double score = Math.abs(MathHelper.wrapAngleTo180_float(
                        rotations[0] - currentYaw(event))) * 3.0D
                        + Math.abs(rotations[1] - currentPitch(event));
                double centerScore = Math.hypot(
                        hit.hitVec.zCoord - (blockBelow.getZ() + 0.5D),
                        hit.hitVec.xCoord - (blockBelow.getX() + 0.5D)) * 15.0D;
                score += centerScore;
                if (score < bestScore) {
                    bestScore = score;
                    bestHitVector = hit.hitVec;
                    bestRotations = rotations;
                }
            }
        }

        if (bestHitVector == null || bestRotations == null) {
            return true;
        }

        selectSlot(waterBucketSlot);
        if (isSpecialInteractionBlock(state)) {
            player.setSneaking(true);
        }
        if (!applyRotation(event, bestRotations)) {
            return true;
        }

        MovingObjectPosition aimedBlock = rayTrace(
                8.0D, currentYaw(event), currentPitch(event));
        if (aimedBlock == null
                || aimedBlock.sideHit != EnumFacing.UP) {
            return true;
        }

        BlockPos aboveHit = aimedBlock.getBlockPos().up();
        AxisAlignedBB aboveHitBox = new AxisAlignedBB(
                aboveHit.getX(), aboveHit.getY(), aboveHit.getZ(),
                aboveHit.getX() + 1.0D,
                aboveHit.getY() + 1.0D,
                aboveHit.getZ() + 1.0D);
        if (!landing.getBoundingBox().intersectsWith(aboveHitBox)) {
            return true;
        }

        if (mc.playerController.sendUseItem(player, mc.theWorld, player.getHeldItem())) {
            placementActive = true;
            waterPickupActive = true;
            waterPosition = aboveHit;
            waterTicks = 0;
        }
        return true;
    }

    private boolean tryLadderPlacement(
            ClientRotationEvent event,
            PredictedMotion landing,
            boolean scaffoldControlStateActive) {
        EntityPlayerSP player = mc.thePlayer;
        int ladderSlot = findLadder();
        if (ladderSlot == NO_SLOT) {
            return false;
        }

        List<BlockPos> supportBlocks = new ArrayList<>();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                BlockPos candidate = new BlockPos(
                        landing.getCenterX() + offsetX,
                        landing.getMinY(),
                        landing.getCenterZ() + offsetZ);
                AxisAlignedBB candidateBox = new AxisAlignedBB(
                        candidate.getX(), candidate.getY(), candidate.getZ(),
                        candidate.getX() + 1.0D,
                        candidate.getY() + 1.0D,
                        candidate.getZ() + 1.0D);
                if (!candidateBox.intersectsWith(landing.getBoundingBox())) {
                    continue;
                }

                BlockPos below = candidate.down();
                if (BlockUtils.getBlock(below).isReplaceable(mc.theWorld, below)
                        || !BlockUtils.getBlock(candidate).isReplaceable(mc.theWorld, candidate)) {
                    continue;
                }

                for (EnumFacing facing : new EnumFacing[]{
                        EnumFacing.SOUTH,
                        EnumFacing.WEST,
                        EnumFacing.NORTH,
                        EnumFacing.EAST}) {
                    BlockPos neighbor = candidate.offset(facing);
                    if (!BlockUtils.getBlock(neighbor).isReplaceable(mc.theWorld, neighbor)) {
                        supportBlocks.add(below);
                        if (!scaffoldControlStateActive) {
                            break;
                        }
                    }
                    if (scaffoldControlStateActive) {
                        break;
                    }
                }
            }
        }

        if (supportBlocks.isEmpty()) {
            return false;
        }

        Vec3 eye = player.getPositionEyes(1.0F);
        double bestScore = Double.MAX_VALUE;
        Vec3 bestHitVector = null;
        BlockPos bestSupportBlock = null;
        IBlockState bestSupportState = null;
        float[] bestRotations = null;

        for (BlockPos supportBlock : supportBlocks) {
            IBlockState supportState = mc.theWorld.getBlockState(supportBlock);
            AxisAlignedBB collisionBox = supportState.getBlock().getCollisionBoundingBox(
                    mc.theWorld, supportBlock, supportState);
            double supportY = collisionBox != null
                    ? collisionBox.maxY - 0.1D
                    : supportBlock.getY() + 0.9D;
            for (float x = 0.05F; x <= 0.95F; x += 0.1F) {
                for (float z = 0.05F; z <= 0.95F; z += 0.1F) {
                    Vec3 point = new Vec3(
                            supportBlock.getX() + x,
                            supportY,
                            supportBlock.getZ() + z);
                    MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
                    if (hit == null
                            || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                            || hit.sideHit != EnumFacing.UP
                            || !supportBlock.equals(hit.getBlockPos())) {
                        continue;
                    }

                    float[] rotations = RotationUtils.getRotationsFromEye(
                            eye, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
                    double score = Math.abs(MathHelper.wrapAngleTo180_float(
                            rotations[0] - currentYaw(event))) * 3.0D
                            + Math.abs(rotations[1] - currentPitch(event));
                    double centerScore = Math.hypot(
                            hit.hitVec.zCoord - (supportBlock.getZ() + 0.5D),
                            hit.hitVec.xCoord - (supportBlock.getX() + 0.5D)) * 15.0D;
                    score += centerScore;
                    if (score < bestScore) {
                        bestScore = score;
                        bestHitVector = hit.hitVec;
                        bestRotations = rotations;
                        bestSupportBlock = supportBlock;
                        bestSupportState = supportState;
                    }
                }
            }
        }

        if (bestHitVector == null || bestSupportBlock == null || bestRotations == null) {
            return true;
        }

        selectSlot(ladderSlot);
        if (isSpecialInteractionBlock(bestSupportState)) {
            player.setSneaking(true);
        }
        if (!applyRotation(event, bestRotations)) {
            return true;
        }

        MovingObjectPosition aimedBlock = rayTrace(
                interactionRange(), currentYaw(event), currentPitch(event));
        if (aimedBlock == null
                || aimedBlock.sideHit != EnumFacing.UP
                || !bestSupportBlock.equals(aimedBlock.getBlockPos())) {
            return true;
        }

        if (mc.playerController.onPlayerRightClick(
                player,
                mc.theWorld,
                player.getHeldItem(),
                bestSupportBlock,
                EnumFacing.UP,
                aimedBlock.hitVec)) {
            placementActive = true;
            waterTicks = 0;
        }
        return true;
    }

    private boolean applyRotation(ClientRotationEvent event, float[] targetRotation) {
        float[] current = {currentYaw(event), currentPitch(event)};
        float[] next = RotationUtils.getCappedRotations(
                current, targetRotation, rotationLimit());
        return event.trySetRotation(
                owner,
                RotationPriority.BLOCK_PLACEMENT,
                next[0],
                next[1],
                false);
    }

    private MovingObjectPosition rayTrace(double distance, float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(
                look.xCoord * distance,
                look.yCoord * distance,
                look.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eye, end, false, true, false);
    }

    private float currentYaw(ClientRotationEvent event) {
        return event.yaw == null ? mc.thePlayer.rotationYaw : event.yaw;
    }

    private float currentPitch(ClientRotationEvent event) {
        return event.pitch == null ? mc.thePlayer.rotationPitch : event.pitch;
    }

    private float interactionRange() {
        return mc.playerController == null
                ? 4.5F
                : mc.playerController.getBlockReachDistance();
    }

    private float rotationLimit() {
        return (float) Math.pow(rotationSpeed.getInput() / 10.0D, 1.5D) * 180.0F;
    }

    private int findLadder() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null
                    && stack.getItem() instanceof ItemBlock
                    && ((ItemBlock) stack.getItem()).getBlock() == Blocks.ladder) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private int findBucket(boolean filled) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            if (filled && stack.getItem() == Items.water_bucket
                    || !filled && stack.getItem() == Items.bucket) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private void selectSlot(int slot) {
        if (slot < 0 || slot >= 9 || mc.thePlayer.inventory.currentItem == slot) {
            return;
        }
        if (switchBack.isToggled() && previousSlot == NO_SLOT) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = slot;
        syncCurrentItem();
    }

    private void restorePreviousSlot() {
        if (previousSlot != NO_SLOT
                && switchBack.isToggled()
                && mc.thePlayer != null
                && previousSlot < 9
                && mc.thePlayer.inventory.currentItem != previousSlot) {
            mc.thePlayer.inventory.currentItem = previousSlot;
            syncCurrentItem();
        }
        previousSlot = NO_SLOT;
    }

    private void syncCurrentItem() {
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    private void reset(boolean restoreSlot) {
        if (restoreSlot && Utils.nullCheck()) {
            restorePreviousSlot();
        } else {
            previousSlot = NO_SLOT;
        }
        resetWaterState();
        placementActive = false;
        restoreDelayTicks = 0;
    }

    private void resetWaterState() {
        waterPickupActive = false;
        waterPosition = null;
        waterTicks = 0;
    }

    private boolean isScaffoldActive() {
        Scaffold scaffold = (Scaffold) ModuleManager.getModule(Scaffold.class);
        return scaffold != null && scaffold.isEnabled();
    }

    private boolean isSpecialInteractionBlock(IBlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof BlockContainer
                || block instanceof BlockWorkbench
                || block instanceof BlockAnvil
                || block instanceof BlockBed
                || block instanceof BlockDoor
                || block instanceof BlockFenceGate
                || block instanceof BlockTrapDoor
                || block instanceof BlockEnchantmentTable;
    }
}
