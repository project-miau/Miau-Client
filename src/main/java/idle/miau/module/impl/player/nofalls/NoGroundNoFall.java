package idle.miau.module.impl.player.nofalls;

import idle.miau.event.PreMotionEvent;

public final class NoGroundNoFall extends NoFallMode {
    public NoGroundNoFall(NoFallModule owner) {
        super("NoGround", owner);
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
        event.setOnGround(false);
    }
}
