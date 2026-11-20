package idle.miau.module.impl.player;

import idle.miau.event.EventMovementInput;
import idle.miau.event.JumpEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.SilentRotationManager;
import idle.miau.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

final class ScaffoldTelly2Mode {
    static final float GROUND_YAW_STEP = 180.0F;
    static final float FIRST_AIRBORNE_YAW_STEP = 120.0F;
    static final float LATER_AIRBORNE_YAW_STEP = 40.0F;
    static final float GROUND_PITCH_STEP = 180.0F;
    static final float AIRBORNE_PITCH_STEP = 90.0F;

    private static final float EPSILON = 1.0E-4F;
    private static final float VULCAN_PITCH_SAMPLE_MIN = 0.15F;
    private static final long VULCAN_PITCH_EXPANDER = 1L << 24;
    private static final long VULCAN_SCAFFOLD_D_MIN_GCD = 131072L;
    private static final int UNKNOWN_PITCH_STEPS = -1;
    private static final long UNKNOWN_PITCH_RAW = -1L;
    private static final int MAX_COPRIME_SEARCH = 64;
    private static final float MAX_PLACEMENT_REPAIR_DEGREES = 3.6F;
    private static final int MAX_YAW_REPAIR_STEPS = 8;
    private static final double PLACEMENT_AIM_LEAD = 1.20D;
    private static final EnumFacing[] PLACEMENT_FACES = {
            EnumFacing.UP, EnumFacing.EAST, EnumFacing.WEST,
            EnumFacing.SOUTH, EnumFacing.NORTH
    };
    private static final double[] FACE_SAMPLES = {
            0.10D, 0.20D, 0.30D, 0.40D, 0.50D,
            0.60D, 0.70D, 0.80D, 0.90D
    };

    private final Scaffold owner;
    private final Minecraft mc = Minecraft.getMinecraft();

    private int posY;
    private int offGroundTicks;
    private float scaffoldYaw;
    private float scaffoldPitch;
    private float currentYawStep = GROUND_YAW_STEP;
    private BlockPos blockPos;
    private EnumFacing facing;
    private boolean rotationClaimed;
    private boolean runtimeRotationInitialized;
    private float lastRuntimeYaw;
    private float lastRuntimePitch;
    private boolean dispatchedPitchInitialized;
    private float lastDispatchedPitch;
    private int lastDispatchedPitchDeltaSteps = UNKNOWN_PITCH_STEPS;
    private long lastDispatchedPitchDeltaRaw = UNKNOWN_PITCH_RAW;
    private boolean midCycleSneakUsed;
    private boolean midCycleSneakAirborne;
    private boolean midCycleSneakEligible;
    private int midCycleSneakCyclesUntilNext;
    private int midCycleSneakInterval = 1;

    ScaffoldTelly2Mode(Scaffold owner) {
        this.owner = owner;
    }

    void reset() {
        EntityPlayerSP player = owner.getPlayerForTelly();
        posY = player == null ? 0 : MathHelper.floor_double(player.posY - 1.0D);
        offGroundTicks = 0;
        currentYawStep = GROUND_YAW_STEP;
        blockPos = null;
        facing = null;
        rotationClaimed = false;
        runtimeRotationInitialized = false;
        dispatchedPitchInitialized = false;
        lastDispatchedPitch = 0.0F;
        lastDispatchedPitchDeltaSteps = UNKNOWN_PITCH_STEPS;
        lastDispatchedPitchDeltaRaw = UNKNOWN_PITCH_RAW;
        midCycleSneakUsed = false;
        midCycleSneakAirborne = player != null && !player.onGround;
        midCycleSneakEligible = false;
        midCycleSneakCyclesUntilNext = 0;
        midCycleSneakInterval = 1;
        owner.setTellySneak(false);
    }

    RotationCommand prepareRotation(float previousYaw, float previousPitch) {
        rotationClaimed = false;
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (!validContext(player)) {
            clearTarget();
            return null;
        }

        if (!owner.hasTelly2PlacementBlock()) {
            clearTarget();
            return null;
        }

        if (!dispatchedPitchInitialized) {
            lastDispatchedPitch = previousPitch;
            dispatchedPitchInitialized = true;
        }

        processBlockData(player.posX, player.posZ);
        boolean moving = isMovementKeyDown();

        if (player.hurtTime == 0 && player.onGround) {
            scaffoldYaw = movementYawFromKeys(player.rotationYaw);
        }

        float targetYaw;
        float targetPitch;
        float pitchStep;
        if (player.onGround && moving) {
            scaffoldYaw = movementYawFromKeys(player.rotationYaw);
            targetYaw = scaffoldYaw;
            targetPitch = scaffoldPitch;
            currentYawStep = GROUND_YAW_STEP;
            pitchStep = GROUND_PITCH_STEP;
            offGroundTicks = 0;
        }
        else {
            if (blockPos != null && facing != null) {
                float[] direction = placementDirection(
                        player, blockPos, facing);
                scaffoldYaw = direction[0];
                scaffoldPitch = direction[1];
            }
            else if (moving && !player.onGround) {
                scaffoldYaw = preferredPlacementYaw(
                        movementYawFromKeys(player.rotationYaw),
                        owner.getTellyYawOffset());
            }
            targetYaw = scaffoldYaw;
            targetPitch = scaffoldPitch;
            if (player.onGround) {
                currentYawStep = GROUND_YAW_STEP;
                offGroundTicks = 0;
            }
            else {
                currentYawStep = cadenceForAirTicks(offGroundTicks);
                offGroundTicks++;
            }
            pitchStep = AIRBORNE_PITCH_STEP;
        }

        float gcd = mouseGcd();
        RotationCommand command = runtimeTelly2Rotation(
                previousYaw, previousPitch,
                targetYaw, targetPitch,
                currentYawStep, pitchStep,
                gcd);
        command = new RotationCommand(command.yaw,
                owner.limitScaffoldPitch(command.pitch, previousPitch, gcd));
        command = safeguardVulcanPitch(
                previousPitch, command, gcd);
        command = repairPlacementRotation(
                previousPitch, command, gcd);
        lastRuntimeYaw = command.yaw;
        lastRuntimePitch = command.pitch;
        runtimeRotationInitialized = true;
        return command;
    }

    void onRotationClaimed(boolean claimed) {
        rotationClaimed = claimed;
    }

    void onClick() {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (!validContext(player) || !rotationClaimed
                || !RotationHelper.get().isRotationOwner(owner)
                || !isPlacementStillRequired()) {
            return;
        }
        if (!owner.ensureTelly2PlacementBlock()) {
            return;
        }

        mc.setRightClickDelayTimer(0);
        MovingObjectPosition hit = mc.objectMouseOver;
        if (!canPlaceCurrentHit(hit)) {
            return;
        }
        owner.placeTelly2Block(blockPos, facing, hit);
    }

    void onPrePlayerInput(
            PrePlayerInputEvent event, boolean safeWalk,
            boolean midCycleSneak) {
        onPrePlayerInput(event, safeWalk, midCycleSneak, 1);
    }

    void onPrePlayerInput(
            PrePlayerInputEvent event, boolean safeWalk,
            boolean midCycleSneak, int everyNCycles) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (event == null || !validContext(player)) {
            return;
        }
        int interval = clampSneakInterval(everyNCycles);
        if (midCycleSneakInterval != interval) {
            midCycleSneakInterval = interval;
            midCycleSneakCyclesUntilNext = 0;
            midCycleSneakEligible = false;
        }
        if (!midCycleSneak) {
            midCycleSneakAirborne = !player.onGround;
            midCycleSneakEligible = false;
            midCycleSneakCyclesUntilNext = 0;
            midCycleSneakUsed = false;
        }
        if (player.onGround) {
            midCycleSneakUsed = false;
            midCycleSneakAirborne = false;
            midCycleSneakEligible = false;
        }
        else if (midCycleSneak) {
            if (!midCycleSneakAirborne) {
                midCycleSneakAirborne = true;
                if (midCycleSneakCyclesUntilNext > 0) {
                    midCycleSneakCyclesUntilNext--;
                }
                midCycleSneakEligible = midCycleSneakCyclesUntilNext == 0;
            }
            if (!owner.isHoldingTellySneakRelease() && shouldPulseMidCycle(
                player.onGround, offGroundTicks,
                player.motionY, midCycleSneakUsed,
                midCycleSneakEligible)) {
                if (event.isSneak()
                        || owner.requestTellySneakPulse(event, "Telly2/Mid cycle")) {
                    midCycleSneakUsed = true;
                    midCycleSneakEligible = false;
                    midCycleSneakCyclesUntilNext = interval;
                }
            }
        }
        if (shouldAutoJump(
                player.onGround, isMovementKeyDown(), physicalJumpDown())) {
            event.setJump(true);
        }

        if (shouldApplySafeWalk(
                safeWalk, player.onGround,
                event.isJump() || physicalJumpDown(),
                mc.theWorld.getCollidingBoundingBoxes(
                        player,
                        player.getEntityBoundingBox().addCoord(
                                player.motionX, player.motionY, player.motionZ)
                                .expand(-0.175D, 0.0D, -0.175D)).isEmpty())) {
            if (!event.isSneak()) {
                owner.noteScaffoldSneak("Telly2/SafeWalk");
            }
            event.setSneak(true);
        }
    }

    static boolean shouldApplySafeWalk(
            boolean enabled, boolean onGround,
            boolean jumpRequested, boolean edge) {
        return enabled && onGround && !jumpRequested && edge;
    }

    void applyMovementInput(EventMovementInput event) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        Float emittedYaw = RotationHelper.get().getServerYaw();
        if (event == null || player == null || !rotationClaimed
                || !RotationHelper.get().isRotationOwner(owner)
                || emittedYaw == null || emittedYaw.isNaN()
                || event.getSpeed() == 0.0F && event.getStrafe() == 0.0F) {
            return;
        }

        SilentRotationManager.ResolvedInput corrected =
                resolveMovementInput(
                        player.rotationYaw, emittedYaw,
                        event.getSpeed(), event.getStrafe(),
                        player.movementInput != null
                                && player.movementInput.sneak);
        event.setSpeed(corrected.forward);
        event.setStrafe(corrected.strafe);
    }

    static SilentRotationManager.ResolvedInput resolveMovementInput(
            float cameraYaw, float movementYaw,
            float forwardInput, float strafeInput, boolean sneaking) {
        return SilentRotationManager.resolveMovementInput(
                cameraYaw, movementYaw, forwardInput, strafeInput, sneaking);
    }

    void applyStrafe(StrafeEvent event) {
        Float emittedYaw = RotationHelper.get().getServerYaw();
        if (event != null && rotationClaimed
                && RotationHelper.get().isRotationOwner(owner)
                && emittedYaw != null && !emittedYaw.isNaN()) {
            event.setYaw(emittedYaw);
        }
    }

    void applyJump(JumpEvent event) {
        Float emittedYaw = RotationHelper.get().getServerYaw();
        if (event != null && rotationClaimed
                && RotationHelper.get().isRotationOwner(owner)
                && emittedYaw != null && !emittedYaw.isNaN()) {
            event.setYaw(emittedYaw);
        }
    }

    void onPacketDispatched(Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)
                || !((C03PacketPlayer) packet).getRotating()) {
            return;
        }
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        float pitch = movement.getPitch();
        if (dispatchedPitchInitialized) {
            lastDispatchedPitchDeltaSteps = pitchDeltaSteps(
                    pitch - lastDispatchedPitch, mouseGcd());
            lastDispatchedPitchDeltaRaw = expandedPitchDelta(
                    pitch - lastDispatchedPitch);
        }
        else {
            dispatchedPitchInitialized = true;
            lastDispatchedPitchDeltaSteps = UNKNOWN_PITCH_STEPS;
        }
        lastDispatchedPitch = pitch;
    }

    private boolean validContext(EntityPlayerSP player) {
        return player != null && mc.theWorld != null
                && mc.playerController != null && owner.isTelly2Mode();
    }

    private void clearTarget() {
        blockPos = null;
        facing = null;
    }

    private void processBlockData(double x, double z) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (!owner.isTelly2SameYEnabled() || physicalJumpDown()) {
            posY = MathHelper.floor_double(player.posY - 1.0D);
        }
        PlacementTarget target = findPlacementTarget(x, z);
        blockPos = target == null ? null : target.support;
        facing = target == null ? null : target.face;
    }

    private PlacementTarget findPlacementTarget(double x, double z) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        double targetX = x + player.motionX;
        double targetZ = z + player.motionZ;
        BlockPos goal = new BlockPos(targetX, posY, targetZ);
        if (isPosSolid(goal)) {
            return null;
        }

        boolean allowUp = !player.onGround;
        PlacementTarget best = null;
        int bestRemainingSteps = Integer.MAX_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestSupportDistance = Double.POSITIVE_INFINITY;
        BlockPos origin = new BlockPos(x, posY, z);
        for (int scanY = posY - 1; scanY <= posY; scanY++) {
            for (int scanX = origin.getX() - 5;
                 scanX <= origin.getX() + 5; scanX++) {
                for (int scanZ = origin.getZ() - 5;
                     scanZ <= origin.getZ() + 5; scanZ++) {
                    BlockPos support = new BlockPos(scanX, scanY, scanZ);
                    if (!isValidBlock(support)) {
                        continue;
                    }
                    for (EnumFacing face : PLACEMENT_FACES) {
                        if (face == EnumFacing.UP && !allowUp) {
                            continue;
                        }
                        BlockPos destination = support.offset(face);
                        if (!advancesTowardGoal(support, destination, goal)
                                || !canFillDestination(destination)) {
                            continue;
                        }
                        int remainingSteps = gridDistance(destination, goal);
                        double distance = squaredDistance(
                                targetX, posY + 0.5D, targetZ,
                                destination.getX() + 0.5D,
                                destination.getY() + 0.5D,
                                destination.getZ() + 0.5D);
                        double supportDistance = squaredDistance(
                                x, player.posY, z,
                                support.getX() + 0.5D,
                                support.getY() + 0.5D,
                                support.getZ() + 0.5D);
                        if (remainingSteps < bestRemainingSteps
                                || remainingSteps == bestRemainingSteps
                                && (distance < bestDistance
                                || distance == bestDistance
                                && supportDistance < bestSupportDistance)) {
                            bestRemainingSteps = remainingSteps;
                            bestDistance = distance;
                            bestSupportDistance = supportDistance;
                            best = new PlacementTarget(support, face);
                        }
                    }
                }
            }
        }
        return best;
    }

    static boolean advancesTowardGoal(
            BlockPos support, BlockPos destination, BlockPos goal) {
        return destination.getY() == goal.getY()
                && gridDistance(destination, goal) < gridDistance(support, goal);
    }

    private static int gridDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    private boolean canFillDestination(BlockPos destination) {
        return mc.theWorld.getBlockState(destination).getBlock()
                .isReplaceable(mc.theWorld, destination)
                && !destinationIntersectsPlayer(destination);
    }

    private boolean isPlacementStillRequired() {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (blockPos == null || facing == null || player == null
                || !isValidBlock(blockPos)) {
            return false;
        }
        BlockPos destination = blockPos.offset(facing);
        if (!canFillDestination(destination)) {
            return false;
        }
        PlacementTarget refreshed = findPlacementTarget(player.posX, player.posZ);
        return refreshed != null
                && destination.equals(refreshed.support.offset(refreshed.face));
    }

    private boolean destinationIntersectsPlayer(BlockPos destination) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (player == null || destination == null) {
            return false;
        }
        AxisAlignedBB destinationBox = new AxisAlignedBB(
                destination.getX(), destination.getY(), destination.getZ(),
                destination.getX() + 1.0D,
                destination.getY() + 1.0D,
                destination.getZ() + 1.0D);
        return player.getEntityBoundingBox().intersectsWith(destinationBox);
    }

    private static final class PlacementTarget {
        final BlockPos support;
        final EnumFacing face;

        PlacementTarget(BlockPos support, EnumFacing face) {
            this.support = support;
            this.face = face;
        }
    }

    private boolean isValidBlock(BlockPos position) {
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return !(block instanceof BlockLiquid)
                && !(block instanceof BlockAir)
                && !(block instanceof BlockChest)
                && !(block instanceof BlockFurnace);
    }

    private boolean isPosSolid(BlockPos position) {
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return (block.getMaterial().isSolid() || !block.isTranslucent()
                || block instanceof BlockLadder
                || block instanceof BlockCarpet
                || block instanceof BlockSnow
                || block instanceof BlockSkull)
                && !block.getMaterial().isLiquid()
                && !(block instanceof BlockContainer);
    }

    private float[] placementDirection(
            EntityPlayerSP player, BlockPos position, EnumFacing side) {
        float[] baseDirection = directionToBlock(player, position, side);
        float offset = owner.getTellyYawOffset();
        float preferredYaw = preferredPlacementYaw(
                movementYawFromKeys(player.rotationYaw), offset);
        float[] best = findRayValidFaceRotation(
                position, side, preferredYaw, baseDirection[1]);
        if (best != null) {
            return best;
        }

        return fallbackPlacementRotation(
                player, position, side, preferredYaw, baseDirection[1]);
    }

    private float[] fallbackPlacementRotation(
            EntityPlayerSP player, BlockPos position, EnumFacing side,
            float preferredYaw, float fallbackPitch) {
        if (side == EnumFacing.UP || side == EnumFacing.DOWN) {
            return new float[]{preferredYaw, fallbackPitch};
        }
        float travelYaw = movementYawFromKeys(player.rotationYaw);
        double radians = Math.toRadians(travelYaw);
        double directionX = -Math.sin(radians);
        double directionZ = Math.cos(radians);
        double momentum = Math.sqrt(
                player.motionX * player.motionX
                        + player.motionZ * player.motionZ);
        double lead = PLACEMENT_AIM_LEAD + Math.min(0.45D, momentum);
        double virtualX = player.posX + directionX * lead;
        double virtualZ = player.posZ + directionZ * lead;
        Vec3 point = pointOnFace(position, side, 0.5D, 0.5D);
        double horizontal = MathHelper.sqrt_double(
                (point.xCoord - virtualX) * (point.xCoord - virtualX)
                        + (point.zCoord - virtualZ) * (point.zCoord - virtualZ));
        if (horizontal <= EPSILON) {
            return new float[]{preferredYaw, fallbackPitch};
        }
        double yDifference = point.yCoord - player.posY
                - (double) player.getEyeHeight();
        float pitch = (float) -Math.toDegrees(
                Math.atan2(yDifference, horizontal));
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            pitch = fallbackPitch;
        }
        return new float[]{preferredYaw,
                MathHelper.clamp_float(pitch, 65.0F, 89.0F)};
    }

    private float[] findRayValidFaceRotation(
            BlockPos position, EnumFacing side,
            float preferredYaw, float preferredPitch) {
        float[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (double first : FACE_SAMPLES) {
            for (double second : FACE_SAMPLES) {
                Vec3 point = pointOnFace(position, side, first, second);
                float[] candidate = rotationsToPoint(
                        mc.thePlayer, point);
                MovingObjectPosition hit = rayTrace(
                        candidate[0], candidate[1]);
                if (!matchesSelectedFace(position, side, hit)) {
                    continue;
                }
                float yawDifference = Math.abs(
                        MathHelper.wrapAngleTo180_float(
                                candidate[0] - preferredYaw));
                float pitchDifference = Math.abs(
                        candidate[1] - preferredPitch);
                float score = yawDifference * 4.0F + pitchDifference;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private Vec3 pointOnFace(
            BlockPos position, EnumFacing side,
            double first, double second) {
        switch (side) {
            case EAST:
                return new Vec3(position.getX() + 1.0D,
                        position.getY() + first,
                        position.getZ() + second);
            case WEST:
                return new Vec3(position.getX(),
                        position.getY() + first,
                        position.getZ() + second);
            case SOUTH:
                return new Vec3(position.getX() + first,
                        position.getY() + second,
                        position.getZ() + 1.0D);
            case NORTH:
                return new Vec3(position.getX() + first,
                        position.getY() + second,
                        position.getZ());
            case UP:
                return new Vec3(position.getX() + first,
                        position.getY() + 1.0D,
                        position.getZ() + second);
            default:
                return new Vec3(position.getX() + first,
                        position.getY(),
                        position.getZ() + second);
        }
    }

    private float[] rotationsToPoint(EntityPlayerSP player, Vec3 point) {
        Vec3 eye = player.getPositionEyes(1.0F);
        double x = point.xCoord - eye.xCoord;
        double y = point.yCoord - eye.yCoord;
        double z = point.zCoord - eye.zCoord;
        double horizontal = MathHelper.sqrt_double(x * x + z * z);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(z, x)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(y, horizontal))
        };
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = Utils.getLookVec(yaw, pitch);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 end = eye.addVector(
                look.xCoord * reach,
                look.yCoord * reach,
                look.zCoord * reach);
        return mc.theWorld.rayTraceBlocks(
                eye, end, false, false, true);
    }

    static float[] directionToBlock(
            EntityPlayerSP player, BlockPos position, EnumFacing side) {
        double x = position.getX() + 0.5D
                + side.getDirectionVec().getX() * 0.5D;
        double y = position.getY() + 0.5D
                + side.getDirectionVec().getY() * 0.5D;
        double z = position.getZ() + 0.5D
                + side.getDirectionVec().getZ() * 0.5D;
        double xDifference = x - player.posX;
        double zDifference = z - player.posZ;
        double yDifference = y - player.posY
                - (double) player.getEyeHeight();
        double horizontal = MathHelper.sqrt_double(
                xDifference * xDifference + zDifference * zDifference);
        return new float[]{
                (float) Math.toDegrees(Math.atan2(
                        zDifference, xDifference)) - 90.0F,
                (float) -Math.toDegrees(Math.atan2(
                        yDifference, horizontal))
        };
    }

    private boolean isMovementKeyDown() {
        return mc.gameSettings != null
                && (mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown());
    }

    private boolean physicalJumpDown() {
        return mc.gameSettings != null
                && mc.gameSettings.keyBindJump.isKeyDown();
    }

    private float movementYawFromKeys(float cameraYaw) {
        return movementYawFromKeys(
                cameraYaw,
                mc.gameSettings.keyBindForward.isKeyDown(),
                mc.gameSettings.keyBindBack.isKeyDown(),
                mc.gameSettings.keyBindLeft.isKeyDown(),
                mc.gameSettings.keyBindRight.isKeyDown());
    }

    static float movementYawFromKeys(
            float cameraYaw, boolean forwardDown, boolean backDown,
            boolean leftDown, boolean rightDown) {
        float yaw = cameraYaw;
        if (!forwardDown && backDown) {
            yaw += 180.0F;
        }
        float forward = 1.0F;
        if (!forwardDown && backDown) {
            forward = -0.5F;
        }
        else if (forwardDown && !backDown) {
            forward = 0.5F;
        }
        if (leftDown && !rightDown) {
            yaw -= 90.0F * forward;
        }
        if (!leftDown && rightDown) {
            yaw += 90.0F * forward;
        }
        return yaw;
    }

    static float preferredPlacementYaw(float movementYaw, float offset) {
        return movementYaw + 180.0F + offset;
    }

    static boolean shouldAutoJump(
            boolean onGround, boolean moving,
            boolean physicalJumpDown) {
        return onGround && moving && !physicalJumpDown;
    }

    static boolean shouldPulseMidCycle(
            boolean onGround, int offGroundTicks,
            double motionY, boolean alreadyUsed) {
        return shouldPulseMidCycle(
                onGround, offGroundTicks, motionY, alreadyUsed, true);
    }

    static boolean shouldPulseMidCycle(
            boolean onGround, int offGroundTicks,
            double motionY, boolean alreadyUsed, boolean cycleEligible) {
        return cycleEligible && !alreadyUsed && !onGround
                && offGroundTicks > 1
                && !Double.isNaN(motionY)
                && !Double.isInfinite(motionY)
                && motionY <= ScaffoldSneakSafety.PREPARATION_MOTION_Y;
    }

    private static int clampSneakInterval(int interval) {
        return Math.max(1, Math.min(10, interval));
    }

    static float cadenceForAirTicks(int airTicks) {
        return airTicks < 2
                ? FIRST_AIRBORNE_YAW_STEP
                : LATER_AIRBORNE_YAW_STEP;
    }

    static RotationCommand telly2Rotation(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float maximumYawChange, float maximumPitchChange,
            float gcd) {
        float normalizedYaw = normalizeYaw(targetYaw, currentYaw);
        float normalizedPitch = normalizePitch(targetPitch, currentPitch);
        float yaw = smoothRotation(
                currentYaw, normalizedYaw, maximumYawChange);
        float pitch = smoothRotation(
                currentPitch, normalizedPitch, maximumPitchChange);
        float step = Math.abs(gcd);
        if (!Float.isNaN(step) && !Float.isInfinite(step)
                && step > EPSILON) {
            float yawDelta = yaw - currentYaw;
            float pitchDelta = pitch - currentPitch;
            yaw = currentYaw + yawDelta - yawDelta % step;
            pitch = currentPitch + pitchDelta - pitchDelta % step;
        }
        return new RotationCommand(
                yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    }

    private RotationCommand runtimeTelly2Rotation(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch,
            float maximumYawChange, float maximumPitchChange,
            float gcd) {
        float yawTargetJitter = runtimeRotationInitialized
                && currentPitch != lastRuntimePitch
                ? triangularRandom() / 3.0F : 0.0F;
        float pitchTargetJitter = runtimeRotationInitialized
                && currentYaw != lastRuntimeYaw
                ? triangularRandom() / 3.0F : 0.0F;
        float yawLimit = maximumYawChange + triangularRandom() * 3.0F;
        float pitchLimit = maximumPitchChange + triangularRandom() * 3.0F;
        return telly2Rotation(
                currentYaw, currentPitch,
                targetYaw + yawTargetJitter,
                targetPitch + pitchTargetJitter,
                yawLimit, pitchLimit, gcd);
    }

    private static float triangularRandom() {
        return (float) (Math.random() - Math.random());
    }

    private float mouseGcd() {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    private RotationCommand safeguardVulcanPitch(
            float previousPitch, RotationCommand command, float gcd) {
        float pitchDelta = command.pitch - previousPitch;
        float absoluteDelta = Math.abs(pitchDelta);
        int requestedSteps = pitchDeltaSteps(pitchDelta, gcd);
        if (lastDispatchedPitchDeltaSteps == UNKNOWN_PITCH_STEPS
                || requestedSteps == 0
                || absoluteDelta <= VULCAN_PITCH_SAMPLE_MIN) {
            return command;
        }

        if (!isVulcanScaffoldDUnsafe(
                pitchDelta, lastDispatchedPitchDeltaRaw)) {
            return command;
        }

        float step = safeRotationStep(gcd);
        MovingObjectPosition originalHit = rayTrace(
                command.yaw, command.pitch);
        boolean originalOwnsPlacement = canPlaceCurrentHit(originalHit);
        int direction = pitchDelta < 0.0F ? -1 : 1;
        for (int distance = 0; distance <= MAX_COPRIME_SEARCH; distance++) {
            int lower = requestedSteps - distance;
            int upper = requestedSteps + distance;
            int[] candidates = distance == 0
                    ? new int[]{requestedSteps}
                    : new int[]{lower, upper};
            for (int candidateSteps : candidates) {
                if (candidateSteps <= 0) {
                    continue;
                }
                float candidatePitch = MathHelper.clamp_float(
                        previousPitch + direction * candidateSteps * step,
                        -90.0F, 90.0F);
                float candidateDelta = candidatePitch - previousPitch;
                if (Math.abs(candidateDelta) <= VULCAN_PITCH_SAMPLE_MIN
                        || !owner.withinScaffoldPitchBudget(candidatePitch, previousPitch)
                        || isVulcanScaffoldDUnsafe(
                        candidateDelta, lastDispatchedPitchDeltaRaw)) {
                    continue;
                }
                if (originalOwnsPlacement
                        && !canPlaceCurrentHit(rayTrace(
                        command.yaw, candidatePitch))) {
                    continue;
                }
                return new RotationCommand(command.yaw, candidatePitch);
            }
        }

        return command;
    }

    private RotationCommand repairPlacementRotation(
            float previousPitch,
            RotationCommand command, float gcd) {
        if (command == null || blockPos == null || facing == null
                || facing == EnumFacing.UP) {
            return command;
        }
        if (matchesSelectedFace(
                blockPos, facing, rayTrace(command.yaw, command.pitch))) {
            return command;
        }

        float step = safeRotationStep(gcd);
        int maxPitchSteps = repairStepCount(step);
        int pitchDirection = scaffoldPitch >= command.pitch ? 1 : -1;

        for (int distance = 1; distance <= maxPitchSteps; distance++) {
            int first = pitchDirection * distance;
            RotationCommand repaired = tryPlacementRepairCandidate(
                    previousPitch, command, 0, first, step);
            if (repaired != null) {
                return repaired;
            }
            repaired = tryPlacementRepairCandidate(
                    previousPitch, command, 0, -first, step);
            if (repaired != null) {
                return repaired;
            }
        }

        int yawSteps = Math.min(MAX_YAW_REPAIR_STEPS, maxPitchSteps);
        int yawDirection = MathHelper.wrapAngleTo180_float(
                scaffoldYaw - command.yaw) >= 0.0F ? 1 : -1;
        for (int yawDistance = 1; yawDistance <= yawSteps; yawDistance++) {
            int firstYaw = yawDirection * yawDistance;
            for (int pitchDistance = 0;
                 pitchDistance <= Math.min(maxPitchSteps, 8);
                 pitchDistance++) {
                int firstPitch = pitchDirection * pitchDistance;
                RotationCommand repaired = tryPlacementRepairCandidate(
                        previousPitch, command,
                        firstYaw, firstPitch, step);
                if (repaired != null) {
                    return repaired;
                }
                if (pitchDistance != 0) {
                    repaired = tryPlacementRepairCandidate(
                            previousPitch, command,
                            firstYaw, -firstPitch, step);
                    if (repaired != null) {
                        return repaired;
                    }
                }
            }
            RotationCommand repaired = tryPlacementRepairCandidate(
                    previousPitch, command, -firstYaw, 0, step);
            if (repaired != null) {
                return repaired;
            }
        }
        return command;
    }

    private RotationCommand tryPlacementRepairCandidate(
            float previousPitch, RotationCommand command,
            int yawSteps, int pitchSteps, float step) {
        float candidateYaw = command.yaw + yawSteps * step;
        float candidatePitch = MathHelper.clamp_float(
                command.pitch + pitchSteps * step, -90.0F, 90.0F);
        float pitchDelta = candidatePitch - previousPitch;
        if (Math.abs(pitchSteps * step) > MAX_PLACEMENT_REPAIR_DEGREES
                || !owner.withinScaffoldPitchBudget(candidatePitch, previousPitch)
                || isVulcanScaffoldDUnsafe(
                pitchDelta, lastDispatchedPitchDeltaRaw)) {
            return null;
        }
        if (!matchesSelectedFace(
                blockPos, facing, rayTrace(candidateYaw, candidatePitch))) {
            return null;
        }
        return new RotationCommand(candidateYaw, candidatePitch);
    }

    private static int repairStepCount(float step) {
        if (Float.isNaN(step) || Float.isInfinite(step)
                || step <= EPSILON) {
            return 24;
        }
        return Math.min(64, Math.max(2,
                (int) Math.ceil(MAX_PLACEMENT_REPAIR_DEGREES / step)));
    }

    static long expandedPitchDelta(float delta) {
        if (Float.isNaN(delta) || Float.isInfinite(delta)) {
            return 0L;
        }
        return (long) (Math.abs(delta) * (double) VULCAN_PITCH_EXPANDER);
    }

    static long vulcanScaffoldGcd(long current, long previous) {
        long left = Math.abs(current);
        long right = Math.abs(previous);
        if (left == 0L || right == 0L) {
            return left;
        }
        while (right > 16384L) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    static boolean isVulcanScaffoldDUnsafe(
            float currentDelta, long previousRawDelta) {
        if (Float.isNaN(currentDelta) || Float.isInfinite(currentDelta)
                || Math.abs(currentDelta) <= VULCAN_PITCH_SAMPLE_MIN
                || previousRawDelta <= 0L) {
            return false;
        }
        long currentRawDelta = expandedPitchDelta(currentDelta);
        if (currentRawDelta <= 0L) {
            return false;
        }
        long gcd = vulcanScaffoldGcd(currentRawDelta, previousRawDelta);
        return gcd > 0L && gcd < VULCAN_SCAFFOLD_D_MIN_GCD;
    }

    static int nearestCoprimePitchSteps(
            int requestedSteps, int previousSteps) {
        int requested = Math.abs(requestedSteps);
        if (requested == 0 || previousSteps == UNKNOWN_PITCH_STEPS) {
            return requested;
        }
        int previous = Math.abs(previousSteps);
        if (previous == 0) {
            return 1;
        }
        if (greatestCommonDivisor(requested, previous) == 1) {
            return requested;
        }
        for (int distance = 1; distance <= MAX_COPRIME_SEARCH; distance++) {
            int lower = requested - distance;
            if (lower > 0
                    && greatestCommonDivisor(lower, previous) == 1) {
                return lower;
            }
            int upper = requested + distance;
            if (greatestCommonDivisor(upper, previous) == 1) {
                return upper;
            }
        }
        return requested;
    }

    static int greatestCommonDivisor(int first, int second) {
        int left = Math.abs(first);
        int right = Math.abs(second);
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private static int pitchDeltaSteps(float delta, float gcd) {
        float step = safeRotationStep(gcd);
        return Math.max(0, Math.round(Math.abs(delta) / step));
    }

    private static float safeRotationStep(float gcd) {
        float step = Math.abs(gcd);
        return !Float.isNaN(step) && !Float.isInfinite(step)
                && step > EPSILON ? step : 0.15F;
    }

    static boolean canPlaceLiveHit(
            BlockPos selectedPosition, EnumFacing selectedFacing,
            MovingObjectPosition hit) {
        return selectedPosition != null && selectedFacing != null
                && hit != null
                && hit.typeOfHit
                == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.getBlockPos() != null
                && hit.getBlockPos().equals(selectedPosition)
                && (hit.sideHit != EnumFacing.UP
                || selectedFacing == EnumFacing.UP);
    }

    private boolean canPlaceCurrentHit(MovingObjectPosition hit) {
        return matchesSelectedFace(blockPos, facing, hit);
    }

    static boolean matchesSelectedFace(
            BlockPos selectedPosition, EnumFacing selectedFacing,
            MovingObjectPosition hit) {
        return canPlaceLiveHit(selectedPosition, selectedFacing, hit)
                && hit.sideHit == selectedFacing;
    }

    private static float smoothRotation(
            float current, float target, float maximumChange) {
        float difference = MathHelper.wrapAngleTo180_float(target - current);
        difference = Math.max(-maximumChange,
                Math.min(maximumChange, difference));
        return current + difference;
    }

    private static float normalizeYaw(float target, float previous) {
        while (target - previous < -180.0F) {
            target += 360.0F;
        }
        while (target - previous >= 180.0F) {
            target -= 360.0F;
        }
        return target;
    }

    private static float normalizePitch(float target, float previous) {
        while (target - previous < -180.0F) {
            target += 360.0F;
        }
        while (target - previous >= 180.0F) {
            target -= 360.0F;
        }
        return target;
    }

    private static double squaredDistance(
            double x1, double y1, double z1,
            double x2, double y2, double z2) {
        double x = x2 - x1;
        double y = y2 - y1;
        double z = z2 - z1;
        return x * x + y * y + z * z;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class RotationCommand {
        final float yaw;
        final float pitch;

        RotationCommand(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

}
