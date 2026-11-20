package idle.miau.clickgui.miau;

final class MiauFloatAnimation {
    private float value;
    private float start;
    private float target;
    private long startedAt;
    private long duration;

    MiauFloatAnimation(float initialValue) {
        snap(initialValue);
    }

    void animateTo(float targetValue, long durationMillis) {
        float current = get();
        if (Math.abs(target - targetValue) < 0.001F) {
            return;
        }
        value = current;
        start = current;
        target = targetValue;
        startedAt = System.nanoTime();
        duration = Math.max(1L, durationMillis) * 1_000_000L;
    }

    float get() {
        if (duration == 0L) {
            return value;
        }
        float progress = Math.min(1.0F, (System.nanoTime() - startedAt) / (float) duration);
        float remaining = 1.0F - progress;
        float eased = 1.0F - remaining * remaining * remaining;
        value = start + (target - start) * eased;
        if (progress >= 1.0F || Math.abs(value - target) < 0.01F) {
            snap(target);
        }
        return value;
    }

    void snap(float value) {
        this.value = value;
        start = value;
        target = value;
        startedAt = 0L;
        duration = 0L;
    }
}
