package idle.miau.module.impl.player;

import idle.miau.event.EventMovementInput;
import idle.miau.event.EventSilentRotation;
import idle.miau.event.JumpEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.helper.SilentRotationManager;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ScaffoldTellyMode {
    static final float MAX_YAW_STEP = 95.0F;
    static final float MAX_PITCH_STEP = 90.0F;
    static final int MAX_PREDICTION_TICKS = 12;
    static final int PACKET_TIMEOUT_TICKS = 20;

    private static final double REACH = 4.5D;
    private static final double SUPPORT_EPSILON = 1.0E-4D;
    private static final double LOCAL_POSE_TOLERANCE = 0.40D;
    private static final double MIN_SAFE_LANDING_OVERLAP = 0.20D;
    private static final double DIAGONAL_COMPONENT_MINIMUM = 0.35D;
    private static final double BASE_JUMP_MOTION = 0.42F;
    private static final float MAX_PLACEMENT_PITCH = 84.5F;
    private static final float RETURN_PITCH_STEP = 45.0F;
    private static final float ANGLE_AXIS = 45.0F;
    private static final float ANGLE_AXIS_TOLERANCE = 0.08F;
    private static final int MIN_AIRBORNE_CLICK_TICKS = 1;
    private static final int INTAVE_ZERO_INPUT_SAMPLES = 1;
    private static final int INTAVE_SAFETY_SAMPLES = 1;
    private static final int DIAGONAL_SPRINT_SAFETY_SAMPLES = 1;
    private static final double SHARED_LOOK_MISS_PENALTY = 500.0D;
    private static final int MAX_FRONTIER_WALK = 6;
    private static final int MAX_ROUTE_STEPS = 16;
    private static final double LANE_DIRECTION_DOT_MINIMUM = 0.984807753D;
    private static final double TOWER_CLEARANCE_EPSILON = 1.0E-3D;
    private static final double[] FACE_SAMPLES = {
            0.18D, 0.34D, 0.50D, 0.66D, 0.82D
    };

    private final Scaffold owner;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final SilentRotationManager rotations =
            SilentRotationManager.getInstance();
    private final JumpLatch jumpLatch = new JumpLatch();

    private Phase phase = Phase.GROUND_READY;
    private long clientTick;
    private long dispatchOrder;
    private long movementDispatchSequence;
    private long armedTick = Long.MIN_VALUE;
    private long returnStartedSequence = Long.MAX_VALUE;
    private long lastClickTick = Long.MIN_VALUE;
    private int supportY = Integer.MIN_VALUE;
    private int airborneTicks;
    private int intaveZeroInputSamples;
    private boolean intaveBridgeCycle;

    private ReportedPose reportedPose;
    private EnvelopePhase envelopePhase = EnvelopePhase.EMPTY;
    private InputSample inputSample;
    private MovementFrame movementFrame;
    private RotationCommand pendingRotation;
    private TargetKey target;
    private PreparedPlacement prepared;
    private PlacementAttempt attempt;
    private PostPlaceHold hold;
    private CycleDraft cycleDraft;
    private DdaLane bridgeLane;
    private boolean insideControllerCall;
    private boolean safetySneak;
    private boolean midCycleSneakActive;
    private boolean midCycleSneakUsed;
    private boolean midCycleSneakAirborne;
    private boolean midCycleSneakEligible;
    private int midCycleSneakCyclesUntilNext;
    private int midCycleSneakInterval = 1;

    private float takeoffYaw;
    private float returnYaw;
    private float returnPitch;
    private float preferredPlacementYaw;
    private float groundViewYaw;
    private float groundViewPitch;
    private float cycleRawForward;
    private float cycleRawStrafe;
    private double cycleDirectionX;
    private double cycleDirectionZ;
    private double cycleOriginX;
    private double cycleOriginZ;
    private BlockPos frontier;
    private BlockPos diagonalRouteEnd;
    private boolean returnRotationCaptured;
    private boolean returnLookPlanned;
    private boolean placementDispatchedThisCycle;
    private boolean lanePlannedThisCycle;
    private boolean diagonalSprintAllowed;
    private final Set<BlockPos> optimisticPlacements =
            new HashSet<BlockPos>();

    private float plannedYaw;
    private float plannedPitch;
    private boolean plannedRotationAvailable;
    private float frozenYaw;
    private float frozenPitch;
    private boolean frozenRotationAvailable;

    ScaffoldTellyMode(Scaffold owner) {
        this.owner = owner;
    }

    void reset() {
        EntityPlayerSP player = owner.getPlayerForTelly();
        phase = Phase.GROUND_READY;
        clientTick = 0L;
        dispatchOrder = 0L;
        movementDispatchSequence = 0L;
        armedTick = Long.MIN_VALUE;
        returnStartedSequence = Long.MAX_VALUE;
        lastClickTick = Long.MIN_VALUE;
        supportY = Integer.MIN_VALUE;
        airborneTicks = 0;
        intaveZeroInputSamples = 0;
        intaveBridgeCycle = false;
        reportedPose = null;
        envelopePhase = EnvelopePhase.EMPTY;
        inputSample = null;
        movementFrame = null;
        pendingRotation = null;
        target = null;
        prepared = null;
        attempt = null;
        hold = null;
        cycleDraft = null;
        bridgeLane = null;
        insideControllerCall = false;
        safetySneak = false;
        midCycleSneakActive = false;
        midCycleSneakUsed = false;
        midCycleSneakAirborne = player != null && !player.onGround;
        midCycleSneakEligible = false;
        midCycleSneakCyclesUntilNext = 0;
        midCycleSneakInterval = 1;
        takeoffYaw = 0.0F;
        returnYaw = 0.0F;
        returnPitch = 0.0F;
        preferredPlacementYaw = 0.0F;
        groundViewYaw = 0.0F;
        groundViewPitch = 0.0F;
        cycleRawForward = 0.0F;
        cycleRawStrafe = 0.0F;
        cycleDirectionX = 0.0D;
        cycleDirectionZ = 0.0D;
        cycleOriginX = 0.0D;
        cycleOriginZ = 0.0D;
        frontier = null;
        returnRotationCaptured = false;
        returnLookPlanned = false;
        placementDispatchedThisCycle = false;
        lanePlannedThisCycle = false;
        diagonalSprintAllowed = false;
        diagonalRouteEnd = null;
        optimisticPlacements.clear();
        plannedYaw = 0.0F;
        plannedPitch = 0.0F;
        plannedRotationAvailable = false;
        frozenYaw = 0.0F;
        frozenPitch = 0.0F;
        frozenRotationAvailable = false;
        jumpLatch.reset();
        owner.setTellySneak(false);
    }

    void onPlannedRotation(float yaw, float pitch, boolean modified) {
        plannedYaw = yaw;
        plannedPitch = pitch;
        plannedRotationAvailable = pendingRotation != null
                || modified || rotations.isModified();
    }

    boolean hasPlannedRotation() {
        return plannedRotationAvailable;
    }

    float getPlannedYaw() {
        return plannedYaw;
    }

    boolean shouldSneak() {
        return safetySneak;
    }

    void onPrePlayerInput(
            PrePlayerInputEvent event, boolean midCycleEnabled) {
        onPrePlayerInput(event, midCycleEnabled, 1);
    }

    void onPrePlayerInput(
            PrePlayerInputEvent event, boolean midCycleEnabled,
            int everyNCycles) {
        midCycleSneakActive = owner.isHoldingTellySneakRelease();
        if (event == null || !validRuntimeContext()) {
            return;
        }

        EntityPlayerSP player = owner.getPlayerForTelly();
        int interval = clampSneakInterval(everyNCycles);
        if (midCycleSneakInterval != interval) {
            midCycleSneakInterval = interval;
            midCycleSneakCyclesUntilNext = 0;
            midCycleSneakEligible = false;
        }
        if (!midCycleEnabled) {
            midCycleSneakAirborne = !player.onGround;
            midCycleSneakEligible = false;
            midCycleSneakCyclesUntilNext = 0;
            midCycleSneakUsed = false;
            return;
        }
        if (player.onGround) {
            midCycleSneakUsed = false;
            midCycleSneakAirborne = false;
            midCycleSneakEligible = false;
            return;
        }
        if (owner.isHoldingTellySneakRelease()) return;
        if (!midCycleSneakAirborne) {
            midCycleSneakAirborne = true;
            if (midCycleSneakCyclesUntilNext > 0) {
                midCycleSneakCyclesUntilNext--;
            }
            midCycleSneakEligible = midCycleSneakCyclesUntilNext == 0;
        }
        if (!shouldPulseMidCycle(
                phase, player.onGround, airborneTicks,
                player.motionY, midCycleSneakUsed,
                midCycleSneakEligible)) {
            return;
        }

        if (!event.isSneak()) {
            midCycleSneakActive = owner.requestTellySneakPulse(event, "Telly/Mid cycle");
        }
        if (event.isSneak()) {
            midCycleSneakUsed = true;
            midCycleSneakEligible = false;
            midCycleSneakCyclesUntilNext = interval;
        }
    }

    void updateRotation(EventSilentRotation event) {
        clientTick++;
        pendingRotation = null;
        plannedRotationAvailable = false;
        frozenRotationAvailable = false;

        if (!validRuntimeContext()) {
            reset();
            return;
        }

        EntityPlayerSP player = owner.getPlayerForTelly();
        if (phase == Phase.GROUND_READY) {
            groundViewYaw = event.getYaw();
            groundViewPitch = MathHelper.clamp_float(
                    event.getPitch(), -90.0F, 90.0F);
        }
        if (owner.getCurrentTellyBlock() == null) {
            if (phase != Phase.GROUND_READY) {
                abortCycle();
                requestReturnRotation(event, player);
            }
            owner.finishTellyPlacement();
            return;
        }
        if (hold != null && !isProtocolTargetValid(
                hold.prepared.target)) {
            abortCycle();
            if (phase == Phase.RETURNING) {
                requestReturnRotation(event, player);
            }
            owner.finishTellyPlacement();
            return;
        }
        if (protocolTimedOut(clientTick)) {
            abortCycle();
        }

        advancePhase(player);
        if (hold != null) {
            requestRotation(event, RotationCommand.hold(
                    hold.prepared.yaw, hold.prepared.pitch));
            owner.finishTellyPlacement();
            return;
        }

        if (phase == Phase.RETURNING) {
            requestReturnRotation(event, player);
        }
        else if (phase == Phase.TRAVELING || phase == Phase.STOPPING) {
            requestRotation(event, RotationCommand.hold(
                    cycleDraft == null ? player.rotationYaw
                            : cycleDraft.takeoffYaw,
                    cycleDraft == null ? player.rotationPitch
                            : cycleDraft.returnPitch));
        }
        else if (phase == Phase.EXTENDING) {
            updatePlacementTarget(player);
            if (target != null) {
                if (preparedTargetOwnsWindow(prepared, target)) {
                    requestRotation(event, RotationCommand.hold(
                            prepared.yaw, prepared.pitch));
                }
                else {
                    AimSolution desired = solveAim(
                            target,
                            predictedPacketOrigin(player),
                            predictedPacketOrigin(player, 2),
                            authorityYaw(player),
                            authorityPitch(player),
                            preferredPlacementYaw);
                    if (desired != null) {
                        RotationCommand command = stagedRotation(
                                authorityYaw(player), authorityPitch(player),
                                desired.yaw, desired.pitch,
                                sensitivityStep());
                        requestRotation(event, command);
                    }
                    else {
                        holdReportedRotation(event, player);
                    }
                }
            }
            else {
                if (shouldBeginReturn(
                        placementDispatchedThisCycle,
                        false, hasConservativeLandingRunway(player))) {
                    beginReturn();
                    requestReturnRotation(event, player);
                }
                else {
                    holdReportedRotation(event, player);
                }
            }
        }
        else if (phase == Phase.TAKEOFF) {
            requestRotation(event, RotationCommand.hold(
                    returnYaw, returnPitch));
        }
        owner.finishTellyPlacement();
    }

    void applyMovementInput(EventMovementInput event) {
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (player == null || event == null) {
            return;
        }
        boolean groundRearmWindow = canRearmFromReturn(player);
        boolean active = phase != Phase.GROUND_READY && !groundRearmWindow;
        if (active && plannedRotationAvailable) {
            freezeFinalRotation(player);
        }
        float cameraYaw = active && cycleDraft != null
                ? cycleDraft.travel.cameraYaw : player.rotationYaw;
        float cameraPitch = active && cycleDraft != null
                ? cycleDraft.returnPitch : player.rotationPitch;
        float rawForward = active && cycleDraft != null
                ? cycleDraft.travel.rawForward
                : rawForwardIntent(event.getSpeed());
        float rawStrafe = active && cycleDraft != null
                ? cycleDraft.travel.rawStrafe
                : rawStrafeIntent(event.getStrafe());
        if (active && isTowerCycle() || owner.isPreparingTellySneak()) {
            rawForward = 0.0F;
            rawStrafe = 0.0F;
        }
        float movementYaw = frozenRotationAvailable
                ? frozenYaw : cameraYaw;
        boolean userSneaking = mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindSneak);
        inputSample = new InputSample(
                cameraYaw, cameraPitch, movementYaw,
                rawForward, rawStrafe,
                userSneaking || midCycleSneakActive,
                player.isSprinting());
        if (!active && safetySneak && !userSneaking) {
            event.setSpeed(rawForward);
            event.setStrafe(rawStrafe);
        }
        if (active && frozenRotationAvailable) {
            SilentRotationManager.ResolvedInput corrected =
                    SilentRotationManager.resolveMovementInput(
                            cameraYaw, movementYaw,
                            rawForward, rawStrafe,
                            userSneaking || midCycleSneakActive);
            event.setSpeed(corrected.forward);
            event.setStrafe(corrected.strafe);
        }
        if (owner.isPreparingTellySneak()) {
            event.setSpeed(0.0F);
            event.setStrafe(0.0F);
        }
        if (isIntaveInputReleasePhase()) {
            releaseMovementInput(event);
        }
    }

    void onFinalizedMovementInput(EventMovementInput event) {
        if (!validRuntimeContext()) {
            reset();
            return;
        }
        if (event == null) {
            return;
        }
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (owner.isPreparingTellySneak()) {
            event.setSpeed(0.0F);
            event.setStrafe(0.0F);
        }
        InputSample sample = inputSample != null
                ? inputSample
                : new InputSample(
                player.rotationYaw, player.rotationPitch,
                currentMovementYaw(player),
                event.getSpeed(), event.getStrafe(),
                player.movementInput != null && player.movementInput.sneak,
                player.isSprinting());
        inputSample = null;
        float forward = event.isCanceled() ? 0.0F : event.getSpeed();
        float strafe = event.isCanceled() ? 0.0F : event.getStrafe();
        movementFrame = new MovementFrame(
                sample.cameraYaw, sample.movementYaw,
                sample.rawForward, sample.rawStrafe,
                forward, strafe, sample.sneaking, sample.sprinting);

        if (isIntaveInputReleasePhase()) {
            releaseMovementInput(event);
            movementFrame = new MovementFrame(
                    sample.cameraYaw, sample.movementYaw,
                    sample.rawForward, sample.rawStrafe,
                    0.0F, 0.0F, sample.sneaking, false);
            intaveZeroInputSamples++;
            player.setSprinting(false);
        }

        midCycleSneakActive = false;

        boolean rearmFromReturn = canRearmFromReturn(player);

        if (phase == Phase.EXTENDING
                && prepared != null && target != null
                && prepared.target.equals(target)
                && attempt == null && hold == null
                && shouldDispatchPrepared(player)) {
            placePrepared(player);
        }

        if (phase != Phase.GROUND_READY && !rearmFromReturn && player.onGround
                && movementFrame.hasMovement()) {
            BlockPos activeSupport = findSupportUnderPlayer(player);
            if (activeSupport != null
                    && willLeaveSupport(player, movementFrame)) {
                engageSafetySneak(event, sample, player);
            }
            else {
                releaseSafetySneak(player);
            }
            return;
        }

        if (phase != Phase.GROUND_READY && !rearmFromReturn) {
            releaseSafetySneak(player);
            return;
        }

        boolean canStartCycle = canArmCycle(phase) || rearmFromReturn;
        if (!canStartCycle || !player.onGround
                || attempt != null || hold != null) {
            return;
        }
        BlockPos support = findSupportUnderPlayer(player);
        boolean hasMovement = movementFrame.hasMovement();
        boolean hasBlock = owner.getCurrentTellyBlock() != null;
        boolean supported = support != null;
        boolean leavesSupport = hasMovement && supported
                && willLeaveSupport(player, movementFrame);
        boolean sneaking = movementFrame.sneaking;
        boolean physicalMovementIntent = hasMovementIntent(
                sample.rawForward, sample.rawStrafe);
        boolean towerRequested = shouldRequestTower(
                physicalJumpDown() || event.isJumping(), physicalMovementIntent,
                supported, hasBlock, sneaking);

        if (player.onGround && canStartCycle && hasMovement
                && leavesSupport && supported && !hasBlock && !sneaking) {
            engageSafetySneak(event, sample, player);
            return;
        }
        if (!towerRequested && !shouldArmJump(player.onGround, canStartCycle,
                hasMovement, leavesSupport, supported, hasBlock)
                || sneaking) {
            releaseSafetySneak(player);
            return;
        }

        if (rearmFromReturn) {
            finishCycle();
        }

        captureCycle(player, sample, support,
                towerRequested ? CycleKind.TOWER : CycleKind.BRIDGE);
        target = towerRequested ? towerTarget(support) : null;
        if (!towerRequested && !hasBridgeExtensionCandidate()) {
            clearCycleDraft();
            engageSafetySneak(event, sample, player);
            return;
        }
        if (!towerRequested && isDiagonalDirection(
                cycleDirectionX, cycleDirectionZ)
                && !prepareDiagonalTakeoff(player)) {
            clearCycleDraft();
            engageSafetySneak(event, sample, player);
            return;
        }
        if (target == null) {
            envelopePhase = EnvelopePhase.EMPTY;
        }
        else {
            envelopePhase = EnvelopePhase.AIMING;
        }
        armedTick = clientTick;
        prepared = null;
        placementDispatchedThisCycle = false;
        phase = armedPhase(towerRequested
                ? CycleKind.TOWER : CycleKind.BRIDGE);
        if (towerRequested) {
            suppressTowerHorizontalInput(event, sample);
        }
        else {
            applyBridgeTakeoffInput(event, sample);
        }
        releaseSafetySneak(player);
        if (!jumpLatch.request()) {
            abortCycle();
        }
    }

    boolean consumeJump(boolean userJump) {
        if (cycleDraft != null && cycleDraft.kind == CycleKind.TOWER) {
            return jumpLatch.consume(false);
        }
        return jumpLatch.consume(userJump);
    }

    void onFinalSprintState(boolean sprinting) {
        if (cycleDraft == null) {
            return;
        }
        cycleDraft = cycleDraft.withSprinting(sprinting);
        if (movementFrame != null) {
            movementFrame = new MovementFrame(
                    movementFrame.cameraYaw, movementFrame.movementYaw,
                    movementFrame.rawForward, movementFrame.rawStrafe,
                    movementFrame.forward, movementFrame.strafe,
                    movementFrame.sneaking, sprinting);
        }
    }

    boolean canSprintWithFrame() {
        return sprintEligible(
                movementFrame, safetySneak,
                cycleDraft == null || diagonalSprintAllowed);
    }

    void applyStrafe(StrafeEvent event) {
        if (event == null) {
            return;
        }
        if (phase == Phase.TAKEOFF && cycleDraft != null) {
            event.setYaw(cycleDraft.takeoffYaw);
            return;
        }
        if ((phase == Phase.TRAVELING || phase == Phase.STOPPING)
                && cycleDraft != null) {
            event.setYaw(cycleDraft.takeoffYaw);
            return;
        }
        if (frozenRotationAvailable) {
            event.setYaw(frozenYaw);
            return;
        }
        rotations.applyStrafe(event, currentMovementYaw(
                owner.getPlayerForTelly()));
    }

    void applyJump(JumpEvent event) {
        if (event == null) {
            return;
        }
        if (phase == Phase.TAKEOFF && cycleDraft != null) {
            event.setYaw(cycleDraft.takeoffYaw);
            if (cycleDraft.kind == CycleKind.BRIDGE
                    && isDiagonalDirection(
                    cycleDraft.directionX, cycleDraft.directionZ)
                    && !diagonalSprintAllowed) {
                event.setSprint(false);
            }
            return;
        }
        if ((phase == Phase.TRAVELING || phase == Phase.STOPPING)
                && cycleDraft != null) {
            event.setYaw(cycleDraft.takeoffYaw);
            return;
        }
        if (frozenRotationAvailable) {
            event.setYaw(frozenYaw);
            return;
        }
        rotations.applyJump(event, currentMovementYaw(
                owner.getPlayerForTelly()));
    }

    void onPreMotion(PreMotionEvent event) {
        if (!validRuntimeContext()) {
            reset();
            return;
        }
        if (event == null) {
            return;
        }
        if ((phase == Phase.TAKEOFF || phase == Phase.TRAVELING
                || phase == Phase.STOPPING) && cycleDraft != null) {
            event.setRotations(cycleDraft.takeoffYaw, cycleDraft.returnPitch);
            plannedYaw = cycleDraft.takeoffYaw;
            plannedPitch = cycleDraft.returnPitch;
            plannedRotationAvailable = true;
            return;
        }
        if (!frozenRotationAvailable) {
            return;
        }
        event.setRotations(frozenYaw, frozenPitch);
        plannedYaw = frozenYaw;
        plannedPitch = frozenPitch;
        plannedRotationAvailable = true;
    }

    void onPacketSent(SendPacketEvent event) {
        if (event == null || !(event.getPacket()
                instanceof C08PacketPlayerBlockPlacement)
                || attempt == null || attempt.packet != null
                || envelopePhase != EnvelopePhase.PREPARED
                || !insideControllerCall) {
            return;
        }
        C08PacketPlayerBlockPlacement packet =
                (C08PacketPlayerBlockPlacement) event.getPacket();
        if (!matchesPacket(packet, attempt.prepared)) {
            return;
        }
        attempt = attempt.bind(packet);
        hold = PostPlaceHold.awaiting(attempt.prepared, packet, clientTick);
        envelopePhase = EnvelopePhase.WAITING_C08;
    }

    void onPacketDispatched(Packet<?> packet) {
        dispatchOrder++;
        if (packet instanceof C0BPacketEntityAction) {
            updateReportedSneak((C0BPacketEntityAction) packet);
            return;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            onPlacementDispatched((C08PacketPlayerBlockPlacement) packet);
            return;
        }
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }

        movementDispatchSequence++;
        reportedPose = ReportedPose.advance(
                reportedPose, (C03PacketPlayer) packet,
                movementDispatchSequence);
        if (reportedPose.hasRotation) {
            rotations.synchronizeEmitted(
                    reportedPose.yaw, reportedPose.pitch);
        }

        if (hold != null) {
            if (!hold.isCompatible(reportedPose, sensitivityStep())) {
                abortCycle();
                return;
            }
            if (hold.c08Dispatched
                    && dispatchOrder > hold.c08DispatchOrder) {
                releaseProtocolHold();
                if (phase == Phase.EXTENDING && target != null
                        && envelopePhase == EnvelopePhase.AIMING) {
                    prepareFromReportedPose();
                }
            }
            return;
        }

        if (phase == Phase.RETURNING) {
            if (returnLookPlanned
                    && movementDispatchSequence > returnStartedSequence
                    && rotationMatches(reportedPose, returnYaw, returnPitch,
                    sensitivityStep())) {
                finishCycle();
            }
            return;
        }
        if (phase == Phase.TAKEOFF || phase == Phase.EXTENDING) {
            prepareFromReportedPose();
        }
    }

    void onPacketReceived(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            reset();
            return;
        }
        if (packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange change = (S23PacketBlockChange) packet;
            reconcileServerBlock(
                    change.getBlockPosition(), change.getBlockState());
            return;
        }
        if (packet instanceof S22PacketMultiBlockChange) {
            for (S22PacketMultiBlockChange.BlockUpdateData change
                    : ((S22PacketMultiBlockChange) packet).getChangedBlocks()) {
                reconcileServerBlock(change.getPos(), change.getBlockState());
            }
        }
    }

    private void onPlacementDispatched(C08PacketPlayerBlockPlacement packet) {
        if (hold == null || hold.packet != packet
                || envelopePhase != EnvelopePhase.WAITING_C08) {
            return;
        }
        if (!hold.isCompatible(reportedPose, sensitivityStep())
                || !isProtocolTargetValid(hold.prepared.target)) {
            abortCycle();
            return;
        }
        hold = hold.dispatched(dispatchOrder);
        acceptDispatchedTarget(hold.prepared.target);
        optimisticPlacements.add(hold.prepared.target.destination);
        placementDispatchedThisCycle = true;
        envelopePhase = EnvelopePhase.POST_C08_HOLD;
    }

    private void acceptDispatchedTarget(TargetKey placed) {
        if (placed == null) {
            return;
        }
        if (!isTowerCycle() && bridgeLane != null) {
            bridgeLane.advance(placed.destination);
        }
        frontier = placed.destination;
    }

    private void reconcileServerBlock(BlockPos position, IBlockState state) {
        if (position == null || !optimisticPlacements.remove(position)) {
            return;
        }
        if (state != null && state.getBlock() != Blocks.air
                && state.getBlock().isFullCube()) {
            return;
        }
        optimisticPlacements.clear();
        bridgeLane = null;
        frontier = null;
        lanePlannedThisCycle = false;
        if (phase != Phase.GROUND_READY && phase != Phase.RETURNING) {
            abortCycle();
        }
    }

    private void updateReportedSneak(C0BPacketEntityAction packet) {
        if (packet.getAction() == C0BPacketEntityAction.Action.START_SNEAKING) {
            reportedPose = ReportedPose.withSneaking(reportedPose, true);
        }
        else if (packet.getAction()
                == C0BPacketEntityAction.Action.STOP_SNEAKING) {
            reportedPose = ReportedPose.withSneaking(reportedPose, false);
        }
    }

    private void prepareFromReportedPose() {
        if (reportedPose == null || !reportedPose.hasPosition
                || !reportedPose.hasRotation || target == null
                || attempt != null || hold != null
                || envelopePhase != EnvelopePhase.AIMING
                && envelopePhase != EnvelopePhase.PREPARED
                || !isTargetStructurallyValid(target)) {
            prepared = null;
            return;
        }
        MovingObjectPosition hit = rayTraceForTarget(
                reportedPose.origin(), reportedPose.yaw, reportedPose.pitch,
                target);
        if (!matchesTarget(hit, target)
                || !isUsablePitch(reportedPose.pitch)) {
            prepared = null;
            envelopePhase = EnvelopePhase.AIMING;
            return;
        }
        prepared = new PreparedPlacement(
                target, clampHitVector(hit.hitVec, target.support),
                reportedPose.yaw, reportedPose.pitch,
                reportedPose,
                movementDispatchSequence);
        envelopePhase = EnvelopePhase.PREPARED;
    }

    private void placePrepared(EntityPlayerSP player) {
        PreparedPlacement placement = prepared;
        if (!placementReady(
                placement != null,
                target != null && placement != null
                        && placement.target.equals(target),
                reportedPose != null && placement != null
                        && placement.sourceMovementSequence
                        == reportedPose.movementSequence,
                envelopePhase == EnvelopePhase.PREPARED
                        && placement != null && localPoseNearSource(
                        player, placement.sourcePose),
                isTargetStructurallyValid(target))) {
            prepared = null;
            return;
        }

        attempt = PlacementAttempt.armed(placement, clientTick);
        insideControllerCall = true;
        boolean placed;
        try {
            placed = owner.placeTellyBlock(
                    new Scaffold.BlockData(
                            placement.target.support,
                            placement.target.face),
                    placement.asHit());
        }
        finally {
            insideControllerCall = false;
        }

        if (hold == null || attempt == null || attempt.packet == null) {
            attempt = null;
            prepared = null;
            envelopePhase = EnvelopePhase.AIMING;
            return;
        }
        lastClickTick = clientTick;
        target = null;
        prepared = null;
        if (!placed) {
            envelopePhase = hold.c08Dispatched
                    ? EnvelopePhase.POST_C08_HOLD
                    : EnvelopePhase.WAITING_C08;
        }
    }

    private void releaseProtocolHold() {
        hold = null;
        attempt = null;
        prepared = null;
        target = null;
        envelopePhase = EnvelopePhase.EMPTY;
        if (!placementDispatchedThisCycle) {
            abortCycle();
            return;
        }
        EntityPlayerSP player = owner.getPlayerForTelly();
        if (isTowerCycle() || player == null || player.onGround
                || hasConservativeLandingRunway(player)) {
            beginReturn();
        }
        else {
            phase = Phase.EXTENDING;
            target = selectNextFrontierTarget(frontier, player);
            envelopePhase = target == null
                    ? EnvelopePhase.EMPTY : EnvelopePhase.AIMING;
        }
    }

    private void advancePhase(EntityPlayerSP player) {
        if (phase == Phase.TAKEOFF) {
            if (!player.onGround) {
                airborneTicks = 1;
                if (intaveBridgeCycle) {
                    phase = nextIntavePhase(
                            phase, true, intaveZeroInputSamples);
                    envelopePhase = EnvelopePhase.EMPTY;
                }
                else {
                    phase = Phase.EXTENDING;
                    envelopePhase = target == null
                            ? EnvelopePhase.EMPTY : EnvelopePhase.AIMING;
                    return;
                }
            }
            else if (clientTick - armedTick > 2L) {
                abortCycle();
                return;
            }
        }
        if (phase == Phase.TRAVELING) {
            if (player.onGround) {
                beginReturn();
                return;
            }
            if (!canContinueIntaveTravel(player)) {
                beginIntaveStop();
            }
            return;
        }
        if (phase == Phase.STOPPING) {
            airborneTicks++;
            if (player.onGround) {
                beginReturn();
                return;
            }
            Phase next = nextIntavePhase(
                    phase, true, intaveZeroInputSamples);
            if (next == Phase.EXTENDING) {
                phase = next;
                lanePlannedThisCycle = false;
                target = null;
                prepared = null;
                envelopePhase = EnvelopePhase.EMPTY;
            }
        }
        else if (phase == Phase.EXTENDING) {
            airborneTicks++;
        }
        if ((phase == Phase.STOPPING || phase == Phase.EXTENDING)
                && player.onGround
                && attempt == null && hold == null) {
            beginReturn();
            return;
        }
        if (isAirbornePhase(phase)
                && supportY != Integer.MIN_VALUE
                && player.posY < supportY - 1.0D) {
            abortCycle();
        }
    }

    private void beginIntaveStop() {
        phase = Phase.STOPPING;
        intaveZeroInputSamples = 0;
        target = null;
        prepared = null;
        attempt = null;
        hold = null;
        envelopePhase = EnvelopePhase.EMPTY;
        lanePlannedThisCycle = false;
    }

    private void abortCycle() {
        attempt = null;
        hold = null;
        prepared = null;
        target = null;
        envelopePhase = EnvelopePhase.EMPTY;
        insideControllerCall = false;
        jumpLatch.reset();
        beginReturn();
    }

    private void beginReturn() {
        target = null;
        prepared = null;
        envelopePhase = EnvelopePhase.EMPTY;
        if (returnRotationCaptured) {
            phase = Phase.RETURNING;
            returnStartedSequence = movementDispatchSequence;
            returnLookPlanned = false;
        }
        else {
            finishCycle();
        }
    }

    private void finishCycle() {
        phase = Phase.GROUND_READY;
        armedTick = Long.MIN_VALUE;
        returnStartedSequence = Long.MAX_VALUE;
        supportY = Integer.MIN_VALUE;
        airborneTicks = 0;
        target = null;
        prepared = null;
        attempt = null;
        hold = null;
        envelopePhase = EnvelopePhase.EMPTY;
        insideControllerCall = false;
        cycleDraft = null;
        frontier = null;
        cycleRawForward = 0.0F;
        cycleRawStrafe = 0.0F;
        cycleDirectionX = 0.0D;
        cycleDirectionZ = 0.0D;
        preferredPlacementYaw = 0.0F;
        takeoffYaw = 0.0F;
        groundViewYaw = returnYaw;
        groundViewPitch = returnPitch;
        returnRotationCaptured = false;
        returnLookPlanned = false;
        placementDispatchedThisCycle = false;
        lanePlannedThisCycle = false;
        diagonalSprintAllowed = false;
        diagonalRouteEnd = null;
        intaveBridgeCycle = false;
        intaveZeroInputSamples = 0;
        frozenRotationAvailable = false;
        jumpLatch.reset();
        safetySneak = false;
        owner.setTellySneak(false);
    }

    private boolean protocolTimedOut(long tick) {
        long start = hold != null ? hold.armedTick
                : attempt != null ? attempt.armedTick : Long.MAX_VALUE;
        return isTimedOut(start, tick, PACKET_TIMEOUT_TICKS);
    }

    private boolean validRuntimeContext() {
        return Utils.nullCheck()
                && mc.theWorld != null
                && owner.getPlayerForTelly() != null
                && mc.currentScreen == null;
    }

    private void captureCycle(
            EntityPlayerSP player, InputSample sample, BlockPos support,
            CycleKind kind) {
        supportY = support.getY();
        cycleOriginX = player.posX;
        cycleOriginZ = player.posZ;
        cycleRawForward = sample.rawForward;
        cycleRawStrafe = sample.rawStrafe;
        double[] direction = movementDirection(
                sample.cameraYaw, cycleRawForward, cycleRawStrafe);
        cycleDirectionX = direction == null ? 0.0D : direction[0];
        cycleDirectionZ = direction == null ? 0.0D : direction[1];
        returnYaw = isFinite(sample.cameraYaw)
                ? sample.cameraYaw : player.rotationYaw;
        returnPitch = isFinite(sample.cameraPitch)
                ? MathHelper.clamp_float(sample.cameraPitch, -90.0F, 90.0F)
                : player.rotationPitch;
        float travelYaw = direction == null
                ? returnYaw
                : (float) Math.toDegrees(Math.atan2(
                -cycleDirectionX, cycleDirectionZ));
        float takeoffReference = reportedPose != null
                && reportedPose.hasRotation
                ? reportedPose.yaw : returnYaw;
        takeoffYaw = quantizeYaw(
                takeoffReference,
                nearestEquivalent(travelYaw, takeoffReference),
                sensitivityStep());
        preferredPlacementYaw = placementYawForOffset(
                takeoffYaw, owner.getTellyYawOffset(), returnYaw);
        SilentRotationManager.ResolvedInput takeoffInput =
                SilentRotationManager.resolveMovementInput(
                        returnYaw, takeoffYaw,
                        cycleRawForward, cycleRawStrafe, false);
        MovementFrame travel = new MovementFrame(
                returnYaw, takeoffYaw,
                cycleRawForward, cycleRawStrafe,
                takeoffInput.forward, takeoffInput.strafe,
                false, sample.sprinting);
        cycleDraft = new CycleDraft(
                kind, support, travel,
                cycleDirectionX, cycleDirectionZ,
                cycleOriginX, cycleOriginZ,
                takeoffYaw, returnYaw, returnPitch,
                preferredPlacementYaw);
        if (kind == CycleKind.BRIDGE) {
            if (bridgeLane == null
                    || !canReuseLaneAtSupport(
                    bridgeLane, support,
                    cycleDirectionX, cycleDirectionZ)
                    || !isFullSupport(bridgeLane.current())) {
                bridgeLane = new DdaLane(
                        support, cycleOriginX, cycleOriginZ,
                        cycleDirectionX, cycleDirectionZ);
            }
            frontier = bridgeLane.current();
        }
        else {
            frontier = support;
        }
        returnRotationCaptured = true;
        returnLookPlanned = false;
        lanePlannedThisCycle = false;
        diagonalSprintAllowed = false;
        diagonalRouteEnd = null;
        intaveBridgeCycle = kind == CycleKind.BRIDGE
                && owner.isTellyIntaveEnabled();
        intaveZeroInputSamples = 0;
        returnStartedSequence = Long.MAX_VALUE;
        airborneTicks = 0;
        envelopePhase = EnvelopePhase.EMPTY;
    }

    private void clearCycleDraft() {
        supportY = Integer.MIN_VALUE;
        frontier = null;
        cycleRawForward = 0.0F;
        cycleRawStrafe = 0.0F;
        cycleDirectionX = 0.0D;
        cycleDirectionZ = 0.0D;
        preferredPlacementYaw = 0.0F;
        takeoffYaw = 0.0F;
        returnRotationCaptured = false;
        lanePlannedThisCycle = false;
        diagonalSprintAllowed = false;
        diagonalRouteEnd = null;
        intaveBridgeCycle = false;
        intaveZeroInputSamples = 0;
        cycleDraft = null;
        target = null;
        prepared = null;
        envelopePhase = EnvelopePhase.EMPTY;
    }

    private void suppressTowerHorizontalInput(
            EventMovementInput event, InputSample sample) {
        event.setSpeed(0.0F);
        event.setStrafe(0.0F);
        movementFrame = new MovementFrame(
                sample.cameraYaw, sample.movementYaw,
                0.0F, 0.0F, 0.0F, 0.0F,
                sample.sneaking, false);
    }

    private void applyBridgeTakeoffInput(
            EventMovementInput event, InputSample sample) {
        if (cycleDraft == null || cycleDraft.kind != CycleKind.BRIDGE) {
            return;
        }
        event.setSpeed(cycleDraft.travel.forward);
        event.setStrafe(cycleDraft.travel.strafe);
        movementFrame = new MovementFrame(
                cycleDraft.travel.cameraYaw,
                cycleDraft.travel.movementYaw,
                cycleDraft.travel.rawForward,
                cycleDraft.travel.rawStrafe,
                cycleDraft.travel.forward,
                cycleDraft.travel.strafe,
                sample.sneaking, sample.sprinting);
    }

    private boolean physicalJumpDown() {
        return mc.gameSettings != null
                && Utils.isBindDown(mc.gameSettings.keyBindJump);
    }

    private boolean canRearmFromReturn(EntityPlayerSP player) {
        return player != null && player.onGround
                && phase == Phase.RETURNING
                && attempt == null && hold == null
                && envelopePhase == EnvelopePhase.EMPTY;
    }

    private boolean hasBridgeExtensionCandidate() {
        if (bridgeLane == null || frontier == null
                || !frontier.equals(bridgeLane.current())
                || !isFullSupport(frontier)) {
            return false;
        }
        DdaLane.Step step = bridgeLane.peek();
        return step != null && (isReplaceable(step.destination)
                || isFullSupport(step.destination));
    }

    private boolean isTowerCycle() {
        return cycleDraft != null
                && cycleDraft.kind == CycleKind.TOWER;
    }

    private TargetKey towerTarget(BlockPos support) {
        if (support == null || !isFullSupport(support)) {
            return null;
        }
        BlockPos destination = support.up();
        return isReplaceable(destination)
                ? new TargetKey(support, EnumFacing.UP, destination)
                : null;
    }

    private void engageSafetySneak(
            EventMovementInput event, InputSample sample,
            EntityPlayerSP player) {
        safetySneak = true;
        owner.setTellySneak(true);
        if (player.movementInput != null) {
            player.movementInput.sneak = true;
        }
        float forward = event.getSpeed();
        float strafe = event.getStrafe();
        if (!sample.sneaking) {
            owner.noteScaffoldSneak("Telly/SafeWalk");
            forward *= 0.3F;
            strafe *= 0.3F;
            event.setSpeed(forward);
            event.setStrafe(strafe);
        }
        movementFrame = new MovementFrame(
                sample.cameraYaw, sample.movementYaw,
                sample.rawForward, sample.rawStrafe,
                forward, strafe, true, false);
        player.setSprinting(false);
    }

    private void releaseSafetySneak(EntityPlayerSP player) {
        boolean wasOwned = safetySneak;
        safetySneak = false;
        owner.setTellySneak(false);
        if (wasOwned && player != null && player.movementInput != null
                && !owner.isHoldingTellySneakRelease()
                && (mc.gameSettings == null
                || !Utils.isBindDown(mc.gameSettings.keyBindSneak))) {
            player.movementInput.sneak = false;
        }
    }

    private float rawForwardIntent(float fallback) {
        if (mc.gameSettings == null) {
            return fallback;
        }
        float value = 0.0F;
        if (Utils.isBindDown(mc.gameSettings.keyBindForward)) {
            value += 1.0F;
        }
        if (Utils.isBindDown(mc.gameSettings.keyBindBack)) {
            value -= 1.0F;
        }
        return value == 0.0F && Math.abs(fallback) > 1.0E-4F
                ? fallback : value;
    }

    private float rawStrafeIntent(float fallback) {
        if (mc.gameSettings == null) {
            return fallback;
        }
        float value = 0.0F;
        if (Utils.isBindDown(mc.gameSettings.keyBindLeft)) {
            value += 1.0F;
        }
        if (Utils.isBindDown(mc.gameSettings.keyBindRight)) {
            value -= 1.0F;
        }
        return value == 0.0F && Math.abs(fallback) > 1.0E-4F
                ? fallback : value;
    }

    private boolean shouldDispatchPrepared(EntityPlayerSP player) {
        if (clientTick == lastClickTick
                || airborneTicks < MIN_AIRBORNE_CLICK_TICKS) {
            return false;
        }
        if (isTowerCycle()) {
            return towerPlacementHasClearance(player, target);
        }
        return true;
    }

    private static boolean towerPlacementHasClearance(
            EntityPlayerSP player, TargetKey key) {
        if (player == null || key == null || key.face != EnumFacing.UP) {
            return false;
        }
        AxisAlignedBB box = player.getEntityBoundingBox();
        return box.minY >= key.destination.getY() + 1.0D
                - TOWER_CLEARANCE_EPSILON;
    }

    private static Vec3 clampHitVector(Vec3 hit, BlockPos support) {
        double x = Math.max(0.0D, Math.min(1.0D,
                hit.xCoord - support.getX()));
        double y = Math.max(0.0D, Math.min(1.0D,
                hit.yCoord - support.getY()));
        double z = Math.max(0.0D, Math.min(1.0D,
                hit.zCoord - support.getZ()));
        return new Vec3(
                support.getX() + x,
                support.getY() + y,
                support.getZ() + z);
    }

    private static boolean rotationMatches(
            ReportedPose pose, float yaw, float pitch, float gcd) {
        if (pose == null || !pose.hasRotation) {
            return false;
        }
        float tolerance = Math.max(1.0E-4F, safeGcd(gcd) * 0.51F);
        return Math.abs(MathHelper.wrapAngleTo180_float(
                pose.yaw - yaw)) <= tolerance
                && Math.abs(pose.pitch - pitch) <= tolerance;
    }

    private void updatePlacementTarget(EntityPlayerSP player) {
        if (attempt != null || hold != null) {
            return;
        }
        if (target != null && isTargetStructurallyValid(target)) {
            return;
        }
        if (!isTowerCycle() && !lanePlannedThisCycle) {
            chooseLandingLane(player);
        }
        target = isTowerCycle()
                ? towerTarget(cycleDraft.support)
                : selectNextFrontierTarget(frontier, player);
        prepared = null;
        envelopePhase = target == null
                ? EnvelopePhase.EMPTY : EnvelopePhase.AIMING;
    }

    private void chooseLandingLane(EntityPlayerSP player) {
        lanePlannedThisCycle = true;
        if (player == null || cycleDraft == null
                || cycleDraft.kind != CycleKind.BRIDGE
                || movementFrame == null || supportY == Integer.MIN_VALUE) {
            return;
        }
        BlockPos actualStart = cycleDraft.support;
        if (!isFullSupport(actualStart)) {
            bridgeLane = null;
            frontier = null;
            return;
        }
        LandingPrediction landing = simulateLanding(
                0.0D, 0.0D, player.posY,
                player.motionX, player.motionY, player.motionZ,
                movementFrame, player.jumpMovementFactor,
                supportY + 1.0D, MAX_PREDICTION_TICKS);
        if (!landing.landed) {
            return;
        }
        RoutePlan chosen = planLandingRoute(player, landing, actualStart);
        if (chosen != null && chosen.valid) {
            bridgeLane = chosen.lane.copy();
            frontier = bridgeLane.current();
        }
    }

    private boolean prepareDiagonalTakeoff(EntityPlayerSP player) {
        if (player == null || cycleDraft == null
                || cycleDraft.kind != CycleKind.BRIDGE
                || supportY == Integer.MIN_VALUE) {
            return false;
        }
        double motionY = BASE_JUMP_MOTION;
        if (player.isPotionActive(Potion.jump)) {
            motionY += (player.getActivePotionEffect(Potion.jump)
                    .getAmplifier() + 1) * 0.1F;
        }
        LandingPrediction sprintLanding = predictTakeoffLanding(
                player, motionY, true);
        RoutePlan sprintPlan = sprintLanding.landed
                ? planLandingRoute(
                player, sprintLanding, cycleDraft.support) : null;
        if (sprintPlan != null && sprintPlan.valid
                && diagonalSprintRouteFits(
                sprintLanding.ticks, sprintPlan.missing.size())) {
            diagonalSprintAllowed = true;
            applyDiagonalPlan(sprintPlan);
            return true;
        }

        LandingPrediction normalLanding = predictTakeoffLanding(
                player, motionY, false);
        RoutePlan normalPlan = normalLanding.landed
                ? planLandingRoute(
                player, normalLanding, cycleDraft.support) : null;
        if (normalPlan == null || !normalPlan.valid
                || !diagonalRouteFits(
                normalLanding.ticks, normalPlan.missing.size())) {
            return false;
        }
        diagonalSprintAllowed = false;
        applyDiagonalPlan(normalPlan);
        return true;
    }

    private LandingPrediction predictTakeoffLanding(
            EntityPlayerSP player, double motionY, boolean sprinting) {
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        if (sprinting) {
            float radians = takeoffYaw * 0.017453292F;
            motionX -= MathHelper.sin(radians) * 0.2F;
            motionZ += MathHelper.cos(radians) * 0.2F;
        }
        return simulateLanding(
                0.0D, 0.0D, player.posY,
                motionX, motionY, motionZ,
                cycleDraft.travel, player.jumpMovementFactor,
                supportY + 1.0D, MAX_PREDICTION_TICKS);
    }

    private void applyDiagonalPlan(RoutePlan plan) {
        bridgeLane = plan.lane.copy();
        frontier = bridgeLane.current();
        diagonalRouteEnd = plan.trace.cursor.current();
        lanePlannedThisCycle = true;
    }

    private RoutePlan planLandingRoute(
            EntityPlayerSP player, LandingPrediction landing,
            BlockPos start) {
        if (player == null || landing == null || start == null) {
            return null;
        }
        AxisAlignedBB landingBox = player.getEntityBoundingBox().offset(
                landing.offsetX, 0.0D, landing.offsetZ);
        DdaLane xFirst = new DdaLane(
                start, cycleOriginX, cycleOriginZ,
                cycleDirectionX, cycleDirectionZ, true);
        DdaLane zFirst = new DdaLane(
                start, cycleOriginX, cycleOriginZ,
                cycleDirectionX, cycleDirectionZ, false);
        return betterRoutePlan(
                planRoute(xFirst, landingBox, MAX_ROUTE_STEPS),
                planRoute(zFirst, landingBox, MAX_ROUTE_STEPS));
    }

    private RoutePlan planRoute(
            DdaLane lane, AxisAlignedBB landingBox, int maxSteps) {
        RouteTrace trace = traceRoute(lane, landingBox, maxSteps);
        if (trace == null || !trace.covered) {
            return RoutePlan.invalid(lane, trace);
        }
        List<BlockPos> missing = new ArrayList<BlockPos>();
        for (DdaLane.Step step : trace.steps) {
            if (isFullSupport(step.destination)) {
                continue;
            }
            if (!isReplaceable(step.destination)) {
                return RoutePlan.invalid(lane, trace);
            }
            missing.add(step.destination);
        }
        return RoutePlan.valid(lane, trace, missing);
    }

    static RoutePlan betterRoutePlan(RoutePlan first, RoutePlan second) {
        if (first == null || !first.valid) {
            return second != null && second.valid ? second : null;
        }
        if (second == null || !second.valid) {
            return first;
        }
        if (first.missing.size() != second.missing.size()) {
            return first.missing.size() < second.missing.size()
                    ? first : second;
        }
        if (first.packetSamples != second.packetSamples) {
            return first.packetSamples < second.packetSamples
                    ? first : second;
        }
        if (first.trace.steps.size() != second.trace.steps.size()) {
            return first.trace.steps.size() < second.trace.steps.size()
                    ? first : second;
        }
        if (Math.abs(first.trace.overlap - second.trace.overlap)
                > SUPPORT_EPSILON) {
            return first.trace.overlap > second.trace.overlap
                    ? first : second;
        }
        return first;
    }

    private TargetKey selectNextFrontierTarget(
            BlockPos start, EntityPlayerSP player) {
        if (start == null || player == null
                || supportY == Integer.MIN_VALUE
                || bridgeLane == null
                || !bridgeLane.isCompatible(
                supportY, cycleDirectionX, cycleDirectionZ)
                || !isFullSupport(bridgeLane.current())) {
            return null;
        }
        for (int depth = 0; depth < MAX_FRONTIER_WALK; depth++) {
            DdaLane.Step step = bridgeLane.peek();
            if (step == null) {
                return null;
            }
            if (isReplaceable(step.destination)) {
                return new TargetKey(
                        step.support, step.face, step.destination);
            }
            if (!isFullSupport(step.destination)) {
                return null;
            }
            bridgeLane.advance(step.destination);
            frontier = bridgeLane.current();
        }
        return null;
    }

    private boolean hasConservativeLandingRunway(EntityPlayerSP player) {
        if (player == null || supportY == Integer.MIN_VALUE
                || movementFrame == null) {
            return false;
        }
        LandingPrediction landing = simulateLanding(
                0.0D, 0.0D, player.posY,
                player.motionX, player.motionY, player.motionZ,
                movementFrame, player.jumpMovementFactor,
                supportY + 1.0D, MAX_PREDICTION_TICKS);
        AxisAlignedBB landingBox = player.getEntityBoundingBox().offset(
                landing.offsetX, 0.0D, landing.offsetZ);
        return landing.landed
                && supportOverlap(landingBox, supportY)
                >= MIN_SAFE_LANDING_OVERLAP
                && centerSupported(landingBox, supportY);
    }

    private boolean isIntaveInputReleasePhase() {
        EntityPlayerSP player = owner.getPlayerForTelly();
        return intaveBridgeCycle && (phase == Phase.STOPPING
                || phase == Phase.EXTENDING
                || phase == Phase.RETURNING
                && player != null && !player.onGround);
    }

    private boolean canContinueIntaveTravel(EntityPlayerSP player) {
        if (!intaveBridgeCycle || player == null || movementFrame == null) {
            return false;
        }
        LandingPrediction afterOneMoreTravel = simulateIntaveLanding(
                player, 1);
        if (!afterOneMoreTravel.landed) {
            return false;
        }
        int placements = projectedMissingPlacements(
                player, afterOneMoreTravel);
        if (placements < 0) {
            return false;
        }
        int budget = intaveStopBudget(placements);
        int samplesAfterTravel = Math.max(
                0, afterOneMoreTravel.ticks - 1);
        return samplesAfterTravel >= budget;
    }

    private LandingPrediction simulateIntaveLanding(
            EntityPlayerSP player, int travelTicks) {
        int continuingTicks = Math.max(0, travelTicks);
        MovementFrame releasedInput = new MovementFrame(
                movementFrame.cameraYaw, movementFrame.movementYaw,
                movementFrame.rawForward, movementFrame.rawStrafe,
                0.0F, 0.0F, movementFrame.sneaking, false);
        if (continuingTicks == 0) {
            return simulateLanding(
                    0.0D, 0.0D, player.posY,
                    player.motionX, player.motionY, player.motionZ,
                    releasedInput, player.jumpMovementFactor,
                    supportY + 1.0D, MAX_PREDICTION_TICKS);
        }
        LandingPrediction continued = simulateLanding(
                0.0D, 0.0D, player.posY,
                player.motionX, player.motionY, player.motionZ,
                movementFrame, player.jumpMovementFactor,
                supportY + 1.0D, continuingTicks);
        if (continued.landed || continued.ticks >= MAX_PREDICTION_TICKS) {
            return continued;
        }
        LandingPrediction coasting = simulateLanding(
                0.0D, 0.0D, player.posY + continued.offsetY,
                continued.motionX, continued.motionY, continued.motionZ,
                releasedInput, player.jumpMovementFactor,
                supportY + 1.0D,
                MAX_PREDICTION_TICKS - continued.ticks);
        return combinePredictions(continued, coasting);
    }

    static void releaseMovementInput(EventMovementInput event) {
        if (event == null) {
            return;
        }
        event.setSpeed(0.0F);
        event.setStrafe(0.0F);
        event.setJump(false);
    }

    static LandingPrediction combinePredictions(
            LandingPrediction first, LandingPrediction second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.landed) {
            return first;
        }
        return new LandingPrediction(
                first.offsetX + second.offsetX,
                first.offsetY + second.offsetY,
                first.offsetZ + second.offsetZ,
                first.ticks + second.ticks,
                second.landed,
                second.motionX, second.motionY, second.motionZ);
    }

    private int projectedMissingPlacements(
            EntityPlayerSP player, LandingPrediction landing) {
        if (player == null || landing == null || cycleDraft == null) {
            return -1;
        }
        RoutePlan plan = planLandingRoute(
                player, landing, cycleDraft.support);
        return plan != null && plan.valid
                ? Math.max(1, plan.missing.size()) : -1;
    }

    static int intaveStopBudget(int placements) {
        return sharedProtocolSamplesForPlacements(Math.max(1, placements))
                + INTAVE_ZERO_INPUT_SAMPLES + INTAVE_SAFETY_SAMPLES;
    }

    static boolean shouldStopForIntave(
            int remainingAirborneSamples, int placements) {
        return remainingAirborneSamples
                <= intaveStopBudget(placements);
    }

    private boolean centerSupported(AxisAlignedBB box, int y) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return isFullSupport(new BlockPos(
                MathHelper.floor_double(centerX), y,
                MathHelper.floor_double(centerZ)));
    }

    static boolean facesTravelDirection(
            EnumFacing face, double directionX, double directionZ) {
        if (face == null || face.getAxis() == EnumFacing.Axis.Y) {
            return false;
        }
        double length = Math.sqrt(
                directionX * directionX + directionZ * directionZ);
        if (length < 1.0E-4D) {
            return false;
        }
        double alignment = (face.getFrontOffsetX() * directionX
                + face.getFrontOffsetZ() * directionZ) / length;
        return alignment > 1.0E-4D;
    }

    private double supportOverlap(AxisAlignedBB box, int y) {
        int minX = MathHelper.floor_double(box.minX + SUPPORT_EPSILON);
        int maxX = MathHelper.floor_double(box.maxX - SUPPORT_EPSILON);
        int minZ = MathHelper.floor_double(box.minZ + SUPPORT_EPSILON);
        int maxZ = MathHelper.floor_double(box.maxZ - SUPPORT_EPSILON);
        double total = 0.0D;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos support = new BlockPos(x, y, z);
                if (isFullSupport(support)) {
                    total += overlapArea(box, support);
                }
            }
        }
        return total;
    }

    private static double overlapArea(AxisAlignedBB box, BlockPos cell) {
        double overlapX = Math.max(0.0D,
                Math.min(box.maxX, cell.getX() + 1.0D)
                        - Math.max(box.minX, cell.getX()));
        double overlapZ = Math.max(0.0D,
                Math.min(box.maxZ, cell.getZ() + 1.0D)
                        - Math.max(box.minZ, cell.getZ()));
        return overlapX * overlapZ;
    }

    private RouteTrace traceRoute(
            DdaLane source, AxisAlignedBB landingBox, int maxSteps) {
        if (source == null || landingBox == null) {
            return null;
        }
        DdaLane cursor = source.copy();
        List<DdaLane.Step> steps = new ArrayList<DdaLane.Step>();
        Set<BlockPos> support = new HashSet<BlockPos>();
        support.add(cursor.current());
        double overlap = supportOverlap(landingBox, support);
        boolean centerSupported = landingCenterSupported(
                landingBox, support);
        int limit = Math.max(1, maxSteps);
        while ((overlap < MIN_SAFE_LANDING_OVERLAP || !centerSupported)
                && steps.size() < limit) {
            DdaLane.Step step = cursor.peek();
            if (step == null || step.destination == null) {
                break;
            }
            steps.add(step);
            cursor.advance(step.destination);
            support.add(step.destination);
            overlap = supportOverlap(landingBox, support);
            centerSupported = landingCenterSupported(
                    landingBox, support);
        }
        boolean covered = overlap >= MIN_SAFE_LANDING_OVERLAP
                && centerSupported;
        return new RouteTrace(steps, cursor, overlap, covered);
    }

    private double supportOverlap(
            AxisAlignedBB box, Set<BlockPos> virtualSupport) {
        if (box == null || virtualSupport == null || virtualSupport.isEmpty()) {
            return 0.0D;
        }
        int minX = MathHelper.floor_double(box.minX + SUPPORT_EPSILON);
        int maxX = MathHelper.floor_double(box.maxX - SUPPORT_EPSILON);
        int minZ = MathHelper.floor_double(box.minZ + SUPPORT_EPSILON);
        int maxZ = MathHelper.floor_double(box.maxZ - SUPPORT_EPSILON);
        double total = 0.0D;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos cell = new BlockPos(x, supportY, z);
                if (virtualSupport.contains(cell)
                        || isFullSupport(cell)) {
                    total += overlapArea(box, cell);
                }
            }
        }
        return total;
    }

    private boolean landingCenterSupported(
            AxisAlignedBB box, Set<BlockPos> virtualSupport) {
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        BlockPos center = new BlockPos(
                MathHelper.floor_double(centerX), supportY,
                MathHelper.floor_double(centerZ));
        return virtualSupport.contains(center) || isFullSupport(center);
    }

    private AimSolution solveAim(
            TargetKey key, PoseOrigin origin, PoseOrigin sharedOrigin,
            float referenceYaw, float referencePitch,
            float preferredYaw) {
        if (key == null || origin == null || mc.theWorld == null) {
            return null;
        }
        float gcd = sensitivityStep();
        TargetKey sharedTarget = nextConnectedTarget(key);
        AimSolution best = null;
        for (Vec3 point : faceSamplePoints(key.support, key.face)) {
            float[] raw = rotationsToPoint(origin.eye(), point,
                    referenceYaw, referencePitch);
            float quantizedYaw = quantizeYaw(
                    referenceYaw, raw[0], gcd);
            float quantizedPitch = quantizeLinear(
                    referencePitch, raw[1], gcd);
            for (float yaw : axisSafeYawCandidates(
                    quantizedYaw, gcd)) {
                MovingObjectPosition hit = rayTraceForTarget(
                        origin, yaw, quantizedPitch, key);
                if (!matchesTarget(hit, key)
                        || !isUsablePitch(quantizedPitch)) {
                    continue;
                }
                float yawMotion = Math.abs(MathHelper.wrapAngleTo180_float(
                        yaw - referenceYaw));
                float yawPreference = Math.abs(MathHelper.wrapAngleTo180_float(
                        yaw - preferredYaw));
                float pitchMotion = Math.abs(
                        quantizedPitch - referencePitch);
                boolean sharesNextFace = sharedTarget == null
                        || rayIntersectsVirtualFace(
                        sharedOrigin == null ? origin : sharedOrigin,
                        yaw, quantizedPitch,
                        sharedTarget.support, sharedTarget.face);
                double score = yawPreference * 4.0D
                        + yawMotion * 0.1D + pitchMotion * 0.25D
                        + origin.eye().distanceTo(hit.hitVec) * 0.01D
                        + (sharesNextFace
                        ? 0.0D : SHARED_LOOK_MISS_PENALTY)
                        + (isPlacementPitchSafe(quantizedPitch)
                        ? 0.0D : 1000.0D);
                if (best == null || score < best.score) {
                    best = new AimSolution(
                            yaw, quantizedPitch, hit.hitVec, score);
                }
            }
        }
        return best;
    }

    private TargetKey nextConnectedTarget(TargetKey currentTarget) {
        if (currentTarget == null || bridgeLane == null
                || isTowerCycle()
                || diagonalRouteEnd == null
                || !isDiagonalDirection(
                cycleDirectionX, cycleDirectionZ)) {
            return null;
        }
        DdaLane cursor = bridgeLane.copy();
        if (!currentTarget.support.equals(cursor.current())
                || !cursor.advance(currentTarget.destination)) {
            return null;
        }
        for (int depth = 0; depth < MAX_FRONTIER_WALK; depth++) {
            if (cursor.current().equals(diagonalRouteEnd)) {
                return null;
            }
            DdaLane.Step step = cursor.peek();
            if (step == null) {
                return null;
            }
            if (isReplaceable(step.destination)) {
                return new TargetKey(
                        step.support, step.face, step.destination);
            }
            if (!isFullSupport(step.destination)
                    || !cursor.advance(step.destination)) {
                return null;
            }
        }
        return null;
    }

    private static List<Vec3> faceSamplePoints(
            BlockPos support, EnumFacing face) {
        List<Vec3> points = new ArrayList<Vec3>();
        for (double first : FACE_SAMPLES) {
            for (double second : FACE_SAMPLES) {
                double x = support.getX() + 0.5D;
                double y = support.getY() + first;
                double z = support.getZ() + 0.5D;
                if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
                    x = support.getX() + first;
                    y = support.getY()
                            + (face == EnumFacing.UP ? 1.0D : 0.0D);
                    z = support.getZ() + second;
                }
                else if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
                    x = support.getX() + second;
                    z = support.getZ()
                            + (face == EnumFacing.SOUTH ? 1.0D : 0.0D);
                }
                else if (face == EnumFacing.WEST || face == EnumFacing.EAST) {
                    z = support.getZ() + second;
                    x = support.getX()
                            + (face == EnumFacing.EAST ? 1.0D : 0.0D);
                }
                points.add(new Vec3(x, y, z));
            }
        }
        return points;
    }

    private static float[] rotationsToPoint(
            Vec3 eye, Vec3 point, float referenceYaw, float referencePitch) {
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float rawYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rawPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{
                nearestEquivalent(rawYaw, referenceYaw),
                MathHelper.clamp_float(rawPitch, -90.0F, 90.0F)
        };
    }

    private MovingObjectPosition rayTrace(
            PoseOrigin origin, float yaw, float pitch) {
        Vec3 eye = origin.eye();
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(
                look.xCoord * REACH,
                look.yCoord * REACH,
                look.zCoord * REACH);
        return mc.theWorld.rayTraceBlocks(
                eye, end, false, false, true);
    }

    private MovingObjectPosition rayTraceForTarget(
            PoseOrigin origin, float yaw, float pitch, TargetKey key) {
        MovingObjectPosition worldHit = rayTrace(origin, yaw, pitch);
        if (matchesTarget(worldHit, key)
                || key == null || !optimisticPlacements.contains(key.support)) {
            return worldHit;
        }
        Vec3 eye = origin.eye();
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(
                look.xCoord * REACH,
                look.yCoord * REACH,
                look.zCoord * REACH);
        AxisAlignedBB bounds = new AxisAlignedBB(
                key.support.getX(), key.support.getY(), key.support.getZ(),
                key.support.getX() + 1.0D,
                key.support.getY() + 1.0D,
                key.support.getZ() + 1.0D);
        MovingObjectPosition virtualHit = bounds.calculateIntercept(eye, end);
        if (virtualHit == null || virtualHit.sideHit != key.face) {
            return worldHit;
        }
        if (worldHit != null && worldHit.hitVec != null
                && eye.distanceTo(worldHit.hitVec)
                < eye.distanceTo(virtualHit.hitVec) - SUPPORT_EPSILON) {
            return worldHit;
        }
        return new MovingObjectPosition(
                virtualHit.hitVec, virtualHit.sideHit, key.support);
    }

    private static boolean matchesTarget(
            MovingObjectPosition hit, TargetKey target) {
        return hit != null
                && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && target != null
                && target.support.equals(hit.getBlockPos())
                && target.face == hit.sideHit;
    }

    static boolean matchesPacket(
            C08PacketPlayerBlockPlacement packet,
            PreparedPlacement prepared) {
        if (packet == null || prepared == null
                || !prepared.target.support.equals(packet.getPosition())
                || prepared.target.face.getIndex()
                != packet.getPlacedBlockDirection()) {
            return false;
        }
        Vec3 hit = prepared.hitVec;
        float expectedX = (float) (hit.xCoord
                - prepared.target.support.getX());
        float expectedY = (float) (hit.yCoord
                - prepared.target.support.getY());
        float expectedZ = (float) (hit.zCoord
                - prepared.target.support.getZ());
        return Math.abs(packet.getPlacedBlockOffsetX() - expectedX) < 1.0E-4F
                && Math.abs(packet.getPlacedBlockOffsetY() - expectedY) < 1.0E-4F
                && Math.abs(packet.getPlacedBlockOffsetZ() - expectedZ) < 1.0E-4F;
    }

    private boolean isTargetStructurallyValid(TargetKey key) {
        if (key == null || supportY == Integer.MIN_VALUE
                || key.support.getY() != supportY
                || !key.support.offset(key.face).equals(key.destination)) {
            return false;
        }
        boolean validLayer = isTowerCycle()
                ? key.face == EnumFacing.UP
                && key.destination.getY() == supportY + 1
                : key.face.getAxis() != EnumFacing.Axis.Y
                && key.destination.getY() == supportY;
        return validLayer && isFullSupport(key.support)
                && isReplaceable(key.destination)
                && owner.getCurrentTellyBlock() != null;
    }

    private boolean isProtocolTargetValid(TargetKey key) {
        return key != null
                && (key.face.getAxis() != EnumFacing.Axis.Y
                || isTowerCycle() && key.face == EnumFacing.UP)
                && key.support.offset(key.face).equals(key.destination)
                && isFullSupport(key.support);
    }

    private boolean isFullSupport(BlockPos position) {
        if (position == null || mc.theWorld == null) {
            return false;
        }
        if (optimisticPlacements.contains(position)) {
            return true;
        }
        IBlockState state = mc.theWorld.getBlockState(position);
        return state.getBlock() != Blocks.air
                && state.getBlock().isFullCube();
    }

    private boolean isReplaceable(BlockPos position) {
        if (position == null || mc.theWorld == null) {
            return false;
        }
        if (optimisticPlacements.contains(position)) {
            return false;
        }
        IBlockState state = mc.theWorld.getBlockState(position);
        return state.getBlock() == Blocks.air
                || state.getBlock().isReplaceable(mc.theWorld, position);
    }

    private BlockPos findSupportUnderPlayer(EntityPlayerSP player) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        int y = MathHelper.floor_double(box.minY - SUPPORT_EPSILON);
        int minX = MathHelper.floor_double(box.minX + SUPPORT_EPSILON);
        int maxX = MathHelper.floor_double(box.maxX - SUPPORT_EPSILON);
        int minZ = MathHelper.floor_double(box.minZ + SUPPORT_EPSILON);
        int maxZ = MathHelper.floor_double(box.maxZ - SUPPORT_EPSILON);
        BlockPos best = null;
        double bestOverlap = 0.0D;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos candidate = new BlockPos(x, y, z);
                if (!isFullSupport(candidate)) {
                    continue;
                }
                double overlap = overlapArea(box, candidate);
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean willLeaveSupport(
            EntityPlayerSP player, MovementFrame frame) {
        float movementFactor = player.onGround
                ? groundMovementFactor(player, frame)
                : player.jumpMovementFactor;
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        LandingPrediction step = simulateLanding(
                0.0D, 0.0D, player.posY,
                motionX, player.motionY, motionZ,
                frame, movementFactor,
                Double.NEGATIVE_INFINITY, 1);
        AxisAlignedBB predicted = player.getEntityBoundingBox()
                .offset(step.offsetX, -0.5D, step.offsetZ);
        return mc.theWorld.getCollidingBoundingBoxes(
                player, predicted).isEmpty();
    }

    private float groundMovementFactor(
            EntityPlayerSP player, MovementFrame frame) {
        BlockPos below = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(
                        player.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(player.posZ));
        float friction = player.worldObj.getBlockState(below)
                .getBlock().slipperiness * 0.91F;
        float acceleration = 0.16277136F
                / (friction * friction * friction);
        float moveSpeed = player.getAIMoveSpeed();
        if (!player.isSprinting() && frame.forward >= 0.8F
                && !frame.sneaking) {
            moveSpeed *= 1.3F;
        }
        return moveSpeed * acceleration;
    }

    private void holdReportedRotation(
            EventSilentRotation event, EntityPlayerSP player) {
        if (reportedPose != null && reportedPose.hasRotation) {
            requestRotation(event, RotationCommand.hold(
                    reportedPose.yaw, reportedPose.pitch));
        }
        else {
            requestRotation(event, RotationCommand.hold(
                    player.rotationYaw, player.rotationPitch));
        }
    }

    private void requestReturnRotation(
            EventSilentRotation event, EntityPlayerSP player) {
        if (!returnRotationCaptured) {
            finishCycle();
            return;
        }
        float currentYaw = authorityYaw(player);
        float currentPitch = authorityPitch(player);
        float targetYaw = nearestEquivalent(returnYaw, currentYaw);
        float targetPitch = returnPitch;
        RotationCommand command = returnRotation(
                currentYaw, currentPitch,
                targetYaw, targetPitch, sensitivityStep());
        requestRotation(event, command);
        returnLookPlanned = command.reaches(
                targetYaw, targetPitch, sensitivityStep());
        phase = Phase.RETURNING;
    }

    private void requestRotation(
            EventSilentRotation event, RotationCommand command) {
        pendingRotation = command;
        plannedYaw = command.yaw;
        plannedRotationAvailable = true;
        event.setYawSpeed(360.0F);
        event.setPitchSpeed(360.0F);
        event.setMaxYawDelta(MAX_YAW_STEP);
        event.setPreventDuplicateLook(false);
        event.setYaw(command.yaw);
        event.setPitch(command.pitch);
    }

    private float currentMovementYaw(EntityPlayerSP player) {
        if (frozenRotationAvailable) {
            return frozenYaw;
        }
        if (pendingRotation != null) {
            return pendingRotation.yaw;
        }
        if (reportedPose != null && reportedPose.hasRotation) {
            return reportedPose.yaw;
        }
        return player == null ? rotations.getYaw() : player.rotationYaw;
    }

    private void freezeFinalRotation(EntityPlayerSP player) {
        Float finalYaw = RotationHelper.get().getServerYaw();
        Float finalPitch = RotationHelper.get().getServerPitch();
        frozenYaw = finalYaw != null && isFinite(finalYaw)
                ? finalYaw : plannedYaw;
        frozenPitch = finalPitch != null && isFinite(finalPitch)
                ? finalPitch : plannedPitch;
        if (!isFinite(frozenYaw)) {
            frozenYaw = player != null ? player.rotationYaw : rotations.getYaw();
        }
        if (!isFinite(frozenPitch)) {
            frozenPitch = player != null
                    ? player.rotationPitch : rotations.getPitch();
        }
        frozenPitch = MathHelper.clamp_float(frozenPitch, -90.0F, 90.0F);
        frozenRotationAvailable = true;
        plannedYaw = frozenYaw;
        plannedPitch = frozenPitch;
    }

    private float authorityYaw(EntityPlayerSP player) {
        return reportedPose != null && reportedPose.hasRotation
                ? reportedPose.yaw
                : player != null ? player.rotationYaw : rotations.getYaw();
    }

    private float authorityPitch(EntityPlayerSP player) {
        return reportedPose != null && reportedPose.hasRotation
                ? reportedPose.pitch
                : player != null ? player.rotationPitch : rotations.getPitch();
    }

    private PoseOrigin playerOrigin(EntityPlayerSP player) {
        return new PoseOrigin(
                player.posX, player.posY, player.posZ,
                reportedPose != null
                        ? reportedPose.sneaking : player.isSneaking());
    }

    private PoseOrigin predictedPacketOrigin(EntityPlayerSP player) {
        return predictedPacketOrigin(player, 1);
    }

    private PoseOrigin predictedPacketOrigin(
            EntityPlayerSP player, int samples) {
        if (movementFrame == null) {
            return playerOrigin(player);
        }
        LandingPrediction step = simulateLanding(
                0.0D, 0.0D, player.posY,
                player.motionX, player.motionY, player.motionZ,
                movementFrame, player.jumpMovementFactor,
                Double.NEGATIVE_INFINITY, Math.max(1, samples));
        return new PoseOrigin(
                player.posX + step.offsetX,
                player.posY + step.offsetY,
                player.posZ + step.offsetZ,
                reportedPose != null
                        ? reportedPose.sneaking : player.isSneaking());
    }

    private float sensitivityStep() {
        if (mc.gameSettings == null) {
            return 0.15F;
        }
        float step = Math.abs(RotationUtils.getGCD());
        return step > 0.0F && !Float.isNaN(step)
                && !Float.isInfinite(step) ? step : 0.15F;
    }

    private static boolean localPoseNearSource(
            EntityPlayerSP player, ReportedPose source) {
        if (player == null || source == null || !source.hasPosition) {
            return false;
        }
        return Math.abs(player.posX - source.x) <= LOCAL_POSE_TOLERANCE
                && Math.abs(player.posY - source.y) <= LOCAL_POSE_TOLERANCE
                && Math.abs(player.posZ - source.z) <= LOCAL_POSE_TOLERANCE;
    }

    static boolean shouldArmJump(
            boolean onGround, boolean idle, boolean hasMovement,
            boolean willLeaveSupport, boolean hasSupport, boolean hasBlock) {
        return onGround && idle && hasMovement && willLeaveSupport
                && hasSupport && hasBlock;
    }

    static boolean shouldRequestTower(
            boolean physicalJump, boolean physicalMovementIntent,
            boolean hasSupport, boolean hasBlock, boolean sneaking) {
        return physicalJump && !physicalMovementIntent
                && hasSupport && hasBlock && !sneaking;
    }

    private static boolean hasMovementIntent(float forward, float strafe) {
        return Math.abs(forward) > 1.0E-4F
                || Math.abs(strafe) > 1.0E-4F;
    }

    static boolean canArmCycle(Phase phase) {
        return phase == Phase.GROUND_READY || phase == Phase.RETURNING;
    }

    static Phase nextIntavePhase(
            Phase phase, boolean airborne, int zeroInputSamples) {
        if (!airborne || phase == null) {
            return phase;
        }
        if (phase == Phase.TAKEOFF) {
            return Phase.TRAVELING;
        }
        if (phase == Phase.STOPPING
                && zeroInputSamples >= INTAVE_ZERO_INPUT_SAMPLES) {
            return Phase.EXTENDING;
        }
        return phase;
    }

    static Phase armedPhase(CycleKind kind) {
        return Phase.TAKEOFF;
    }

    static boolean canReuseLaneAtSupport(
            DdaLane lane, BlockPos support,
            double directionX, double directionZ) {
        return lane != null && support != null
                && support.equals(lane.current())
                && lane.isCompatible(
                support.getY(), directionX, directionZ);
    }

    static boolean shouldBeginReturn(
            boolean placedThisCycle, boolean targetPresent,
            boolean projectedLandingSupported) {
        return placedThisCycle && !targetPresent
                && projectedLandingSupported;
    }

    static boolean placementReady(
            boolean prepared, boolean matchingTarget,
            boolean latestReportedPose,
            boolean localPoseNearSource, boolean structurallyValid) {
        return prepared && matchingTarget && latestReportedPose
                && localPoseNearSource
                && structurallyValid;
    }

    static boolean preparedTargetOwnsWindow(
            PreparedPlacement prepared, TargetKey target) {
        return prepared != null && target != null
                && prepared.target.equals(target);
    }

    static boolean isTimedOut(long start, long now, int timeout) {
        return start != Long.MAX_VALUE && start != Long.MIN_VALUE
                && now - start > Math.max(0, timeout);
    }

    static boolean isAirbornePhase(Phase phase) {
        return phase == Phase.TAKEOFF || phase == Phase.TRAVELING
                || phase == Phase.STOPPING || phase == Phase.EXTENDING;
    }

    static boolean shouldPulseMidCycle(
            Phase phase, boolean onGround, int airborneTicks,
            double motionY, boolean alreadyUsed) {
        return shouldPulseMidCycle(
                phase, onGround, airborneTicks, motionY,
                alreadyUsed, true);
    }

    static boolean shouldPulseMidCycle(
            Phase phase, boolean onGround, int airborneTicks,
            double motionY, boolean alreadyUsed, boolean cycleEligible) {
        return cycleEligible && !alreadyUsed && !onGround
                && isAirbornePhase(phase)
                && airborneTicks > 1
                && !Double.isNaN(motionY)
                && !Double.isInfinite(motionY)
                && motionY <= ScaffoldSneakSafety.PREPARATION_MOTION_Y;
    }

    private static int clampSneakInterval(int interval) {
        return Math.max(1, Math.min(10, interval));
    }

    static RotationCommand stagedRotation(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch, float gcd) {
        float step = safeGcd(gcd);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                targetYaw - currentYaw);
        float yawLimit = SilentRotationManager.gcdFlooredLimit(
                MAX_YAW_STEP, step);
        float appliedYaw = Math.max(-yawLimit,
                Math.min(yawLimit, yawDelta));
        appliedYaw = quantizeLinear(0.0F, appliedYaw, step);
        float yaw = currentYaw + appliedYaw;

        float pitch = currentPitch;
        if (Math.abs(yawDelta) <= MAX_YAW_STEP) {
            float pitchDelta = targetPitch - currentPitch;
            float pitchLimit = SilentRotationManager.gcdFlooredLimit(
                    MAX_PITCH_STEP, step);
            float appliedPitch = Math.max(-pitchLimit,
                    Math.min(pitchLimit, pitchDelta));
            pitch = currentPitch
                    + quantizeLinear(0.0F, appliedPitch, step);
        }
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
        return new RotationCommand(yaw, pitch);
    }

    static RotationCommand returnRotation(
            float currentYaw, float currentPitch,
            float targetYaw, float targetPitch, float gcd) {
        float step = safeGcd(gcd);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                targetYaw - currentYaw);
        float yawLimit = SilentRotationManager.gcdFlooredLimit(
                MAX_YAW_STEP, step);
        float appliedYaw = Math.max(-yawLimit,
                Math.min(yawLimit, yawDelta));
        float yaw = currentYaw
                + quantizeLinear(0.0F, appliedYaw, step);

        float pitchDelta = targetPitch - currentPitch;
        float pitchLimit = SilentRotationManager.gcdFlooredLimit(
                RETURN_PITCH_STEP, step);
        float appliedPitch = Math.max(-pitchLimit,
                Math.min(pitchLimit, pitchDelta));
        float pitch = currentPitch
                + quantizeLinear(0.0F, appliedPitch, step);
        return new RotationCommand(
                yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    }

    static float quantizeYaw(float previous, float target, float gcd) {
        float delta = MathHelper.wrapAngleTo180_float(target - previous);
        return previous + Math.round(delta / safeGcd(gcd)) * safeGcd(gcd);
    }

    static float quantizeLinear(float previous, float target, float gcd) {
        float step = safeGcd(gcd);
        return previous + Math.round((target - previous) / step) * step;
    }

    private static float safeGcd(float gcd) {
        float step = Math.abs(gcd);
        return step > 0.0F && !Float.isNaN(step)
                && !Float.isInfinite(step) ? step : 0.15F;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static float nearestEquivalent(float target, float reference) {
        return target + 360.0F
                * Math.round((reference - target) / 360.0F);
    }

    static float placementYawForOffset(
            float travelYaw, float offset, float referenceYaw) {
        float safeTravelYaw = isFinite(travelYaw) ? travelYaw : referenceYaw;
        float safeOffset = isFinite(offset) ? offset : 0.0F;
        return nearestEquivalent(
                safeTravelYaw + 180.0F + safeOffset, referenceYaw);
    }

    static float distanceTo45DegreeAxis(float yaw) {
        float remainder = yaw % ANGLE_AXIS;
        if (remainder < 0.0F) {
            remainder += ANGLE_AXIS;
        }
        return Math.min(remainder, ANGLE_AXIS - remainder);
    }

    static float[] axisSafeYawCandidates(float yaw, float gcd) {
        if (distanceTo45DegreeAxis(yaw) >= ANGLE_AXIS_TOLERANCE) {
            return new float[]{yaw};
        }
        float step = safeGcd(gcd);
        int steps = Math.max(1,
                (int) Math.ceil(ANGLE_AXIS_TOLERANCE / step) + 1);
        return new float[]{yaw + steps * step, yaw - steps * step, yaw};
    }

    static boolean isPlacementPitchSafe(float pitch) {
        return !Float.isNaN(pitch) && !Float.isInfinite(pitch)
                && pitch <= MAX_PLACEMENT_PITCH;
    }

    private static boolean isUsablePitch(float pitch) {
        return isFinite(pitch) && pitch >= -90.0F && pitch <= 90.0F;
    }

    static double[] movementDirection(
            float yaw, float forward, float strafe) {
        double magnitude = Math.sqrt(forward * forward + strafe * strafe);
        if (magnitude < 1.0E-4D) {
            return null;
        }
        double radians = Math.toRadians(yaw);
        double sine = Math.sin(radians);
        double cosine = Math.cos(radians);
        double x = strafe * cosine - forward * sine;
        double z = forward * cosine + strafe * sine;
        double length = Math.sqrt(x * x + z * z);
        return new double[]{x / length, z / length};
    }

    static boolean isDiagonalDirection(double directionX, double directionZ) {
        double length = Math.hypot(directionX, directionZ);
        return length >= 1.0E-4D
                && Math.abs(directionX / length)
                >= DIAGONAL_COMPONENT_MINIMUM
                && Math.abs(directionZ / length)
                >= DIAGONAL_COMPONENT_MINIMUM;
    }

    static boolean sprintEligible(
            MovementFrame frame, boolean safetySneak,
            boolean allowDiagonal) {
        if (frame == null || !frame.hasMovement()
                || frame.forward < 0.8F || safetySneak) {
            return false;
        }
        double[] direction = frame.worldDirection();
        return allowDiagonal
                || !isDiagonalDirection(direction[0], direction[1]);
    }

    static boolean diagonalRouteFits(int landingTicks, int placements) {
        int availableAirborneSamples = Math.max(0, landingTicks - 1);
        return protocolSamplesForPlacements(placements)
                <= availableAirborneSamples;
    }

    static boolean diagonalSprintRouteFits(
            int landingTicks, int placements) {
        int availableAirborneSamples = Math.max(0, landingTicks - 1);
        return sharedProtocolSamplesForPlacements(placements)
                + DIAGONAL_SPRINT_SAFETY_SAMPLES
                <= availableAirborneSamples;
    }

    static boolean rayIntersectsBlockFromPose(
            double x, double feetY, double z, boolean sneaking,
            float yaw, float pitch, BlockPos block) {
        PoseOrigin origin = new PoseOrigin(x, feetY, z, sneaking);
        return virtualBlockIntercept(origin, yaw, pitch, block) != null;
    }

    static boolean rayIntersectsFaceFromPose(
            double x, double feetY, double z, boolean sneaking,
            float yaw, float pitch, BlockPos block, EnumFacing face) {
        MovingObjectPosition hit = virtualBlockIntercept(
                new PoseOrigin(x, feetY, z, sneaking),
                yaw, pitch, block);
        return hit != null && hit.sideHit == face;
    }

    private static boolean rayIntersectsVirtualFace(
            PoseOrigin origin, float yaw, float pitch,
            BlockPos block, EnumFacing face) {
        MovingObjectPosition hit = virtualBlockIntercept(
                origin, yaw, pitch, block);
        return hit != null && hit.sideHit == face;
    }

    private static MovingObjectPosition virtualBlockIntercept(
            PoseOrigin origin, float yaw, float pitch, BlockPos block) {
        if (origin == null || block == null) {
            return null;
        }
        Vec3 eye = origin.eye();
        Vec3 look = Utils.getLookVec(yaw, pitch);
        Vec3 end = eye.addVector(
                look.xCoord * REACH,
                look.yCoord * REACH,
                look.zCoord * REACH);
        AxisAlignedBB bounds = new AxisAlignedBB(
                block.getX(), block.getY(), block.getZ(),
                block.getX() + 1.0D,
                block.getY() + 1.0D,
                block.getZ() + 1.0D);
        return bounds.calculateIntercept(eye, end);
    }

    static LandingPrediction simulateLanding(
            double startX, double startZ, double startFeetY,
            double initialMotionX, double initialMotionY,
            double initialMotionZ, MovementFrame frame,
            float movementFactor, double supportTop, int maxTicks) {
        double x = startX;
        double y = startFeetY;
        double z = startZ;
        double motionX = initialMotionX;
        double motionY = initialMotionY;
        double motionZ = initialMotionZ;
        int ticks = 0;
        boolean landed = false;
        for (int tick = 0; tick < Math.max(0, maxTicks); tick++) {
            if (Math.abs(motionX) < 0.005D) {
                motionX = 0.0D;
            }
            if (Math.abs(motionZ) < 0.005D) {
                motionZ = 0.0D;
            }
            double[] acceleration = movementAcceleration(
                    frame.movementYaw, frame.forward, frame.strafe,
                    movementFactor);
            motionX += acceleration[0];
            motionZ += acceleration[1];
            x += motionX;
            y += motionY;
            z += motionZ;
            ticks++;
            if (motionY <= 0.0D && y <= supportTop) {
                landed = supportTop != Double.NEGATIVE_INFINITY;
                break;
            }
            motionY = (motionY - 0.08D) * 0.9800000190734863D;
            motionX *= 0.91F;
            motionZ *= 0.91F;
        }
        return new LandingPrediction(
                x - startX, y - startFeetY, z - startZ,
                ticks, landed, motionX, motionY, motionZ);
    }

    private static double[] movementAcceleration(
            float yaw, float forward, float strafe, float movementFactor) {
        float scaledForward = forward * 0.98F;
        float scaledStrafe = strafe * 0.98F;
        float magnitude = scaledStrafe * scaledStrafe
                + scaledForward * scaledForward;
        if (magnitude < 1.0E-4F) {
            return new double[]{0.0D, 0.0D};
        }
        magnitude = MathHelper.sqrt_float(magnitude);
        if (magnitude < 1.0F) {
            magnitude = 1.0F;
        }
        float scale = movementFactor / magnitude;
        scaledStrafe *= scale;
        scaledForward *= scale;
        float radians = yaw * 0.017453292F;
        float sine = MathHelper.sin(radians);
        float cosine = MathHelper.cos(radians);
        return new double[]{
                scaledStrafe * cosine - scaledForward * sine,
                scaledForward * cosine + scaledStrafe * sine
        };
    }

    enum Phase {
        GROUND_READY,
        TAKEOFF,
        TRAVELING,
        STOPPING,
        EXTENDING,
        RETURNING
    }

    enum CycleKind {
        BRIDGE,
        TOWER
    }

    enum EnvelopePhase {
        EMPTY,
        AIMING,
        PREPARED,
        WAITING_C08,
        POST_C08_HOLD
    }

    static final class JumpLatch {
        private boolean pending;

        boolean request() {
            if (pending) {
                return false;
            }
            pending = true;
            return true;
        }

        boolean consume(boolean userJump) {
            boolean result = userJump || pending;
            pending = false;
            return result;
        }

        boolean isPending() {
            return pending;
        }

        void reset() {
            pending = false;
        }
    }

    static final class MovementFrame {
        final float cameraYaw;
        final float movementYaw;
        final float rawForward;
        final float rawStrafe;
        final float forward;
        final float strafe;
        final boolean sneaking;
        final boolean sprinting;

        MovementFrame(
                float cameraYaw, float movementYaw,
                float rawForward, float rawStrafe,
                float forward, float strafe,
                boolean sneaking, boolean sprinting) {
            this.cameraYaw = cameraYaw;
            this.movementYaw = movementYaw;
            this.rawForward = rawForward;
            this.rawStrafe = rawStrafe;
            this.forward = forward;
            this.strafe = strafe;
            this.sneaking = sneaking;
            this.sprinting = sprinting;
        }

        boolean hasMovement() {
            return Math.abs(forward) > 1.0E-4F
                    || Math.abs(strafe) > 1.0E-4F;
        }

        double[] worldDirection() {
            double[] direction = movementDirection(
                    movementYaw, forward, strafe);
            return direction == null
                    ? new double[]{0.0D, 0.0D} : direction;
        }
    }

    static final class CycleDraft {
        final CycleKind kind;
        final BlockPos support;
        final MovementFrame travel;
        final double directionX;
        final double directionZ;
        final double originX;
        final double originZ;
        final float takeoffYaw;
        final float returnYaw;
        final float returnPitch;
        final float placementYaw;

        CycleDraft(
                CycleKind kind, BlockPos support, MovementFrame travel,
                double directionX, double directionZ,
                double originX, double originZ,
                float takeoffYaw, float returnYaw,
                float returnPitch, float placementYaw) {
            this.kind = kind;
            this.support = support;
            this.travel = travel;
            this.directionX = directionX;
            this.directionZ = directionZ;
            this.originX = originX;
            this.originZ = originZ;
            this.takeoffYaw = takeoffYaw;
            this.returnYaw = returnYaw;
            this.returnPitch = returnPitch;
            this.placementYaw = placementYaw;
        }

        CycleDraft withSprinting(boolean sprinting) {
            MovementFrame updated = new MovementFrame(
                    travel.cameraYaw, travel.movementYaw,
                    travel.rawForward, travel.rawStrafe,
                    travel.forward, travel.strafe,
                    travel.sneaking, sprinting);
            return new CycleDraft(
                    kind, support, updated,
                    directionX, directionZ, originX, originZ,
                    takeoffYaw, returnYaw, returnPitch, placementYaw);
        }
    }

    static final class DdaLane {
        private static final double TIE_EPSILON = 1.0E-10D;

        private BlockPos current;
        private final double directionX;
        private final double directionZ;
        private final int stepX;
        private final int stepZ;
        private final double deltaX;
        private final double deltaZ;
        private final boolean preferXAxisOnTie;
        private double nextBoundaryX;
        private double nextBoundaryZ;

        DdaLane(
                BlockPos start, double originX, double originZ,
                double directionX, double directionZ) {
            this(start, originX, originZ,
                    directionX, directionZ, true);
        }

        DdaLane(
                BlockPos start, double originX, double originZ,
                double directionX, double directionZ,
                boolean preferXAxisOnTie) {
            if (start == null) {
                throw new IllegalArgumentException("start");
            }
            double length = Math.hypot(directionX, directionZ);
            if (length < 1.0E-4D) {
                throw new IllegalArgumentException("direction");
            }
            this.current = start;
            this.directionX = directionX / length;
            this.directionZ = directionZ / length;
            this.stepX = this.directionX > 1.0E-9D ? 1
                    : this.directionX < -1.0E-9D ? -1 : 0;
            this.stepZ = this.directionZ > 1.0E-9D ? 1
                    : this.directionZ < -1.0E-9D ? -1 : 0;
            this.deltaX = stepX == 0 ? Double.POSITIVE_INFINITY
                    : Math.abs(1.0D / this.directionX);
            this.deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                    : Math.abs(1.0D / this.directionZ);
            this.preferXAxisOnTie = preferXAxisOnTie;

            double clampedX = Math.max(start.getX() + TIE_EPSILON,
                    Math.min(start.getX() + 1.0D - TIE_EPSILON, originX));
            double clampedZ = Math.max(start.getZ() + TIE_EPSILON,
                    Math.min(start.getZ() + 1.0D - TIE_EPSILON, originZ));
            this.nextBoundaryX = stepX > 0
                    ? (start.getX() + 1.0D - clampedX) / this.directionX
                    : stepX < 0
                    ? (clampedX - start.getX()) / -this.directionX
                    : Double.POSITIVE_INFINITY;
            this.nextBoundaryZ = stepZ > 0
                    ? (start.getZ() + 1.0D - clampedZ) / this.directionZ
                    : stepZ < 0
                    ? (clampedZ - start.getZ()) / -this.directionZ
                    : Double.POSITIVE_INFINITY;
        }

        private DdaLane(DdaLane source) {
            this.current = source.current;
            this.directionX = source.directionX;
            this.directionZ = source.directionZ;
            this.stepX = source.stepX;
            this.stepZ = source.stepZ;
            this.deltaX = source.deltaX;
            this.deltaZ = source.deltaZ;
            this.preferXAxisOnTie = source.preferXAxisOnTie;
            this.nextBoundaryX = source.nextBoundaryX;
            this.nextBoundaryZ = source.nextBoundaryZ;
        }

        DdaLane copy() {
            return new DdaLane(this);
        }

        boolean prefersXAxisOnTie() {
            return preferXAxisOnTie;
        }

        BlockPos current() {
            return current;
        }

        boolean isCompatible(
                int y, double candidateX, double candidateZ) {
            double length = Math.hypot(candidateX, candidateZ);
            if (current.getY() != y || length < 1.0E-4D) {
                return false;
            }
            double dot = directionX * candidateX / length
                    + directionZ * candidateZ / length;
            return dot >= LANE_DIRECTION_DOT_MINIMUM;
        }

        Step peek() {
            double boundaryDelta = nextBoundaryX - nextBoundaryZ;
            boolean tied = Math.abs(boundaryDelta) <= TIE_EPSILON;
            boolean xAxis = tied ? preferXAxisOnTie
                    : boundaryDelta < 0.0D;
            if (stepX == 0) {
                xAxis = false;
            }
            else if (stepZ == 0) {
                xAxis = true;
            }
            BlockPos destination = xAxis
                    ? current.add(stepX, 0, 0)
                    : current.add(0, 0, stepZ);
            EnumFacing face = xAxis
                    ? stepX > 0 ? EnumFacing.EAST : EnumFacing.WEST
                    : stepZ > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
            return new Step(current, face, destination, xAxis);
        }

        boolean advance(BlockPos destination) {
            Step next = peek();
            if (destination == null || !next.destination.equals(destination)) {
                return false;
            }
            current = destination;
            if (next.xAxis) {
                nextBoundaryX += deltaX;
            }
            else {
                nextBoundaryZ += deltaZ;
            }
            return true;
        }

        static final class Step {
            final BlockPos support;
            final EnumFacing face;
            final BlockPos destination;
            final boolean xAxis;

            Step(
                    BlockPos support, EnumFacing face,
                    BlockPos destination, boolean xAxis) {
                this.support = support;
                this.face = face;
                this.destination = destination;
                this.xAxis = xAxis;
            }
        }
    }

    static final class RouteTrace {
        final List<DdaLane.Step> steps;
        final DdaLane cursor;
        final double overlap;
        final boolean covered;

        RouteTrace(
                List<DdaLane.Step> steps, DdaLane cursor,
                double overlap, boolean covered) {
            this.steps = steps;
            this.cursor = cursor;
            this.overlap = overlap;
            this.covered = covered;
        }
    }

    static final class RoutePlan {
        final DdaLane lane;
        final RouteTrace trace;
        final List<BlockPos> missing;
        final int packetSamples;
        final int sharedPacketSamples;
        final boolean valid;

        private RoutePlan(
                DdaLane lane, RouteTrace trace,
                List<BlockPos> missing, boolean valid) {
            this.lane = lane == null ? null : lane.copy();
            this.trace = trace;
            this.missing = missing;
            this.packetSamples = protocolSamplesForPlacements(
                    missing == null ? 0 : missing.size());
            this.sharedPacketSamples = sharedProtocolSamplesForPlacements(
                    missing == null ? 0 : missing.size());
            this.valid = valid;
        }

        static RoutePlan valid(
                DdaLane lane, RouteTrace trace,
                List<BlockPos> missing) {
            return new RoutePlan(
                    lane, trace,
                    new ArrayList<BlockPos>(missing), true);
        }

        static RoutePlan invalid(DdaLane lane, RouteTrace trace) {
            return new RoutePlan(
                    lane, trace, new ArrayList<BlockPos>(), false);
        }
    }

    static int protocolSamplesForPlacements(int placements) {
        int count = Math.max(0, placements);
        return count == 0 ? 0 : count * 2 + 1;
    }

    static int sharedProtocolSamplesForPlacements(int placements) {
        int count = Math.max(0, placements);
        return count == 0 ? 0 : count + 2;
    }

    private static final class InputSample {
        final float cameraYaw;
        final float cameraPitch;
        final float movementYaw;
        final float rawForward;
        final float rawStrafe;
        final boolean sneaking;
        final boolean sprinting;

        InputSample(
                float cameraYaw, float cameraPitch, float movementYaw,
                float rawForward, float rawStrafe,
                boolean sneaking, boolean sprinting) {
            this.cameraYaw = cameraYaw;
            this.cameraPitch = cameraPitch;
            this.movementYaw = movementYaw;
            this.rawForward = rawForward;
            this.rawStrafe = rawStrafe;
            this.sneaking = sneaking;
            this.sprinting = sprinting;
        }
    }

    static final class LandingPrediction {
        final double offsetX;
        final double offsetY;
        final double offsetZ;
        final int ticks;
        final boolean landed;
        final double motionX;
        final double motionY;
        final double motionZ;

        LandingPrediction(
                double offsetX, double offsetY, double offsetZ,
                int ticks, boolean landed,
                double motionX, double motionY, double motionZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.ticks = ticks;
            this.landed = landed;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
        }
    }

    static final class ReportedPose {
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final boolean onGround;
        final boolean sneaking;
        final boolean hasPosition;
        final boolean hasRotation;
        final long movementSequence;

        ReportedPose(
                double x, double y, double z,
                float yaw, float pitch,
                boolean onGround, boolean sneaking,
                boolean hasPosition, boolean hasRotation,
                long movementSequence) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
            this.sneaking = sneaking;
            this.hasPosition = hasPosition;
            this.hasRotation = hasRotation;
            this.movementSequence = movementSequence;
        }

        static ReportedPose advance(
                ReportedPose previous, C03PacketPlayer packet,
                long sequence) {
            boolean moving = packet.isMoving();
            boolean rotating = packet.getRotating();
            return new ReportedPose(
                    moving ? packet.getPositionX()
                            : previous == null ? 0.0D : previous.x,
                    moving ? packet.getPositionY()
                            : previous == null ? 0.0D : previous.y,
                    moving ? packet.getPositionZ()
                            : previous == null ? 0.0D : previous.z,
                    rotating ? packet.getYaw()
                            : previous == null ? 0.0F : previous.yaw,
                    rotating ? packet.getPitch()
                            : previous == null ? 0.0F : previous.pitch,
                    packet.isOnGround(),
                    previous != null && previous.sneaking,
                    moving || previous != null && previous.hasPosition,
                    rotating || previous != null && previous.hasRotation,
                    sequence);
        }

        static ReportedPose withSneaking(
                ReportedPose previous, boolean sneaking) {
            if (previous == null) {
                return new ReportedPose(
                        0.0D, 0.0D, 0.0D,
                        0.0F, 0.0F,
                        false, sneaking, false, false, 0L);
            }
            return new ReportedPose(
                    previous.x, previous.y, previous.z,
                    previous.yaw, previous.pitch,
                    previous.onGround, sneaking,
                    previous.hasPosition, previous.hasRotation,
                    previous.movementSequence);
        }

        PoseOrigin origin() {
            return new PoseOrigin(x, y, z, sneaking);
        }
    }

    static final class TargetKey {
        final BlockPos support;
        final EnumFacing face;
        final BlockPos destination;

        TargetKey(BlockPos support, EnumFacing face, BlockPos destination) {
            this.support = support;
            this.face = face;
            this.destination = destination;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TargetKey)) {
                return false;
            }
            TargetKey key = (TargetKey) other;
            return support.equals(key.support)
                    && face == key.face
                    && destination.equals(key.destination);
        }

        @Override
        public int hashCode() {
            return Objects.hash(support, face, destination);
        }
    }

    static final class PreparedPlacement {
        final TargetKey target;
        final Vec3 hitVec;
        final float yaw;
        final float pitch;
        final ReportedPose sourcePose;
        final long sourceMovementSequence;

        PreparedPlacement(
                TargetKey target, Vec3 hitVec,
                float yaw, float pitch,
                ReportedPose sourcePose,
                long sourceMovementSequence) {
            this.target = target;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
            this.sourcePose = sourcePose;
            this.sourceMovementSequence = sourceMovementSequence;
        }

        MovingObjectPosition asHit() {
            return new MovingObjectPosition(
                    hitVec, target.face, target.support);
        }
    }

    static final class PlacementAttempt {
        final PreparedPlacement prepared;
        final long armedTick;
        final C08PacketPlayerBlockPlacement packet;

        private PlacementAttempt(
                PreparedPlacement prepared, long armedTick,
                C08PacketPlayerBlockPlacement packet) {
            this.prepared = prepared;
            this.armedTick = armedTick;
            this.packet = packet;
        }

        static PlacementAttempt armed(
                PreparedPlacement prepared, long tick) {
            return new PlacementAttempt(prepared, tick, null);
        }

        PlacementAttempt bind(C08PacketPlayerBlockPlacement packet) {
            return new PlacementAttempt(prepared, armedTick, packet);
        }
    }

    static final class PostPlaceHold {
        final PreparedPlacement prepared;
        final C08PacketPlayerBlockPlacement packet;
        final long armedTick;
        final boolean c08Dispatched;
        final long c08DispatchOrder;

        private PostPlaceHold(
                PreparedPlacement prepared,
                C08PacketPlayerBlockPlacement packet,
                long armedTick, boolean c08Dispatched,
                long c08DispatchOrder) {
            this.prepared = prepared;
            this.packet = packet;
            this.armedTick = armedTick;
            this.c08Dispatched = c08Dispatched;
            this.c08DispatchOrder = c08DispatchOrder;
        }

        static PostPlaceHold awaiting(
                PreparedPlacement prepared,
                C08PacketPlayerBlockPlacement packet,
                long tick) {
            return new PostPlaceHold(
                    prepared, packet, tick, false, Long.MAX_VALUE);
        }

        PostPlaceHold dispatched(long order) {
            return new PostPlaceHold(
                    prepared, packet, armedTick, true, order);
        }

        boolean isCompatible(ReportedPose pose, float gcd) {
            if (pose == null || !pose.hasRotation) {
                return false;
            }
            float tolerance = Math.max(1.0E-4F, safeGcd(gcd) * 0.51F);
            return Math.abs(MathHelper.wrapAngleTo180_float(
                    pose.yaw - prepared.yaw)) <= tolerance
                    && Math.abs(pose.pitch - prepared.pitch) <= tolerance;
        }
    }

    static final class RotationCommand {
        final float yaw;
        final float pitch;

        RotationCommand(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        static RotationCommand hold(float yaw, float pitch) {
            return new RotationCommand(yaw, pitch);
        }

        boolean reaches(float targetYaw, float targetPitch, float gcd) {
            float tolerance = Math.max(1.0E-4F, safeGcd(gcd) * 0.51F);
            return Math.abs(MathHelper.wrapAngleTo180_float(
                    yaw - targetYaw)) <= tolerance
                    && Math.abs(pitch - targetPitch) <= tolerance;
        }
    }

    static final class PoseOrigin {
        final double x;
        final double y;
        final double z;
        final boolean sneaking;

        PoseOrigin(double x, double y, double z, boolean sneaking) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.sneaking = sneaking;
        }

        Vec3 eye() {
            return new Vec3(x, y + (sneaking ? 1.54D : 1.62D), z);
        }
    }

    private static final class AimSolution {
        final float yaw;
        final float pitch;
        final Vec3 hitVec;
        final double score;

        AimSolution(
                float yaw, float pitch, Vec3 hitVec, double score) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.hitVec = hitVec;
            this.score = score;
        }
    }

}
