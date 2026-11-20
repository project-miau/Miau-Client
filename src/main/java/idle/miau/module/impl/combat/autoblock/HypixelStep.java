package idle.miau.module.impl.combat.autoblock;

public interface HypixelStep {
    boolean shouldBeginQueue();

    boolean shouldReleaseBlock();

    boolean shouldSpoofSlot();

    boolean shouldRestoreSlot();

    int getKeepSprintStage();

    boolean isAttackWindow();
}
