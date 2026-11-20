package idle.miau.lag.timeout;

public final class DurationTimeout extends AbstractTimeout {
    private final long deadlineNanos;

    public DurationTimeout(long durationMs) {
        long safeDurationMs = Math.max(0L, durationMs);
        this.deadlineNanos = System.nanoTime() + safeDurationMs * 1_000_000L;
    }

    @Override
    protected boolean shouldHaveTimedOut() {
        return System.nanoTime() - deadlineNanos >= 0L;
    }
}
