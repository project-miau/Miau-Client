package idle.miau.module.impl.render;

public final class MiauHudAnimation {
    public static final long DURATION_MS = 250L;

    private boolean forwards;
    private long startTime;
    private long duration = DURATION_MS;

    public MiauHudAnimation(boolean visible, long now) {
        snapTo(visible, now);
    }

    public void setVisible(boolean visible, long now) {
        if (forwards == visible) {
            return;
        }

        double output = getOutput(now);
        forwards = visible;
        double elapsedProgress = visible
                ? 1.0D - Math.sqrt(1.0D - output)
                : 1.0D - Math.sqrt(output);
        startTime = now - Math.round(clamp01(elapsedProgress) * duration);
    }

    public void setDuration(long duration, long now) {
        long next = Math.max(1, duration);
        if (this.duration == next) return;
        double progress = clamp01((now - startTime) / (double) this.duration);
        this.duration = next;
        startTime = now - Math.round(progress * next);
    }

    public void snapTo(boolean visible, long now) {
        forwards = visible;
        startTime = now - duration;
    }

    public double getOutput(long now) {
        double progress = clamp01((now - startTime) / (double) duration);
        double decelerated = 1.0D - (progress - 1.0D) * (progress - 1.0D);
        return clamp01(forwards ? decelerated : 1.0D - decelerated);
    }

    public boolean isFinishedHidden(long now) {
        return !forwards && now - startTime >= duration;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
