package idle.miau.module.impl.player;

import idle.miau.event.PrePlayerInputEvent;
import idle.miau.utility.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;

final class ScaffoldSneakSafety {
    static final double SAFE_DELTA = 0.268D;
    private static final double AIR_DRAG = (double) 0.91F;
    private static final double POSITION_THRESHOLD = 0.031D;
    static final int MAX_COAST_TICKS = 3;
    static final double PREPARATION_MOTION_Y = 0.17D;
    private int coastTicks;
    private boolean coastingInput;
    private boolean ownedPulse, holdingRelease;

    private boolean positionKnown;
    private double lastX, lastY, lastZ;
    private boolean pendingSneakAction;
    private boolean packetSneaking;
    private boolean pendingMismatch;
    private String pendingActions = "";
    private String pendingContext = "";
    private String inputSource = "unattributed";
    private boolean inputSneak;
    private String lastC08Face = "unknown";

    synchronized void reset() {
        positionKnown = false;
        pendingSneakAction = false;
        packetSneaking = false;
        pendingMismatch = false;
        pendingActions = "";
        pendingContext = "";
        inputSource = "unattributed";
        inputSneak = false;
        lastC08Face = "unknown";
        coastTicks = 0;
        coastingInput = false;
        ownedPulse = holdingRelease = false;
    }

    synchronized void beginInput(String source, boolean sneak) {
        beginInput(source, sneak, false);
    }

    synchronized void beginInput(String source, boolean sneak, boolean onGround) {
        inputSource = source;
        inputSneak = sneak;
        coastingInput = false;
        holdingRelease = false;
        if (onGround || sneak) {
            coastTicks = 0;
        }
    }

    synchronized boolean isCoastingInput() {
        return coastingInput;
    }

    synchronized boolean isHoldingRelease() {
        return holdingRelease;
    }

    synchronized void continueOwnedPulse(EntityPlayerSP player, WorldClient world, PrePlayerInputEvent input,
                                         boolean physicalSneak) {
        if (!ownedPulse || input == null || player == null) return;
        if (!player.isSneaking() && !packetSneaking && !pendingSneakAction) {
            ownedPulse = false;
            return;
        }
        if (physicalSneak) {
            ownedPulse = false;
            return;
        }
        if (input.isSneak()) {
            holdingRelease = true;
            return;
        }
        double offset = positionKnown
                ? Math.hypot(player.posX - lastX, player.posZ - lastZ) : Double.NaN;
        double offsetY = positionKnown ? player.posY - lastY : Double.NaN;
        boolean movingInput = input.getForward() != 0 || input.getStrafe() != 0;
        boolean safe = !pendingSneakAction && positionKnown
                && offset <= POSITION_THRESHOLD && Math.abs(offsetY) <= POSITION_THRESHOLD
                && releaseFits(player.motionX, player.motionY, player.motionZ,
                player.jumpMovementFactor, offset, offsetY, player.onGround,
                movingInput, input.isJump(), player.getPositionUpdateTicks() >= 20);
        if (safe && player.getPositionUpdateTicks() < 20) {
            double travel = Math.hypot(player.motionX, player.motionZ)
                    + Math.max(0.026D, player.jumpMovementFactor);
            double y = Math.abs(player.motionY) < 0.005D ? 0 : player.motionY;
            safe = world != null && player.getEntityBoundingBox() != null
                    && world.getCollidingBoundingBoxes(player, player.getEntityBoundingBox()
                    .addCoord(0, y, 0).expand(travel, 0.001D, travel)).isEmpty();
        }
        if (safe) {
            noteInput("Telly/optional release", false);
            return;
        }
        input.setSneak(true);
        holdingRelease = true;
        noteInput("Telly/deferred optional release", true);
    }

    static boolean releaseFits(double x, double y, double z, float factor,
                               double offset, double offsetY, boolean ground,
                               boolean movingInput, boolean jump, boolean heartbeat) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(factor) || factor < 0
                || !Double.isFinite(offset) || !Double.isFinite(offsetY)) return false;
        if (ground && (movingInput || jump)) return false;
        double acceleration = Math.max(0.026D, factor);
        double speed = Math.hypot(x, z);
        if (speed + acceleration + offset > SAFE_DELTA) return false;
        double vertical = ground ? 0 : Math.abs(y) < 0.005D ? 0 : y;
        return heartbeat || speed - acceleration - offset > POSITION_THRESHOLD
                || Math.abs(vertical + offsetY) > POSITION_THRESHOLD;
    }

    synchronized void noteInput(String source, boolean sneak) {
        inputSource = source;
        inputSneak = sneak;
    }

    synchronized void ownSafetySneak(String source) {
        ownedPulse = true;
        noteInput(source, true);
    }

    synchronized boolean tryPulse(EntityPlayerSP player, WorldClient world,
                     PrePlayerInputEvent input, String source) {
        coastingInput = false;
        if (input == null || input.isSneak() || player == null || world == null
                || !positionKnown || pendingSneakAction || packetSneaking
                || player.isSneaking() || player.getServerSneakState()
                || player.movementInput != null && player.movementInput.sneak
                || player.onGround || player.hurtTime > 0 || player.isRiding()
                || player.isInWater() || player.isInLava()
                || player.capabilities == null || player.capabilities.isFlying || player.noClip) {
            return false;
        }
        if (!Double.isFinite(player.posX) || !Double.isFinite(player.posY)
                || !Double.isFinite(player.posZ)) {
            return false;
        }
        double offset = Math.hypot(player.posX - lastX, player.posZ - lastZ);
        double offsetY = player.posY - lastY;
        if (!Double.isFinite(offset) || !Double.isFinite(offsetY)
                || offset > POSITION_THRESHOLD || Math.abs(offsetY) > POSITION_THRESHOLD
                || !Double.isFinite(input.getSneakSlowDownMultiplier())
                || input.getSneakSlowDownMultiplier() < 0.0D
                || input.getSneakSlowDownMultiplier() > 0.3D
                || !Float.isFinite(input.getForward()) || !Float.isFinite(input.getStrafe())
                || input.getForward() == 0.0F && input.getStrafe() == 0.0F) {
            return false;
        }
        Preparation plan = preparation(player.motionX, player.motionY,
                player.motionZ, player.jumpMovementFactor,
                offset, offsetY, MAX_COAST_TICKS - coastTicks);
        if (plan == null) {
            return false;
        }
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB sweep = new AxisAlignedBB(box.minX, box.minY + plan.minY, box.minZ,
                box.maxX, box.maxY + plan.maxY, box.maxZ)
                .expand(plan.travel, 0.001D, plan.travel);
        if (!world.getCollidingBoundingBoxes(player, sweep).isEmpty()) {
            return false;
        }
        if (plan.coastTicks > 0) {
            coastTicks++;
            coastingInput = true;
            input.setForward(0.0F);
            input.setStrafe(0.0F);
            noteInput(source + "/prepare", false);
            return false;
        }
        input.setSneak(true);
        ownedPulse = true;
        noteInput(source, true);
        return true;
    }

    static PulseBounds pulseBounds(double motionX, double motionY,
                                   double motionZ, float airFactor) {
        return pulseBounds(motionX, motionY, motionZ, airFactor, 0.0D, 0.0D);
    }

    private static PulseBounds pulseBounds(double motionX, double motionY,
                                          double motionZ, float airFactor,
                                          double offset, double offsetY) {
        if (!Double.isFinite(motionX) || !Double.isFinite(motionY)
                || !Double.isFinite(motionZ) || !Float.isFinite(airFactor)
                || motionY > 0.0D || airFactor < 0.0F) {
            return null;
        }
        double speed = Math.hypot(motionX, motionZ);
        double acceleration = Math.max(0.026D, airFactor);
        double sneakAcceleration = acceleration * 0.3D * 0.98D * Math.sqrt(2.0D);
        double start = speed + sneakAcceleration;
        double stop = start * AIR_DRAG + acceleration;
        if (start + offset > SAFE_DELTA || stop > SAFE_DELTA) {
            return null;
        }
        double y = Math.abs(motionY) < 0.005D ? 0.0D : motionY;
        double nextY = (y - 0.08D) * 0.9800000190734863D;
        double minimumStart = Math.max(0.0D, speed - sneakAcceleration);
        double minimumStop = Math.max(0.0D, minimumStart * AIR_DRAG - acceleration);
        if (minimumStart - offset <= POSITION_THRESHOLD && Math.abs(y + offsetY) <= POSITION_THRESHOLD
                || minimumStop <= POSITION_THRESHOLD && Math.abs(nextY) <= POSITION_THRESHOLD) {
            return null;
        }
        return new PulseBounds(start + offset, stop, y + nextY);
    }

    static Preparation preparation(double motionX, double motionY, double motionZ,
                                   float airFactor, double offset, double offsetY, int budget) {
        if (!Double.isFinite(motionX) || !Double.isFinite(motionY)
                || !Double.isFinite(motionZ) || !Float.isFinite(airFactor)
                || airFactor < 0 || motionY > PREPARATION_MOTION_Y
                || !Double.isFinite(offset) || offset < 0
                || !Double.isFinite(offsetY)) {
            return null;
        }
        double x = motionX, z = motionZ, y = motionY;
        double height = 0, minY = 0, maxY = 0, travel = 0;
        for (int ticks = 0; ticks <= Math.min(MAX_COAST_TICKS, budget); ticks++) {
            PulseBounds pulse = pulseBounds(x, y, z, airFactor,
                    ticks == 0 ? offset : 0, ticks == 0 ? offsetY : 0);
            if (pulse != null) {
                return new Preparation(ticks, Math.min(minY, height + pulse.drop), maxY,
                        travel + pulse.travel);
            }
            if (Math.hypot(x, z) <= SAFE_DELTA - Math.max(0.026D, airFactor)) {
                return null;
            }
            travel += Math.hypot(x, z);
            y = Math.abs(y) < 0.005D ? 0.0D : y;
            height += y;
            minY = Math.min(minY, height);
            maxY = Math.max(maxY, height);
            x *= AIR_DRAG;
            z *= AIR_DRAG;
            y = (y - 0.08D) * 0.9800000190734863D;
        }
        return null;
    }

    synchronized Transition onPacketDispatched(Packet<?> packet, String context) {
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            int direction = ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection();
            if (direction >= 0 && direction < 6) {
                lastC08Face = EnumFacing.getFront(direction).name();
            }
            return null;
        }
        if (packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction.Action action = ((C0BPacketEntityAction) packet).getAction();
            if (action == C0BPacketEntityAction.Action.START_SNEAKING
                    || action == C0BPacketEntityAction.Action.STOP_SNEAKING) {
                String label = action == C0BPacketEntityAction.Action.START_SNEAKING ? "START" : "STOP";
                packetSneaking = action == C0BPacketEntityAction.Action.START_SNEAKING;
                if (!packetSneaking) ownedPulse = false;
                pendingActions = pendingSneakAction
                        ? (pendingActions.length() < 80 ? pendingActions + "+" + label : pendingActions)
                        : label;
                pendingMismatch |= (action == C0BPacketEntityAction.Action.START_SNEAKING) != inputSneak;
                String attribution = pendingMismatch
                        ? "packet/input mismatch; " : "";
                pendingContext = attribution + inputSource + "; lastC08Face=" + lastC08Face + "; " + context;
                pendingSneakAction = true;
            }
            return null;
        }
        if (!(packet instanceof C03PacketPlayer)) {
            return null;
        }
        if (!((C03PacketPlayer) packet).isMoving()) return null;
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        double delta = positionKnown
                ? Math.hypot(movement.getPositionX() - lastX, movement.getPositionZ() - lastZ)
                : Double.NaN;
        Transition transition = pendingSneakAction
                ? new Transition(pendingActions, pendingContext, delta) : null;
        lastX = movement.getPositionX();
        lastY = movement.getPositionY();
        lastZ = movement.getPositionZ();
        positionKnown = Double.isFinite(lastX) && Double.isFinite(lastY) && Double.isFinite(lastZ);
        pendingSneakAction = false;
        pendingMismatch = false;
        pendingActions = "";
        pendingContext = "";
        return transition;
    }

    void observe(Packet<?> packet, String context) {
        Transition transition = onPacketDispatched(packet, context);
        if (transition != null && (!Double.isFinite(transition.deltaXZ)
                || transition.deltaXZ > SAFE_DELTA)) {
            Utils.log.warn("[ScaffoldSneak] actions={} deltaXZ={} context={}",
                    transition.actions, transition.deltaXZ, transition.context);
        }
    }

    static final class Preparation {
        final int coastTicks;
        final double minY, maxY, travel;

        Preparation(int coastTicks, double minY, double maxY, double travel) {
            this.coastTicks = coastTicks;
            this.minY = minY;
            this.maxY = maxY;
            this.travel = travel;
        }
    }

    static final class PulseBounds {
        final double startDelta, stopDelta, drop, travel;

        PulseBounds(double startDelta, double stopDelta, double drop) {
            this.startDelta = startDelta;
            this.stopDelta = stopDelta;
            this.drop = drop;
            this.travel = startDelta + stopDelta;
        }
    }

    static final class Transition {
        final String actions, context;
        final double deltaXZ;

        Transition(String actions, String context, double deltaXZ) {
            this.actions = actions;
            this.context = context;
            this.deltaXZ = deltaXZ;
        }
    }
}
