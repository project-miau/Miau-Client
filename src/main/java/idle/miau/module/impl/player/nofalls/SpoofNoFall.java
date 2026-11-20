package idle.miau.module.impl.player.nofalls;

import idle.miau.event.PreMotionEvent;

public final class SpoofNoFall extends NoFallMode {
    public SpoofNoFall(NoFallModule owner) {
        super("Spoof", owner);
    }

    @Override
    public void onPreMotion(PreMotionEvent event) {
        event.setOnGround(true);
    }
}
