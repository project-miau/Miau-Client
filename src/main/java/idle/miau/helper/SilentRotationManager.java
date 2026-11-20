package idle.miau.helper;

import idle.miau.event.EventMovementInput;
import idle.miau.event.EventSilentRotation;
import idle.miau.event.JumpEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.utility.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.function.Consumer;

public final class SilentRotationManager {
    private static final SilentRotationManager INSTANCE = new SilentRotationManager();
    private final Minecraft mc = Minecraft.getMinecraft();
    private float yaw;
    private float previousYaw;
    private float pitch;
    private float previousPitch;
    private boolean modified;
    private boolean blockUserInput;
    private float lastPlaceYawDelta = -1.0F;
    private float yawSpeed;
    private float pitchSpeed;

    private SilentRotationManager() {
    }

    public static SilentRotationManager getInstance() {
        return INSTANCE;
    }

    public void synchronize() {
        if (mc.thePlayer == null) {
            clear();
            return;
        }
        yaw = mc.thePlayer.rotationYaw;
        previousYaw = yaw;
        pitch = mc.thePlayer.rotationPitch;
        previousPitch = pitch;
        modified = false;
        blockUserInput = false;
        lastPlaceYawDelta = -1.0F;
        yawSpeed = 0.0F;
        pitchSpeed = 0.0F;
    }

    public void clear() {
        yaw = 0.0F;
        previousYaw = 0.0F;
        pitch = 0.0F;
        previousPitch = 0.0F;
        modified = false;
        blockUserInput = false;
        lastPlaceYawDelta = -1.0F;
        yawSpeed = 0.0F;
        pitchSpeed = 0.0F;
    }

    public EventSilentRotation.Post update(Consumer<EventSilentRotation> listener) {
        EventSilentRotation rotation = new EventSilentRotation(
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, yawSpeed, pitchSpeed);
        listener.accept(rotation);
        previousYaw = yaw;
        previousPitch = pitch;
        yawSpeed = rotation.getYawSpeed();
        pitchSpeed = rotation.getPitchSpeed();
        blockUserInput = rotation.isBlockUserInput();

        if (!rotation.hasBeenModified() && !modified) {
            yaw = snapYawToMultipleOf360(yaw, mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw = yaw;
            pitch = mc.thePlayer.rotationPitch;
            modified = false;
            return createPost();
        }

        float maxYawDelta = rotation.getMaxYawDelta();
        float gcd = Math.abs(RotationUtils.getGCD());
        float quantizedYawLimit = gcdFlooredLimit(maxYawDelta, gcd);
        float yawDelta = clampYawDelta(
                getYawDelta(rotation.getYaw()), quantizedYawLimit);
        float pitchDelta = getPitchDelta(rotation.getPitch());
        yaw = previousYaw + yawDelta;
        pitch = previousPitch + pitchDelta;
        float[] rotations = RotationUtils.patchGCD(
                new float[]{previousYaw, previousPitch}, new float[]{yaw, pitch});
        yaw = rotations[0];
        pitch = Math.min(90.0F, rotations[1]);

        if (rotation.isPreventDuplicateLook()) {
            float currentDelta = Math.abs(yaw - previousYaw);
            if (currentDelta >= 2.0F) {
                float difference = Math.abs(currentDelta - lastPlaceYawDelta);
                if (difference < 0.002F) {
                    yaw += RotationUtils.getGCD();
                }
            }
        }

        if (!Float.isInfinite(quantizedYawLimit)) {
            yaw = previousYaw + clampYawDelta(
                    yaw - previousYaw, quantizedYawLimit);
        }
        if (rotation.isPreventDuplicateLook() && Math.abs(yaw - previousYaw) >= 2.0F) {
            lastPlaceYawDelta = Math.abs(yaw - previousYaw);
        }

        float remainingYaw = Math.abs(RotationUtils.getYawDifference(
                mc.thePlayer.rotationYaw, yaw));
        float remainingPitch = Math.abs(mc.thePlayer.rotationPitch - pitch);
        float returnTolerance = Math.max(0.0001F, RotationUtils.getGCD() * 0.5F);
        modified = rotation.hasBeenModified()
                || remainingYaw > returnTolerance
                || remainingPitch > returnTolerance;
        if (!modified && !rotation.hasBeenModified()) {
            yaw = snapYawToMultipleOf360(yaw, mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw = yaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        return createPost();
    }

    public void applyMovementInput(EventMovementInput event) {
        applyMovementInput(event, yaw);
    }

    public void applyMovementInput(EventMovementInput event, float movementYaw) {
        if (!idle.miau.module.impl.movement.MovementFix.isActive()
                || !modified
                || event.getSpeed() == 0.0F && event.getStrafe() == 0.0F) {
            return;
        }
        ResolvedInput resolved = resolveMovementInput(
                mc.thePlayer.rotationYaw, movementYaw,
                event.getSpeed(), event.getStrafe(), mc.thePlayer.isSneaking());
        event.setSpeed(resolved.forward);
        event.setStrafe(resolved.strafe);
    }

    public static ResolvedInput resolveMovementInput(
            float cameraYaw, float movementYaw,
            float forwardInput, float strafeInput, boolean sneaking) {
        if (forwardInput == 0.0F && strafeInput == 0.0F) {
            return new ResolvedInput(0.0F, 0.0F);
        }
        float moveAngle = wrapAngleToPi(
                (float) Math.toRadians(MathHelper.wrapAngleTo180_float(cameraYaw))
                        + (float) Math.atan2(-strafeInput, forwardInput));
        float keyAngle = wrapAngleToPi(
                moveAngle - (float) Math.toRadians(movementYaw));
        float forward = keyAngle >= -Math.PI * 3.0F / 8.0F
                && keyAngle <= Math.PI * 3.0F / 8.0F
                ? 1.0F : keyAngle <= -Math.PI * 5.0F / 8.0F
                || keyAngle >= Math.PI * 5.0F / 8.0F ? -1.0F : 0.0F;
        float strafe = keyAngle >= Math.PI / 8.0F
                && keyAngle <= Math.PI * 7.0F / 8.0F
                ? -1.0F : keyAngle <= -Math.PI / 8.0F
                && keyAngle >= -Math.PI * 7.0F / 8.0F ? 1.0F : 0.0F;
        if (sneaking) {
            forward *= 0.3F;
            strafe *= 0.3F;
        }
        return new ResolvedInput(forward, strafe);
    }

    public void applyStrafe(StrafeEvent event) {
        applyStrafe(event, yaw);
    }

    public void applyStrafe(StrafeEvent event, float movementYaw) {
        if (idle.miau.module.impl.movement.MovementFix.isActive()
                && modified) {
            event.setYaw(movementYaw);
        }
    }

    public void applyJump(JumpEvent event) {
        applyJump(event, yaw);
    }

    public void applyJump(JumpEvent event, float movementYaw) {
        if (idle.miau.module.impl.movement.MovementFix.isActive()
                && modified) {
            event.setYaw(movementYaw);
        }
    }

    public boolean isBlockingUserInput() {
        return blockUserInput && mc.thePlayer != null;
    }

    public boolean isModified() {
        return modified;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPreviousYaw() {
        return previousYaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getPreviousPitch() {
        return previousPitch;
    }

    public void synchronizeEmitted(float emittedYaw, float emittedPitch) {
        if (!Float.isNaN(emittedYaw) && !Float.isInfinite(emittedYaw)) {
            yaw = emittedYaw;
        }
        if (!Float.isNaN(emittedPitch) && !Float.isInfinite(emittedPitch)) {
            pitch = emittedPitch;
        }
    }

    public float getSpeed() {
        return Math.max(yawSpeed, pitchSpeed);
    }

    public float getYawSpeed() {
        return yawSpeed;
    }

    public float getPitchSpeed() {
        return pitchSpeed;
    }

    private EventSilentRotation.Post createPost() {
        return new EventSilentRotation.Post(
                yaw, pitch, previousYaw, previousPitch, modified, yawSpeed, pitchSpeed);
    }

    public static float wrapAngleToPi(float value) {
        float twoPi = (float) (Math.PI * 2.0D);
        value = (value + (float) Math.PI) % twoPi;
        if (value < 0.0F) {
            value += twoPi;
        }
        return value - (float) Math.PI;
    }

    private float getYawDelta(float targetYaw) {
        float delta = MathHelper.wrapAngleTo180_float(
                MathHelper.wrapAngleTo180_float(targetYaw)
                        - MathHelper.wrapAngleTo180_float(previousYaw));
        float speedValue = (float) (yawSpeed
                * (Math.sin(Math.toRadians(Math.abs(delta))) / 2.0F + 0.5F));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - previousPitch;
        float speedValue = (float) (pitchSpeed
                * (Math.sin(Math.toRadians(Math.abs(delta))) / 2.0F + 0.5F));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    public static float clampYawDelta(float delta, float maxYawDelta) {
        if (Float.isNaN(maxYawDelta) || Float.isInfinite(maxYawDelta)) {
            return delta;
        }
        float limit = Math.max(0.0F, maxYawDelta);
        return Math.max(-limit, Math.min(limit, delta));
    }

    public static float gcdFlooredLimit(float maxYawDelta, float gcd) {
        if (Float.isNaN(maxYawDelta) || Float.isInfinite(maxYawDelta)) {
            return Float.POSITIVE_INFINITY;
        }
        float limit = Math.max(0.0F, maxYawDelta);
        float step = Math.abs(gcd);
        if (Float.isNaN(step) || Float.isInfinite(step) || step <= 0.0F) {
            return limit;
        }
        return (float) Math.floor((double) limit / (double) step) * step;
    }

    private float snapYawToMultipleOf360(float currentYaw, float targetYaw) {
        float difference = currentYaw - targetYaw;
        float turns = Math.round(difference / 360.0F);
        return targetYaw + turns * 360.0F;
    }

    public static final class ResolvedInput {
        public final float forward;
        public final float strafe;

        public ResolvedInput(float forward, float strafe) {
            this.forward = forward;
            this.strafe = strafe;
        }
    }
}
