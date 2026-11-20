package idle.miau.module.impl.combat.autoblock;

public enum HypixelPostStep {
    NONE(false, false),
    BLOCK(true, false),
    FLUSH(false, true),
    BLOCK_AND_FLUSH(true, true);

    private final boolean block;
    private final boolean flush;

    HypixelPostStep(boolean block, boolean flush) {
        this.block = block;
        this.flush = flush;
    }

    public boolean shouldBlock() {
        return block;
    }

    public boolean shouldFlush() {
        return flush;
    }
}
