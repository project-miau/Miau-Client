package idle.miau.event;

import idle.miau.mcp.event.Event;

import java.util.Objects;

public class ClientRotationEvent extends Event {
    public Float yaw;
    public Float pitch;
    public boolean scriptRotations;
    private float maxYawDelta = Float.POSITIVE_INFINITY;

    private final Float initialYaw;
    private final Float initialPitch;
    private Float trackedYaw;
    private Float trackedPitch;
    private boolean rotationClaimed;
    private Object rotationOwner;
    private int rotationPriority = Integer.MIN_VALUE;
    private boolean serverRelativeMovementInputs;
    private boolean rotationAlreadyQuantized;
    private Boolean movementYawCorrection;

    public ClientRotationEvent(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.initialYaw = yaw;
        this.initialPitch = pitch;
        this.trackedYaw = yaw;
        this.trackedPitch = pitch;
    }

    public void setYaw(Float yaw) {
        this.yaw = yaw;
        claimLegacyRotation();
    }

    public void setPitch(Float pitch) {
        this.pitch = pitch;
        claimLegacyRotation();
    }

    public void setRotation(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        claimLegacyRotation();
    }

    public boolean canClaimRotation(Object owner, int priority) {
        Objects.requireNonNull(owner, "owner");
        synchronizeLegacyRotation();
        return !rotationClaimed
                || rotationOwner == null
                || rotationOwner == owner
                || priority > rotationPriority;
    }

    public boolean trySetRotation(Object owner, int priority, Float yaw, Float pitch) {
        return trySetRotation(owner, priority, yaw, pitch, false);
    }

    public boolean trySetRotation(
            Object owner,
            int priority,
            Float yaw,
            Float pitch,
            boolean serverRelativeMovementInputs
    ) {
        if (!canClaimRotation(owner, priority)) {
            return false;
        }
        boolean sameOwner = rotationOwner == owner;
        this.yaw = yaw;
        this.pitch = pitch;
        this.trackedYaw = yaw;
        this.trackedPitch = pitch;
        this.rotationClaimed = true;
        this.rotationOwner = owner;
        this.rotationPriority = sameOwner
                ? Math.max(rotationPriority, priority)
                : priority;
        this.serverRelativeMovementInputs = serverRelativeMovementInputs;
        this.rotationAlreadyQuantized = false;
        this.movementYawCorrection = null;
        this.maxYawDelta = Float.POSITIVE_INFINITY;
        return true;
    }

    public void setRotationAlreadyQuantized(boolean rotationAlreadyQuantized) {
        synchronizeLegacyRotation();
        this.rotationAlreadyQuantized = rotationAlreadyQuantized
                && rotationClaimed && rotationOwner != null;
    }

    public boolean isRotationAlreadyQuantized() {
        synchronizeLegacyRotation();
        return rotationAlreadyQuantized;
    }

    public void setMaxYawDelta(float maxYawDelta) {
        this.maxYawDelta = Float.isNaN(maxYawDelta)
                ? Float.POSITIVE_INFINITY
                : Math.max(0.0F, maxYawDelta);
    }

    public float getMaxYawDelta() {
        return maxYawDelta;
    }

    public Float getInitialYaw() {
        return initialYaw;
    }

    public Float getInitialPitch() {
        return initialPitch;
    }

    public Object getRotationOwner() {
        synchronizeLegacyRotation();
        return rotationOwner;
    }

    public boolean isRotationOwner(Object owner) {
        return getRotationOwner() == owner;
    }

    public boolean usesServerRelativeMovementInputs() {
        synchronizeLegacyRotation();
        return serverRelativeMovementInputs;
    }

    public void setMovementYawCorrection(boolean enabled) {
        synchronizeLegacyRotation();
        if (rotationClaimed && rotationOwner != null) {
            movementYawCorrection = enabled;
        }
    }

    public Boolean getMovementYawCorrection() {
        synchronizeLegacyRotation();
        return movementYawCorrection;
    }

    public boolean isRotationClaimed() {
        synchronizeLegacyRotation();
        return rotationClaimed;
    }

    private void synchronizeLegacyRotation() {
        if (!Objects.equals(trackedYaw, yaw) || !Objects.equals(trackedPitch, pitch)) {
            claimLegacyRotation();
        }
    }

    private void claimLegacyRotation() {
        trackedYaw = yaw;
        trackedPitch = pitch;
        rotationClaimed = true;
        rotationOwner = null;
        rotationPriority = Integer.MAX_VALUE;
        serverRelativeMovementInputs = false;
        rotationAlreadyQuantized = false;
        movementYawCorrection = null;
    }
}
