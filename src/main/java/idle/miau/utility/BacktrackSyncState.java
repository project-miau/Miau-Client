package idle.miau.utility;

public final class BacktrackSyncState {
    private long confirmationDeadline = -1L;
    private long delayDeadline = -1L;

    public void armHit(long now, long confirmationTimeout) {
        confirmationDeadline = deadline(now, confirmationTimeout);
    }

    public boolean confirmHit(long now, long delay) {
        if (!isAwaitingHit() || now > confirmationDeadline || delay <= 0L) {
            expireAwaitingHit(now);
            if (delay <= 0L) {
                confirmationDeadline = -1L;
            }
            return false;
        }
        confirmationDeadline = -1L;
        delayDeadline = Math.max(delayDeadline, deadline(now, delay));
        return true;
    }

    public boolean expireAwaitingHit(long now) {
        if (!isAwaitingHit() || now <= confirmationDeadline) {
            return false;
        }
        confirmationDeadline = -1L;
        return true;
    }

    public boolean isAwaitingHit() {
        return confirmationDeadline >= 0L;
    }

    public boolean hasDelayWindow() {
        return delayDeadline >= 0L;
    }

    public boolean isDelaying(long now) {
        return hasDelayWindow() && now < delayDeadline;
    }

    public boolean isDelayExpired(long now) {
        return hasDelayWindow() && now >= delayDeadline;
    }

    public void clearDelayWindow() {
        delayDeadline = -1L;
    }

    public void reset() {
        confirmationDeadline = -1L;
        delayDeadline = -1L;
    }

    private static long deadline(long now, long duration) {
        long safeDuration = Math.max(0L, duration);
        return now > Long.MAX_VALUE - safeDuration ? Long.MAX_VALUE : now + safeDuration;
    }
}
