package idle.miau.helper;

import idle.miau.event.*;
import idle.miau.module.impl.movement.MovementFix;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;

import java.util.Random;

public class RotationHelper {

    private static RotationHelper INSTANCE = new RotationHelper();

    private Float serverYaw = null;
    private Float serverPitch = null;
    private Object rotationOwner;

    private boolean setRotations = false;
    public boolean forceMovementFix = false;

    private boolean serverRelativeMovementInputs = false;
    private Boolean movementYawCorrection;

    private float savedYaw, savedPitch;
    private float savedPrevYaw, savedPrevPitch;
    public boolean swappedForMouseOver;

    private boolean rotationsUpdatedThisTick = false;

    private boolean needsArmYawUpdate = false;

    private Minecraft mc = Minecraft.getMinecraft();

    private final Random rotationRandom = new Random();
    private Entity adaptiveTarget;
    private boolean adaptiveSilentPath;
    private boolean adaptivePathInitialized;
    private int adaptiveLastTick = Integer.MIN_VALUE;
    private int adaptiveLastSpeed = Integer.MIN_VALUE;
    private int humanizationLastTick = Integer.MIN_VALUE;
    private float pendingYawSteps;
    private float pendingPitchSteps;
    private float yawIntegral;
    private float pitchIntegral;
    private float previousYawStep;
    private float previousPitchStep;
    private float yawProportionalScale = 1.0f;
    private float pitchProportionalScale = 1.0f;
    private float yawIntegralScale = 1.0f;
    private float pitchIntegralScale = 1.0f;
    private float controllerSpeedMultiplier = 1.0f;
    private boolean controllerHasOutput;
    private float lastControllerYaw;
    private float lastControllerPitch;
    private float speedMultiplier = 1.0f;
    private float targetSpeedMultiplier = 1.0f;
    private long nextHumanizationRetarget;
    private long nextControllerSpeedRetarget;
    private double jitterX;
    private double jitterY;
    private double jitterZ;
    private double targetJitterX;
    private double targetJitterY;
    private double targetJitterZ;

    public static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, float smoothingFactor) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
        if (rot == null) return null;
        float factor = Math.max(1f, smoothingFactor);
        float yaw = baseYaw + MathHelper.wrapAngleTo180_float(rot[0] - baseYaw) / factor;
        float pitch = basePitch + (rot[1] - basePitch) / factor;
        return new float[] { yaw, pitch };
    }

    public float[] getRotationsToTarget(Entity target, float smoothingFactor) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        float[] rot = RotationUtils.getRotations(target, baseYaw, basePitch);
        if (rot == null) return null;
        float factor = Math.max(1f, smoothingFactor);
        float yaw = baseYaw + MathHelper.wrapAngleTo180_float(rot[0] - baseYaw) / factor;
        float pitch = basePitch + (rot[1] - basePitch) / factor;
        return new float[] { yaw, pitch };
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed) {
        return getRotationsToTarget(target, e, speed, 0.0, 0.0, 0f);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent) {
        return getRotationsToTarget(target, e, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range) {
        return getRotationsToTarget(target, e, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, useBackupPoints, range, false, true);
    }

    public float[] getRotationsToTarget(Entity target, ClientRotationEvent e, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        return getAdaptiveRotationsToTarget(target, baseYaw, basePitch, speed, horizontalMultipoint,
                verticalMultipoint, randomizationPercent, useBackupPoints, range,
                allowThroughBlocks, allowThroughEntities, true);
    }

    public float[] getRotationsToTarget(Entity target, int speed) {
        return getRotationsToTarget(target, speed, 0.0, 0.0, 0f);
    }

    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent) {
        return getRotationsToTarget(target, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, false, 10.0);
    }

    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range) {
        return getRotationsToTarget(target, speed, horizontalMultipoint, verticalMultipoint, randomizationPercent, useBackupPoints, range, false, true);
    }

    public float[] getRotationsToTarget(Entity target, int speed, double horizontalMultipoint, double verticalMultipoint, float randomizationPercent, boolean useBackupPoints, double range, boolean allowThroughBlocks, boolean allowThroughEntities) {
        if (target == null || mc.thePlayer == null) return null;
        float baseYaw = mc.thePlayer.rotationYaw;
        float basePitch = mc.thePlayer.rotationPitch;
        return getAdaptiveRotationsToTarget(target, baseYaw, basePitch, speed, horizontalMultipoint,
                verticalMultipoint, randomizationPercent, useBackupPoints, range,
                allowThroughBlocks, allowThroughEntities, false);
    }

    private float[] getAdaptiveRotationsToTarget(Entity target, float baseYaw, float basePitch, int speed,
                                                  double horizontalMultipoint, double verticalMultipoint,
                                                  float randomizationPercent, boolean useBackupPoints, double range,
                                                  boolean allowThroughBlocks, boolean allowThroughEntities,
                                                  boolean silentPath) {
        prepareAdaptiveState(target, silentPath, speed);

        Vec3 aimPoint = useBackupPoints
                ? RotationUtils.getAimPointWithBackup(target, horizontalMultipoint, verticalMultipoint, range,
                allowThroughBlocks, allowThroughEntities)
                : RotationUtils.getAimPoint(target, horizontalMultipoint, verticalMultipoint);
        if (aimPoint == null) {
            resetControllerMotion();
            return null;
        }

        aimPoint = humanizeAimPoint(target, aimPoint, randomizationPercent, range,
                useBackupPoints, allowThroughBlocks, allowThroughEntities);
        float[] targetRotations = RotationUtils.getRotationsToPoint(
                aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord, baseYaw, basePitch);
        return applyVapeRotationController(target, aimPoint, baseYaw, basePitch,
                targetRotations[0], targetRotations[1], speed);
    }

    private void prepareAdaptiveState(Entity target, boolean silentPath, int speed) {
        int tick = mc.thePlayer.ticksExisted;
        if (this.adaptiveTarget != target || !this.adaptivePathInitialized
                || this.adaptiveSilentPath != silentPath || this.adaptiveLastTick == Integer.MIN_VALUE
                || tick < this.adaptiveLastTick || tick - this.adaptiveLastTick > 1
                || this.adaptiveLastSpeed != speed) {
            this.adaptiveTarget = target;
            this.adaptiveSilentPath = silentPath;
            this.adaptivePathInitialized = true;
            this.adaptiveLastSpeed = speed;
            this.pendingYawSteps = 0.0f;
            this.pendingPitchSteps = 0.0f;
            this.yawIntegral = 0.0f;
            this.pitchIntegral = 0.0f;
            this.previousYawStep = 0.0f;
            this.previousPitchStep = 0.0f;
            this.yawProportionalScale = 0.8f + this.rotationRandom.nextFloat() * 0.4f;
            this.pitchProportionalScale = 0.85f + this.rotationRandom.nextFloat() * 0.3f;
            this.yawIntegralScale = 0.85f + this.rotationRandom.nextFloat() * 0.3f;
            this.pitchIntegralScale = 0.85f + this.rotationRandom.nextFloat() * 0.3f;
            this.controllerSpeedMultiplier = sampleVapeSpeedMultiplier();
            this.nextControllerSpeedRetarget = 0L;
            this.controllerHasOutput = false;
            this.speedMultiplier = 1.0f;
            this.targetSpeedMultiplier = 1.0f;
            this.nextHumanizationRetarget = 0L;
            this.humanizationLastTick = Integer.MIN_VALUE;
            this.jitterX = this.jitterY = this.jitterZ = 0.0;
            this.targetJitterX = this.targetJitterY = this.targetJitterZ = 0.0;
        }
        this.adaptiveLastTick = tick;
    }

    private Vec3 humanizeAimPoint(Entity target, Vec3 aimPoint, float randomizationPercent, double range,
                                  boolean validatePoint, boolean allowThroughBlocks, boolean allowThroughEntities) {
        float amount = MathHelper.clamp_float(randomizationPercent / 100.0f, 0.0f, 1.0f);
        updateHumanization(amount);
        if (amount <= 0.001f) {
            return aimPoint;
        }

        double motionX = target.posX - target.lastTickPosX;
        double motionZ = target.posZ - target.lastTickPosZ;
        double motionScale = 1.0 + Math.min(0.5, Math.sqrt(motionX * motionX + motionZ * motionZ));
        float border = target.getCollisionBorderSize();
        AxisAlignedBB box = target.getEntityBoundingBox().expand(border, border, border);
        double x = clampToBox(aimPoint.xCoord + this.jitterX * motionScale, box.minX, box.maxX);
        double y = clampToBox(aimPoint.yCoord + this.jitterY, box.minY, box.maxY);
        double z = clampToBox(aimPoint.zCoord + this.jitterZ * motionScale, box.minZ, box.maxZ);
        Vec3 candidate = new Vec3(x, y, z);

        if (validatePoint) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
            if (!RotationUtils.canAimAtPoint(eye, candidate, target, range,
                    allowThroughBlocks, allowThroughEntities)) {
                return aimPoint;
            }
        }
        return candidate;
    }

    private void updateHumanization(float amount) {
        int tick = mc.thePlayer.ticksExisted;
        if (this.humanizationLastTick == tick) {
            return;
        }
        this.humanizationLastTick = tick;

        long now = System.currentTimeMillis();
        if (now >= this.nextHumanizationRetarget) {
            this.nextHumanizationRetarget = now + 100L + this.rotationRandom.nextInt(901);
            this.targetJitterX = randomRange(-0.15, 0.15) * amount;
            this.targetJitterY = randomRange(-0.30, 0.25) * amount;
            this.targetJitterZ = randomRange(-0.15, 0.15) * amount;
            float speedRange = 0.6f * amount;
            this.targetSpeedMultiplier = 1.0f - speedRange / 2.0f
                    + this.rotationRandom.nextFloat() * speedRange;
        }

        double jitterStep = (0.01 + this.rotationRandom.nextDouble() * 0.05) * Math.max(amount, 0.05f);
        this.jitterX = approach(this.jitterX, this.targetJitterX, jitterStep);
        this.jitterY = approach(this.jitterY, this.targetJitterY, jitterStep);
        this.jitterZ = approach(this.jitterZ, this.targetJitterZ, jitterStep);
        this.speedMultiplier = (float) approach(this.speedMultiplier, this.targetSpeedMultiplier, 0.04);
    }

    private float[] applyVapeRotationController(Entity target, Vec3 aimPoint,
                                                 float baseYaw, float basePitch,
                                                 float targetYaw, float targetPitch,
                                                 int speed) {
        if (speed <= 0) {
            resetControllerMotion();
            return new float[]{baseYaw, RotationUtils.clampPitch(basePitch)};
        }

        float mouseStep = (float) RotationUtils.getSensitivityMultiplier();
        if (mouseStep <= 0.0f) {
            return RotationUtils.smoothRotation(baseYaw, basePitch, targetYaw, targetPitch, speed);
        }

        if (this.controllerHasOutput
                && (Math.abs(MathHelper.wrapAngleTo180_float(baseYaw - this.lastControllerYaw))
                > Math.max(0.5f, mouseStep * 2.0f)
                || Math.abs(basePitch - this.lastControllerPitch)
                > Math.max(0.5f, mouseStep * 2.0f))) {
            resetControllerMotion();
        }

        float yawError = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float pitchError = targetPitch - basePitch;
        float previousYaw = this.previousYawStep;
        float previousPitch = this.previousPitchStep;

        double motionX = target.posX - target.lastTickPosX;
        double motionZ = target.posZ - target.lastTickPosZ;
        double horizontalTargetMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double playerHorizontalSpeed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX
                + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        double distanceToTarget = distanceFromEye(aimPoint);

        float pitchProportionalGain = 0.45f * this.pitchProportionalScale;
        float pitchIntegralGain = 0.91f * this.pitchIntegralScale;
        float yawProportionalGain = (Math.abs(yawError) < 20.0f ? 0.05f : 0.1f)
                * this.yawProportionalScale;
        float yawIntegralGain = 0.33f * this.yawIntegralScale;

        if (Math.abs(mc.thePlayer.motionY) > 0.1D) {
            pitchError *= 1.0f + this.rotationRandom.nextFloat() * 0.32f;
        }

        boolean yawContinuingInSameDirection = Math.signum(yawError) == Math.signum(previousYaw);
        if (yawContinuingInSameDirection && Math.abs(yawError) < 20.0f) {
            yawProportionalGain *= 2.5f;
            previousYaw *= (float) (1.0D + Math.min(horizontalTargetMotion + playerHorizontalSpeed, 0.25D));
        }

        if (distanceToTarget < 0.8D) {
            double distanceScale = distanceToTarget / 0.8D;
            pitchError *= (float) (distanceScale * distanceScale);
            yawError *= (float) distanceScale;
        }

        float integrationStep = 0.05f;
        float pitchControlError = pitchError - previousPitch
                + previousYaw * integrationStep * (this.rotationRandom.nextBoolean() ? -1.0f : 1.0f);
        float yawControlError = yawError - previousYaw;
        this.pitchIntegral = clampIntegral(this.pitchIntegral + pitchControlError * integrationStep, 45.0f);
        this.yawIntegral = clampIntegral(this.yawIntegral + yawControlError * integrationStep, 60.0f);

        float pitchAdjustment = pitchProportionalGain * pitchControlError
                + pitchIntegralGain * this.pitchIntegral;
        float yawAdjustment = yawProportionalGain * yawControlError
                + yawIntegralGain * this.yawIntegral;

        if (Math.abs(yawError) > 120.0f) {
            this.yawIntegral = 0.0f;
            yawAdjustment = 0.0f;
        }
        if (mc.currentScreen != null) {
            this.yawIntegral = 0.0f;
            this.pitchIntegral = 0.0f;
        }

        float controlYaw = baseYaw + yawError + yawAdjustment / 3.0f;
        float controlPitch = basePitch + pitchAdjustment;

        updateControllerSpeedMultiplier();
        float aimSpeed = mapRavenSpeedToVape(speed);
        float effectiveSpeed = aimSpeed * this.controllerSpeedMultiplier * this.speedMultiplier;
        float sensitivityBase = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float mouseScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float sensitivityTimeScale = mouseScale <= 0.0f ? 1.0f : 1.0f / mouseScale;
        float controllerStep = effectiveSpeed * 0.25f * 50.0f * sensitivityTimeScale;

        float controlYawError = MathHelper.wrapAngleTo180_float(controlYaw - baseYaw);
        float controlPitchError = controlPitch - basePitch;
        this.pendingYawSteps = addControllerSteps(this.pendingYawSteps, controlYawError,
                controllerStep, mouseStep);
        this.pendingPitchSteps = addControllerSteps(this.pendingPitchSteps, controlPitchError,
                controllerStep, mouseStep);

        int yawSteps = (int) this.pendingYawSteps;
        int pitchSteps = (int) this.pendingPitchSteps;
        this.pendingYawSteps -= yawSteps;
        this.pendingPitchSteps -= pitchSteps;

        float yaw = RotationUtils.unwrapYaw(baseYaw + yawSteps * mouseStep, baseYaw);
        float pitch = RotationUtils.clampPitch(basePitch + pitchSteps * mouseStep);
        this.previousYawStep = MathHelper.wrapAngleTo180_float(yaw - baseYaw);
        this.previousPitchStep = pitch - basePitch;
        this.lastControllerYaw = yaw;
        this.lastControllerPitch = pitch;
        this.controllerHasOutput = true;
        return new float[]{yaw, pitch};
    }

    private float addControllerSteps(float pendingSteps, float error,
                                     float controllerStep, float mouseStep) {
        if (Math.abs(error) <= mouseStep * 0.5f) {
            return 0.0f;
        }

        pendingSteps = alignPendingDirection(pendingSteps, error);
        float remainingSteps = Math.abs(error / mouseStep);
        float availableSteps = Math.max(0.0f, remainingSteps - Math.abs(pendingSteps));
        float appliedSteps = Math.min(controllerStep, availableSteps);
        return error > 0.0f ? pendingSteps + appliedSteps : pendingSteps - appliedSteps;
    }

    private static float alignPendingDirection(float pendingSteps, float error) {
        if ((pendingSteps > 0.0f && error < 0.0f) || (pendingSteps < 0.0f && error > 0.0f)) {
            return 0.0f;
        }
        return pendingSteps;
    }

    private void updateControllerSpeedMultiplier() {
        long now = System.currentTimeMillis();
        if (now < this.nextControllerSpeedRetarget) {
            return;
        }
        this.nextControllerSpeedRetarget = now + 180L + this.rotationRandom.nextInt(321);
        this.controllerSpeedMultiplier = sampleVapeSpeedMultiplier();
    }

    private float sampleVapeSpeedMultiplier() {
        double uniform1 = Math.max(1.0E-4D, this.rotationRandom.nextDouble());
        double uniform2 = this.rotationRandom.nextDouble();
        double gaussian = Math.sqrt(-2.0D * Math.log(uniform1))
                * Math.cos(Math.PI * 2.0D * uniform2);
        double multiplier = Math.exp(0.65D + 0.25D * gaussian);
        return (float) Math.max(1.4D, Math.min(3.0D, multiplier));
    }

    private static float mapRavenSpeedToVape(int speed) {
        float normalized = MathHelper.clamp_float((speed - 1.0f) / 29.0f, 0.0f, 1.0f);
        return 1.0f + normalized * 9.0f;
    }

    private double distanceFromEye(Vec3 point) {
        double dx = point.xCoord - mc.thePlayer.posX;
        double dy = point.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = point.zCoord - mc.thePlayer.posZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float clampIntegral(float value, float limit) {
        return MathHelper.clamp_float(value, -limit, limit);
    }

    private void resetControllerMotion() {
        this.pendingYawSteps = 0.0f;
        this.pendingPitchSteps = 0.0f;
        this.yawIntegral = 0.0f;
        this.pitchIntegral = 0.0f;
        this.previousYawStep = 0.0f;
        this.previousPitchStep = 0.0f;
        this.controllerHasOutput = false;
    }

    private double randomRange(double min, double max) {
        return min + this.rotationRandom.nextDouble() * (max - min);
    }

    private static double approach(double current, double target, double step) {
        if (current < target) return Math.min(current + step, target);
        if (current > target) return Math.max(current - step, target);
        return current;
    }

    private static double clampToBox(double value, double min, double max) {
        if (max - min < 0.002) {
            return (min + max) * 0.5;
        }
        return Math.max(min + 0.001, Math.min(max - 0.001, value));
    }

    public void updateServerRotations() {
        if (mc.thePlayer == null) {
            return;
        }
        if (rotationsUpdatedThisTick) {
            return;
        }
        rotationsUpdatedThisTick = true;

        ClientRotationEvent event = new ClientRotationEvent(this.serverYaw, this.serverPitch);

        McpRuntime.EVENTS.post(event);

        this.serverYaw = event.yaw;
        this.serverPitch = event.pitch;
        this.rotationOwner = event.getRotationOwner();
        this.serverRelativeMovementInputs = event.usesServerRelativeMovementInputs();
        this.movementYawCorrection = event.getMovementYawCorrection();

        if (this.serverYaw == null && this.serverPitch == null) {
            return;
        }

        float previousServerYaw = RotationUtils.serverRotations[0];
        float targetServerYaw = this.serverYaw == null
                ? mc.thePlayer.rotationYaw : this.serverYaw;
        float quantizedYawLimit = SilentRotationManager.gcdFlooredLimit(
                event.getMaxYawDelta(), RotationUtils.getGCD());
        targetServerYaw = clampYawDelta(
                targetServerYaw, previousServerYaw, quantizedYawLimit);
        float targetServerPitch = this.serverPitch == null
                ? mc.thePlayer.rotationPitch : this.serverPitch;
        float[] fixed = event.isRotationAlreadyQuantized()
                ? new float[]{
                targetServerYaw,
                RotationUtils.clampPitch(targetServerPitch)}
                : RotationUtils.fixRotation(
                targetServerYaw,
                targetServerPitch,
                previousServerYaw,
                RotationUtils.serverRotations[1]
        );
        this.serverYaw = fixed[0];
        this.serverPitch = fixed[1];

        if (this.serverYaw != mc.thePlayer.rotationYaw && (event.yaw == null || !event.yaw.isNaN())) {
            this.setRotations = true;
        }

        if (this.serverPitch != mc.thePlayer.rotationPitch && (event.pitch == null || !event.pitch.isNaN())) {
            this.setRotations = true;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        updateServerRotations();
    }

    @Subscribe
    public void onPreMotion(PreMotionEvent e) {
        if (!this.setRotations) {
            return;
        }
        if (this.serverYaw != null && !this.serverYaw.isNaN()) e.setYaw(this.serverYaw);
        if (this.serverPitch != null && !this.serverPitch.isNaN()) e.setPitch(this.serverPitch);
    }

    @Subscribe
    public void onRunTick(GameTickEvent e) {
        if (this.setRotations && this.serverYaw != null && mc.thePlayer != null) {
            float serverYawVal = RotationUtils.serverRotations[0];
            float unwrapped = unwrapYaw(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), serverYawVal);
            mc.thePlayer.rotationYaw = unwrapped;
            mc.thePlayer.prevRotationYaw = unwrapped;
        }
        this.serverYaw = this.serverPitch = null;
        this.rotationOwner = null;
        this.setRotations = false;
        this.forceMovementFix = false;
        this.serverRelativeMovementInputs = false;
        this.movementYawCorrection = null;
        this.rotationsUpdatedThisTick = false;
        this.swappedForMouseOver = false;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (this.setRotations && mc.thePlayer != null) {
            mc.thePlayer.prevRenderArmYaw = mc.thePlayer.rotationYaw;
            mc.thePlayer.renderArmYaw = mc.thePlayer.rotationYaw;
        }
    }

    public boolean isActive() {
        return this.setRotations && (this.serverYaw != null || this.serverPitch != null);
    }

    public boolean isRotationOwner(Object owner) {
        return owner != null && this.rotationOwner == owner;
    }

    public void beginSwap(Entity e, float yaw, float pitch, boolean swapPitch) {
        this.savedYaw = e.rotationYaw;
        this.savedPrevYaw = e.prevRotationYaw;
        this.savedPitch = e.rotationPitch;
        this.savedPrevPitch = e.prevRotationPitch;

        e.rotationYaw = yaw;
        e.prevRotationYaw = yaw;

        if (swapPitch) {
            e.rotationPitch = pitch;
            e.prevRotationPitch = pitch;
        }
    }

    public void endSwap(Entity e) {
        e.rotationYaw = this.savedYaw;
        e.prevRotationYaw = this.savedPrevYaw;
        e.rotationPitch = this.savedPitch;
        e.prevRotationPitch = this.savedPrevPitch;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPostInput(PostPlayerInputEvent event) {
        if (!fixMovement()) {
            return;
        }

        if (this.serverRelativeMovementInputs) {
            return;
        }

        float sneakMultiplier = mc.thePlayer.movementInput.sneak ? 0.3F : 1F;

        float yaw = this.serverYaw;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;

        if (forward == 0 && strafe == 0) {
            return;
        }

        double angle = Math.toDegrees(getDirection(mc.thePlayer.rotationYaw, forward, strafe));

        float closestForward = 0, closestStrafe = 0, closestDifference = Float.MAX_VALUE;

        for (float pfRaw = -1F; pfRaw <= 1F; pfRaw += 1F) {
            for (float psRaw = -1F; psRaw <= 1F; psRaw += 1F) {
                if (pfRaw == 0 && psRaw == 0) {
                    continue;
                }

                float predictedForward = pfRaw * sneakMultiplier;
                float predictedStrafe = psRaw * sneakMultiplier;

                double predictedAngle = Math.toDegrees(getDirection(yaw, predictedForward, predictedStrafe));
                double difference = Math.abs(MathHelper.wrapAngleTo180_double(angle - predictedAngle));

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        mc.thePlayer.movementInput.moveForward = closestForward;
        mc.thePlayer.movementInput.moveStrafe  = closestStrafe;
    }

    @Subscribe
    public void onStrafe(StrafeEvent e) {
        if (fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    @Subscribe
    public void onJump(JumpEvent e) {
        if (fixMovement()) {
            e.setYaw(this.serverYaw);
        }
    }

    public boolean fixMovement() {
        boolean enabled = this.movementYawCorrection != null
                ? this.movementYawCorrection
                : MovementFix.isActive() || this.forceMovementFix;
        return enabled && this.setRotations;
    }

    public static double getDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;

        float forward = 1F;

        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;

        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;

        return Math.toRadians(rotationYaw);
    }

    public static RotationHelper get() {
        return INSTANCE;
    }

    private static float clampYawDelta(float yaw, float previousYaw, float maxYawDelta) {
        if (Float.isNaN(maxYawDelta) || Float.isInfinite(maxYawDelta)) {
            return yaw;
        }
        float delta = MathHelper.wrapAngleTo180_float(yaw - previousYaw);
        float limit = Math.max(0.0F, maxYawDelta);
        return previousYaw + Math.max(-limit, Math.min(limit, delta));
    }

    public void setRotations(float yaw, float pitch) {
        this.serverYaw = yaw;
        this.serverPitch = pitch;
        this.rotationOwner = null;
        this.movementYawCorrection = null;
        this.setRotations = true;
    }

    public void setYaw(float yaw) {
        this.serverYaw = yaw;
        this.rotationOwner = null;
        this.movementYawCorrection = null;
        this.setRotations = true;
    }

    public void setPitch(float pitch) {
        this.serverPitch = pitch;
        this.rotationOwner = null;
        this.movementYawCorrection = null;
        this.setRotations = true;
    }

    public void setServerRelativeMovementInputs(boolean relative) {
        this.serverRelativeMovementInputs = relative;
    }

    public Float getServerYaw() {
        return serverYaw;
    }

    public Float getServerPitch() {
        return serverPitch;
    }
}
