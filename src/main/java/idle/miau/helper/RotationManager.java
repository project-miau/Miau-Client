package idle.miau.helper;

import idle.miau.event.ClientRotationEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

public final class RotationManager {
    private static final String[] ANGLE_LIMITERS = {
            "Linear", "Accelerated", "Interpolated", "None"
    };
    private static final String[] AVERAGE_LIMITERS = {
            "NCP", "Custom", "None"
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Module module;
    private final ButtonSetting silent;
    private final SliderSetting angleLimiter;
    private final SliderSetting yawSpeed;
    private final SliderSetting pitchSpeed;
    private final SliderSetting maxDeltaHistorySize;
    private final SliderSetting averageLimitMode;
    private final SliderSetting maxAverageYawDelta;
    private final RangeSetting yawMultiplierOnLimit;
    private final Deque<Float> yawHistory = new ArrayDeque<Float>();
    private float previousYaw;
    private float previousPitch;
    private double acceleratedYaw;
    private double acceleratedPitch;
    private boolean initialized;

    public RotationManager(Module module) {
        this.module = module;
        module.registerSetting(silent = new ButtonSetting("Silent", true));
        module.registerSetting(angleLimiter = new SliderSetting(
                "Angle limiter", 0, ANGLE_LIMITERS));
        module.registerSetting(yawSpeed = new SliderSetting(
                "Yaw speed", 180.0D, 1.0D, 180.0D, 1.0D));
        module.registerSetting(pitchSpeed = new SliderSetting(
                "Pitch speed", 180.0D, 1.0D, 180.0D, 1.0D));
        module.registerSetting(maxDeltaHistorySize = new SliderSetting(
                "Max delta history size", 20.0D, 0.0D, 20.0D, 1.0D));
        module.registerSetting(averageLimitMode = new SliderSetting(
                "Average yaw limiter mode", 2, AVERAGE_LIMITERS));
        module.registerSetting(maxAverageYawDelta = new SliderSetting(
                "Max average yaw delta", 90.0D, 1.0D, 180.0D, 1.0D));
        module.registerSetting(yawMultiplierOnLimit = new RangeSetting(
                "Yaw multiplier on limit", 0.0D, 1.0D, 0.1D, 1.0D, 0.01D));
        updateVisibility();
    }

    public void updateVisibility() {
        boolean limited = (int) angleLimiter.getInput() != 3;
        boolean history = maxDeltaHistorySize.getInput() > 0.0D;
        boolean customAverage = history && (int) averageLimitMode.getInput() == 1;
        yawSpeed.setVisible(limited, module);
        pitchSpeed.setVisible(limited, module);
        averageLimitMode.setVisible(history, module);
        maxAverageYawDelta.setVisible(customAverage, module);
        yawMultiplierOnLimit.setVisible(customAverage, module);
    }

    public float[] getCurrentRotation(ClientRotationEvent event) {
        float yaw = event.yaw != null ? event.yaw
                : initialized ? previousYaw
                : mc.thePlayer.rotationYaw;
        float pitch = event.pitch != null ? event.pitch
                : initialized ? previousPitch
                : mc.thePlayer.rotationPitch;
        return new float[]{yaw, pitch};
    }

    public float[] setRotation(ClientRotationEvent event, float[] targetRotation) {
        float[] current = getCurrentRotation(event);
        float[] next = limitRotation(current, targetRotation);
        next = RotationUtils.patchGCD(current, next);
        next[1] = MathHelper.clamp_float(next[1], -90.0F, 90.0F);
        applyRotation(current, next);
        event.setRotation(next[0], next[1]);
        return next;
    }

    public float[] setRotation(
            ClientRotationEvent event, float[] targetRotation, int priority) {
        float[] current = getCurrentRotation(event);
        float[] next = limitRotation(current, targetRotation);
        next = RotationUtils.patchGCD(current, next);
        next[1] = MathHelper.clamp_float(next[1], -90.0F, 90.0F);
        if (!event.trySetRotation(module, priority, next[0], next[1])) {
            return null;
        }
        applyRotation(current, next);
        return next;
    }

    private void applyRotation(float[] current, float[] next) {
        updateHistory(current[0], next[0]);
        previousYaw = next[0];
        previousPitch = next[1];
        initialized = true;
        if (!silent.isToggled()) {
            mc.thePlayer.rotationYaw = next[0];
            mc.thePlayer.rotationPitch = next[1];
        } else {
            RotationUtils.setFakeRotations(next[0], next[1]);
        }
    }

    public void reset() {
        yawHistory.clear();
        acceleratedYaw = 0.0D;
        acceleratedPitch = 0.0D;
        initialized = false;
    }

    private float[] limitRotation(float[] current, float[] target) {
        float yawDifference = MathHelper.wrapAngleTo180_float(target[0] - current[0]);
        float pitchDifference = MathHelper.wrapAngleTo180_float(target[1] - current[1]);
        yawDifference = applyAverageLimit(yawDifference);
        int limiter = (int) angleLimiter.getInput();
        if (limiter == 3) {
            return new float[]{current[0] + yawDifference, current[1] + pitchDifference};
        }
        double difference = Math.max(1.0E-6D,
                Math.hypot(Math.abs(yawDifference), Math.abs(pitchDifference)));
        float horizontalSpeed = Math.min((float) yawSpeed.getInput() / 3.0F, 180.0F);
        float verticalSpeed = Math.min((float) pitchSpeed.getInput() / 3.0F, 180.0F);
        if (limiter == 2) {
            horizontalSpeed = MathHelper.clamp_float(
                    Math.abs(yawDifference) / 180.0F * horizontalSpeed,
                    random(4.0F, 6.0F), 180.0F);
            verticalSpeed = MathHelper.clamp_float(
                    Math.abs(pitchDifference) / 180.0F * verticalSpeed,
                    random(4.0F, 6.0F), 180.0F);
        }
        double yawLimit = Math.abs(yawDifference / difference) * horizontalSpeed;
        double pitchLimit = Math.abs(pitchDifference / difference) * verticalSpeed;
        double yawDelta = MathHelper.clamp_double(yawDifference, -yawLimit, yawLimit);
        double pitchDelta = MathHelper.clamp_double(pitchDifference, -pitchLimit, pitchLimit);
        if (limiter == 1) {
            acceleratedYaw += (yawDelta - acceleratedYaw) * 0.25D;
            acceleratedPitch += (pitchDelta - acceleratedPitch) * 0.25D;
            yawDelta = acceleratedYaw;
            pitchDelta = acceleratedPitch;
        } else {
            acceleratedYaw = yawDelta;
            acceleratedPitch = pitchDelta;
        }
        return new float[]{current[0] + (float) yawDelta, current[1] + (float) pitchDelta};
    }

    private float applyAverageLimit(float yawDifference) {
        if (yawHistory.isEmpty() || (int) averageLimitMode.getInput() == 2) {
            return yawDifference;
        }
        float sum = 0.0F;
        for (float value : yawHistory) {
            sum += value;
        }
        float average = Math.abs(sum / yawHistory.size());
        if ((int) averageLimitMode.getInput() == 0) {
            return average > 50.0F ? 0.0F : yawDifference;
        }
        if (average <= maxAverageYawDelta.getInput()) {
            return yawDifference;
        }
        float percentage = 1.0F - MathHelper.clamp_float(
                (average - (float) maxAverageYawDelta.getInput())
                        / (float) maxAverageYawDelta.getInput(), 0.0F, 1.0F);
        double multiplier = yawMultiplierOnLimit.getMinimum()
                + (yawMultiplierOnLimit.getMaximum() - yawMultiplierOnLimit.getMinimum())
                * percentage;
        return (float) (yawDifference * multiplier);
    }

    private void updateHistory(float currentYaw, float nextYaw) {
        int maximum = (int) maxDeltaHistorySize.getInput();
        if (maximum <= 0) {
            yawHistory.clear();
            return;
        }
        yawHistory.addLast(MathHelper.wrapAngleTo180_float(currentYaw - nextYaw));
        while (yawHistory.size() > maximum) {
            yawHistory.removeFirst();
        }
    }

    private static float random(float minimum, float maximum) {
        return minimum + ThreadLocalRandom.current().nextFloat() * (maximum - minimum);
    }
}
