package idle.miau.baritone;

public final class MovementFrame {
    private MovementFrame() {}
    public static float[] rebase(float forward, float strafe, float movementYaw, float facingYaw) {
        double radians = Math.toRadians(baritone.api.utils.Rotation.normalizeYaw(movementYaw - facingYaw));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        return new float[]{(float)(forward * cos + strafe * sin), (float)(strafe * cos - forward * sin)};
    }
}
