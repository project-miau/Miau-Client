package idle.miau.clickgui;

public final class ClickGuiInput {
    private static final float WHEEL_UNIT = 120.0F;
    private static final float MIN_PRECISE_STEP = 0.25F;

    private ClickGuiInput() {
    }

    public static int eventMouseX(int eventX, int displayWidth, int logicalWidth) {
        return scaleEventCoordinate(eventX, displayWidth, logicalWidth);
    }

    public static int eventMouseY(int eventY, int displayHeight, int logicalHeight) {
        int scaled = scaleEventCoordinate(eventY, displayHeight, logicalHeight);
        return Math.max(0, logicalHeight - scaled - 1);
    }

    public static float scrollSteps(int wheelDelta) {
        if (wheelDelta == 0) {
            return 0.0F;
        }
        float steps = wheelDelta / WHEEL_UNIT;
        float magnitude = Math.max(MIN_PRECISE_STEP, Math.abs(steps));
        return Math.copySign(magnitude, steps);
    }

    public static float scrollPixels(int wheelDelta, double speed) {
        return scrollSteps(wheelDelta) * (float) Math.max(0.0D, speed);
    }

    private static int scaleEventCoordinate(int coordinate, int displaySize, int logicalSize) {
        if (logicalSize <= 1 || displaySize <= 1) {
            return 0;
        }
        long scaled = (long) Math.max(0, coordinate) * logicalSize / displaySize;
        return (int) Math.max(0L, Math.min(logicalSize - 1L, scaled));
    }
}
