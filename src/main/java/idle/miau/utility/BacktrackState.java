package idle.miau.utility;

import java.util.Deque;

public final class BacktrackState {
    private BacktrackState() {}

    public static boolean isHealthDecrease(float currentHealth, float incomingHealth) {
        return !Float.isNaN(currentHealth) && !Float.isNaN(incomingHealth)
                && incomingHealth < currentHealth;
    }

    public static int clampDelay(int value, int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        return Math.max(low, Math.min(high, value));
    }

    public static boolean passesOnlyNeedCheck(boolean onlyNeed, double trueDistance, double clientDistance) {
        return !onlyNeed || trueDistance >= clientDistance;
    }

    public static boolean isMovingAway(
            double previousDistanceSquared, double currentDistanceSquared, double epsilon) {
        return currentDistanceSquared - Math.max(0.0D, epsilon) > previousDistanceSquared;
    }

    public static boolean shouldKeepMovementTarget(
            double appliedDistanceSquared, double predictedDistanceSquared, double maximumDistanceSquared) {
        return predictedDistanceSquared <= maximumDistanceSquared
                && appliedDistanceSquared <= predictedDistanceSquared;
    }

    public static boolean shouldKeepSyncWindow(
            boolean smart,
            double trueDistance,
            double clientDistance,
            double trueHitboxDistance,
            double maximumDistance) {
        return trueDistance <= 6.0D
                && passesOnlyNeedCheck(smart, trueDistance, clientDistance)
                && trueHitboxDistance <= Math.max(0.0D, maximumDistance);
    }

    public static void trimHistory(Deque<BacktrackPosition> history, int maximum) {
        int safeMaximum = Math.max(1, maximum);
        while (history.size() >= safeMaximum) {
            history.pollFirst();
        }
    }

    public static void expireHistory(Deque<BacktrackPosition> history, long cutoff) {
        while (!history.isEmpty() && history.peekFirst().getTimestamp() < cutoff) {
            history.pollFirst();
        }
    }

    public static long findRangeTimestamp(
            Iterable<BacktrackPosition> positions,
            AxisDistance distance,
            double minimum,
            double maximum) {
        for (BacktrackPosition position : positions) {
            double value = distance.distance(position);
            if (value >= minimum && value <= maximum) {
                return position.getTimestamp();
            }
        }
        return -1L;
    }

    public interface AxisDistance {
        double distance(BacktrackPosition position);
    }
}
