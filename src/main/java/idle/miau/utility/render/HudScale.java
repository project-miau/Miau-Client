package idle.miau.utility.render;

public final class HudScale {
    public static final double USER_BASELINE = 1.0D;
    public static final double LEGACY_VISUAL_BASELINE = 0.9D;
    public static final double LEGACY_TO_USER_FACTOR = 1.0D / LEGACY_VISUAL_BASELINE;

    private HudScale() {
    }

    public static float toRenderScale(double userScale) {
        return (float) (finiteOrBaseline(userScale) * LEGACY_VISUAL_BASELINE);
    }

    public static double fromLegacyScale(double legacyScale) {
        return Double.isFinite(legacyScale)
                ? legacyScale * LEGACY_TO_USER_FACTOR
                : USER_BASELINE;
    }

    public static double migrateLegacyScale(double legacyScale, double minimum, double maximum) {
        double migrated = fromLegacyScale(legacyScale);
        return Math.max(minimum, Math.min(maximum, migrated));
    }

    private static double finiteOrBaseline(double value) {
        return Double.isFinite(value) ? value : USER_BASELINE;
    }
}