package idle.miau.module.impl.player;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.EnumFacing;

final class ScaffoldPlacementSafety {
    static final float MAX_PITCH_STEP = 17.5F;
    private boolean pitchKnown;
    private float pitch, pitchDelta;

    synchronized void reset() {
        pitchKnown = false;
        pitchDelta = 0;
    }

    synchronized void observe(Packet<?> packet) {
        if (!(packet instanceof C03PacketPlayer)) return;
        C03PacketPlayer movement = (C03PacketPlayer) packet;
        if (movement.getRotating()) {
            float nextPitch = movement.getPitch();
            pitchDelta = pitchKnown ? Math.abs(nextPitch - pitch) : 0;
            pitchKnown = Float.isFinite(nextPitch);
            pitch = nextPitch;
        }
    }

    synchronized boolean allowsPlacement(EnumFacing face) {
        return face == EnumFacing.UP || !pitchKnown || pitchDelta <= 18.0F;
    }

    synchronized float limitPitch(float requested, float fallback, float gcd) {
        float origin = pitchKnown ? pitch : fallback;
        if (!Float.isFinite(origin)) origin = 0;
        if (!Float.isFinite(requested)) return origin;
        if (Math.abs(requested - origin) <= MAX_PITCH_STEP) return requested;
        float step = Float.isFinite(gcd) && gcd > 0 ? gcd : 0.15F;
        float limit = (float) Math.floor(MAX_PITCH_STEP / step) * step;
        return Math.max(-90, Math.min(90, origin + Math.copySign(limit, requested - origin)));
    }

    synchronized boolean withinPitchBudget(float candidate, float fallback) {
        float origin = pitchKnown ? pitch : fallback;
        return Float.isFinite(candidate) && Float.isFinite(origin)
                && Math.abs(candidate - origin) <= MAX_PITCH_STEP;
    }

    static boolean isScaffoldJWindow(double acceleration, float pitchDelta) {
        return Double.isFinite(acceleration) && acceleration < 1.0D
                && Double.toString(acceleration).contains("E") && pitchDelta > 18.0F;
    }
}
