package idle.miau.module.impl.combat;

final class FightBotMovement {
    enum Phase { SEARCHING, APPROACH, ADVANCE, PAUSE, RETREAT }
    record Step(Phase phase, float forward, float strafe, boolean sprint) {}
    private int cycleStart = Integer.MIN_VALUE;
    private int hitPauseAt = Integer.MAX_VALUE;
    private int hitPauseUntil = Integer.MIN_VALUE;
    private int nextHitReset = Integer.MIN_VALUE;

    void reset() {
        cycleStart = Integer.MIN_VALUE;
        hitPauseAt = Integer.MAX_VALUE;
        hitPauseUntil = nextHitReset = Integer.MIN_VALUE;
    }

    void onHit(int tick, int moveTicks, int stopTicks) {
        if (tick < nextHitReset) return;
        hitPauseAt = tick + 1;
        hitPauseUntil = hitPauseAt + Math.max(1, stopTicks);
        nextHitReset = tick + Math.max(6, moveTicks + stopTicks);
    }

    Step step(int tick, double distance, double idealDistance, boolean rhythm,
              int moveTicks, int stopTicks, boolean strafe, boolean sprint) {
        if (cycleStart == Integer.MIN_VALUE || tick < cycleStart) cycleStart = tick;
        if (distance < idealDistance - 0.35) return new Step(Phase.RETREAT, -1, 0, false);
        boolean pause = rhythm && (tick >= hitPauseAt && tick < hitPauseUntil
                || Math.floorMod(tick - cycleStart, Math.max(1, moveTicks) + Math.max(1, stopTicks)) >= Math.max(1, moveTicks));
        if (pause) return new Step(Phase.PAUSE, 0, 0, false);
        float sideways = strafe ? (Math.floorDiv(tick - cycleStart, 20) % 2 == 0 ? 1 : -1) : 0;
        float forward = distance > idealDistance + 0.15 ? 1 : 0;
        return new Step(Phase.ADVANCE, forward, sideways, sprint && forward > 0);
    }
}
