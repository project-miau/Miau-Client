package idle.miau.clickgui.modern;

final class MiauAnimation {
    enum Curve { LINEAR, DECELERATE, SINE, EXPO }

    private final long duration;
    private final Curve curve;
    private float start;
    private float target;
    private long started;

    MiauAnimation(long duration, Curve curve) {
        this.duration = duration * 1_000_000L;
        this.curve = curve;
    }

    float value() {
        return value(System.nanoTime());
    }

    float value(long now) {
        float progress = Math.max(0, Math.min(1, (now - started) / (float) duration));
        float eased = switch (curve) {
            case LINEAR -> progress;
            case DECELERATE -> 1 - (1 - progress) * (1 - progress);
            case SINE -> (float) Math.sin(progress * Math.PI * 0.5);
            case EXPO -> progress == 1 ? 1 : 1 - (float) Math.pow(2, -10 * progress);
        };
        return start + (target - start) * eased;
    }

    float to(float next) {
        if (next != target) {
            start = value();
            target = next;
            started = System.nanoTime();
        }
        return value();
    }

    void reset(float value) {
        start = target = value;
        started = 0;
    }

    boolean finished() {
        return System.nanoTime() - started >= duration;
    }
}
