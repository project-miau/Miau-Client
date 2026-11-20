package idle.miau.helper;

public final class RotationPriority {
    public static final int MOVEMENT = 100;
    public static final int BED_AURA_DEFERRED = 200;
    public static final int COMBAT = 300;
    public static final int BED_AURA = 400;
    public static final int BLOCK_PLACEMENT = 500;

    private RotationPriority() {
    }
}
