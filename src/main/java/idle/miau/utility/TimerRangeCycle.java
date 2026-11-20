package idle.miau.utility;

public final class TimerRangeCycle {
    public static final long TICK_NANOS = 50_000_000L;
    private static final long MAX_RECOVERY_NANOS = 1_000_000_000L;
    private static final int MAX_CATCH_UP_TICKS = 60;

    private Phase phase = Phase.IDLE;
    private long startedAt;
    private long freezeAt;
    private long recoverAt;
    private long recoveryDeadline;
    private int recoveryTicks;
    private int completedTicks;
    private double initialPartialTicks;
    private float baseTimerSpeed;

    public void start(long now, int lagTicks, int freezeTicks, int recoveryTicks,
                      float partialTicks) {
        start(now, lagTicks, freezeTicks, recoveryTicks, partialTicks, 1.0F);
    }

    public void start(long now, int lagTicks, int freezeTicks, int recoveryTicks,
                      float partialTicks, float timerSpeed) {
        startedAt = now;
        freezeAt = now + Math.max(0, lagTicks) * TICK_NANOS;
        recoverAt = freezeAt + Math.max(1, freezeTicks) * TICK_NANOS;
        recoveryDeadline = 0L;
        this.recoveryTicks = Math.max(1, Math.min(10, recoveryTicks));
        completedTicks = 0;
        initialPartialTicks = Math.max(0.0D, Math.min(1.0D, partialTicks));
        baseTimerSpeed = Math.max(0.0F, timerSpeed);
        phase = lagTicks > 0 ? Phase.LAGGING : Phase.FROZEN;
    }

    public boolean advance(long now) {
        if (phase == Phase.IDLE || phase == Phase.RECOVERING) {
            return false;
        }
        if (now >= recoverAt) {
            recover(now);
            return true;
        }
        if (now >= freezeAt) {
            phase = Phase.FROZEN;
        }
        return false;
    }

    public void recover(long now) {
        if (phase == Phase.IDLE || phase == Phase.RECOVERING) {
            return;
        }
        phase = Phase.RECOVERING;
        recoveryDeadline = now + MAX_RECOVERY_NANOS;
    }

    public float timerSpeed(long now) {
        if (phase == Phase.FROZEN) {
            return 0.0F;
        }
        if (phase != Phase.LAGGING) {
            return 1.0F;
        }
        double progress = (double) Math.max(0L, now - startedAt)
                / Math.max(1L, freezeAt - startedAt);
        return (float) Math.max(0.1D, 1.0D - progress * 0.9D);
    }

    public long remainingFreezeNanos(long now) {
        return phase == Phase.FROZEN ? Math.max(0L, recoverAt - now) : 0L;
    }

    public int extraTicks(long now, int scheduledTicks) {
        if (phase != Phase.RECOVERING) {
            return 0;
        }
        int missing = Math.max(0, expectedTicks(now) - completedTicks - scheduledTicks);
        return Math.min(missing, Math.min(recoveryTicks, Math.max(0, 10 - scheduledTicks)));
    }

    public void completeTick() {
        if (phase != Phase.IDLE) {
            ++completedTicks;
        }
    }

    public boolean isRecovered(long now) {
        return phase == Phase.RECOVERING
                && (completedTicks >= expectedTicks(now) || now >= recoveryDeadline);
    }

    public float partialTicks(long now) {
        double ticks = elapsedTicks(now);
        return (float) (ticks - Math.floor(ticks));
    }

    public Phase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public void reset() {
        phase = Phase.IDLE;
        startedAt = 0L;
        freezeAt = 0L;
        recoverAt = 0L;
        recoveryDeadline = 0L;
        completedTicks = 0;
        initialPartialTicks = 0.0D;
    }

    private int expectedTicks(long now) {
        return (int) Math.min(MAX_CATCH_UP_TICKS, Math.floor(elapsedTicks(now)));
    }

    private double elapsedTicks(long now) {
        return initialPartialTicks
                + (double) Math.max(0L, now - startedAt) * baseTimerSpeed / TICK_NANOS;
    }

    public enum Phase {
        IDLE,
        LAGGING,
        FROZEN,
        RECOVERING
    }
}
