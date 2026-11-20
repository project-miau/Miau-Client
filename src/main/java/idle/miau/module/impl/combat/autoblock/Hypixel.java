package idle.miau.module.impl.combat.autoblock;

public final class Hypixel {
    private static final int STAGE_BLOCKED = 0;
    private static final int STAGE_DEFERRED = 1;
    private static final int STAGE_ATTACK = 2;
    private static final int STAGE_RELEASED = 3;
    private static final int STAGE_WAIT = 5;
    private static final int STAGE_POST_BLOCK = 7;
    private static final int STAGE_POST_BLOCK_WAIT = 10;
    private static final int STAGE_FLUSH = 11;
    private static final int STAGE_NO_SLOW_POST_BLOCK = 12;
    private static final long PREPARE_MILLIS = 50L;

    private int stage;
    private long nextAttackMillis;
    private long stepMillis;
    private long attackDelayMillis;
    private boolean initialized;

    public void initialize(boolean blocking) {
        stage = blocking ? STAGE_BLOCKED : STAGE_DEFERRED;
        initialized = true;
    }

    public PreStep preStep(boolean noSlow, double cps, boolean canReleaseBlock, long now) {
        stepMillis = now;
        attackDelayMillis = cpsToDelay(cps);
        boolean attackDue = nextAttackMillis == 0L || now >= nextAttackMillis;
        boolean prepareAttack = nextAttackMillis == 0L
                || now + PREPARE_MILLIS >= nextAttackMillis;
        boolean beginQueue = false;
        boolean releaseBlock = false;
        boolean spoofSlot = false;
        boolean restoreSlot = false;
        int keepSprintStage = -1;

        if (stage == STAGE_DEFERRED && attackDue) {
            stage = STAGE_ATTACK;
            keepSprintStage = 2;
        } else if (stage == STAGE_BLOCKED && prepareAttack && canReleaseBlock) {
            beginQueue = true;
            releaseBlock = true;
            spoofSlot = noSlow;
            stage = STAGE_RELEASED;
        } else if (stage == STAGE_RELEASED) {
            beginQueue = true;
            restoreSlot = noSlow;
            stage = attackDue ? STAGE_ATTACK : STAGE_WAIT;
            keepSprintStage = attackDue ? 2 : 1;
        } else if (stage == STAGE_WAIT) {
            beginQueue = true;
            if (attackDue) {
                stage = STAGE_ATTACK;
                keepSprintStage = 2;
            } else {
                keepSprintStage = 1;
            }
        }

        boolean normalized = true;
        if (stage == STAGE_NO_SLOW_POST_BLOCK) {
            stage = STAGE_POST_BLOCK;
        } else if (stage == STAGE_POST_BLOCK_WAIT) {
            stage = STAGE_FLUSH;
        } else {
            normalized = false;
        }

        boolean attackWindow = !normalized && stage == STAGE_ATTACK && attackDue;
        return new PreStep(
                beginQueue,
                releaseBlock,
                spoofSlot,
                restoreSlot,
                keepSprintStage,
                attackWindow);
    }

    public void completeAttackWindow(boolean noSlow, boolean performedAction) {
        if (stage != STAGE_ATTACK || !performedAction) {
            return;
        }
        nextAttackMillis = stepMillis + attackDelayMillis;
        stage = noSlow ? STAGE_NO_SLOW_POST_BLOCK : STAGE_POST_BLOCK;
    }

    public HypixelPostStep postStep() {
        if (stage == STAGE_POST_BLOCK) {
            stage = STAGE_POST_BLOCK_WAIT;
            return HypixelPostStep.BLOCK;
        }
        if (stage == STAGE_FLUSH) {
            stage = STAGE_BLOCKED;
            return HypixelPostStep.FLUSH;
        }
        return HypixelPostStep.NONE;
    }

    public void reset() {
        stage = STAGE_BLOCKED;
        nextAttackMillis = 0L;
        stepMillis = 0L;
        attackDelayMillis = 0L;
        initialized = false;
    }

    public int getStage() {
        return stage;
    }

    public boolean isInitialized() {
        return initialized;
    }

    static long cpsToDelay(double cps) {
        double normalizedCps = Math.max(1.0D, Math.min(50.0D, cps));
        return Math.max(1L, Math.round(1000.0D / normalizedCps));
    }

    public static final class PreStep implements HypixelStep {
        private final boolean beginQueue;
        private final boolean releaseBlock;
        private final boolean spoofSlot;
        private final boolean restoreSlot;
        private final int keepSprintStage;
        private final boolean attackWindow;

        private PreStep(
                boolean beginQueue,
                boolean releaseBlock,
                boolean spoofSlot,
                boolean restoreSlot,
                int keepSprintStage,
                boolean attackWindow) {
            this.beginQueue = beginQueue;
            this.releaseBlock = releaseBlock;
            this.spoofSlot = spoofSlot;
            this.restoreSlot = restoreSlot;
            this.keepSprintStage = keepSprintStage;
            this.attackWindow = attackWindow;
        }

        @Override
        public boolean shouldBeginQueue() {
            return beginQueue;
        }

        @Override
        public boolean shouldReleaseBlock() {
            return releaseBlock;
        }

        @Override
        public boolean shouldSpoofSlot() {
            return spoofSlot;
        }

        @Override
        public boolean shouldRestoreSlot() {
            return restoreSlot;
        }

        @Override
        public int getKeepSprintStage() {
            return keepSprintStage;
        }

        @Override
        public boolean isAttackWindow() {
            return attackWindow;
        }
    }
}
