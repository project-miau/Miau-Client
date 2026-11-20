package idle.miau.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class MiauLoadingScreen {
    private static final ResourceLocation SPLASH_TEXTURE = new ResourceLocation("miau/images/splash.png");
    private static final int TEXTURE_WIDTH = 300;
    private static final int TEXTURE_HEIGHT = 100;
    private static final int DISPLAY_WIDTH = 150;
    private static final int DISPLAY_HEIGHT = 50;
    private static final int PROGRESS_WIDTH = 116;
    private static final int PROGRESS_HEIGHT = 3;
    private static final long MIN_PROGRESS_ANIMATION_NANOS = 90_000_000L;
    private static final long FULL_PROGRESS_ANIMATION_NANOS = 600_000_000L;
    private static final int PROGRESS_ANIMATION_FPS = 120;
    static final int PROGRESS_COLOR = 0xFFFFFFFF;
    private static float displayedProgress;

    private MiauLoadingScreen() {
    }

    public static void render(Minecraft minecraft, TextureManager textureManager) {
        render(minecraft, textureManager, 0.0F);
    }

    public static void render(Minecraft minecraft, TextureManager textureManager, float progress) {
        int framebufferWidth = Math.max(1, Display.getWidth());
        int framebufferHeight = Math.max(1, Display.getHeight());
        int scaleFactor = calculateScaleFactor(
                framebufferWidth,
                framebufferHeight,
                minecraft.gameSettings.guiScale,
                minecraft.isUnicode());
        int scaledWidth = scaleDimension(framebufferWidth, scaleFactor);
        int scaledHeight = scaleDimension(framebufferHeight, scaleFactor);
        Framebuffer framebuffer = new Framebuffer(framebufferWidth, framebufferHeight, true);
        float targetProgress = clampProgress(progress);
        float startProgress = targetProgress < displayedProgress ? 0.0F : displayedProgress;
        long durationNanos = calculateAnimationDurationNanos(startProgress, targetProgress);
        long animationStartedAt = System.nanoTime();

        try {
            float animationProgress;
            do {
                animationProgress = durationNanos == 0L
                        ? 1.0F
                        : clampProgress((System.nanoTime() - animationStartedAt) / (float) durationNanos);
                float frameProgress = interpolateProgress(
                        startProgress,
                        targetProgress,
                        animationProgress);
                renderFrame(
                        minecraft,
                        textureManager,
                        framebuffer,
                        framebufferWidth,
                        framebufferHeight,
                        scaledWidth,
                        scaledHeight,
                        frameProgress);

                if (animationProgress < 1.0F) {
                    Display.sync(PROGRESS_ANIMATION_FPS);
                }
            } while (animationProgress < 1.0F && !Display.isCloseRequested());

            displayedProgress = targetProgress;
        } finally {
            framebuffer.deleteFramebuffer();
        }
    }

    private static void renderFrame(Minecraft minecraft,
                                    TextureManager textureManager,
                                    Framebuffer framebuffer,
                                    int framebufferWidth,
                                    int framebufferHeight,
                                    int scaledWidth,
                                    int scaledHeight,
                                    float progress) {
        framebuffer.bindFramebuffer(true);
        GlStateManager.viewport(0, 0, framebufferWidth, framebufferHeight);
        configureProjection(scaledWidth, scaledHeight);
        configureRenderState();
        Gui.drawRect(0, 0, scaledWidth, scaledHeight, 0xFF000000);

        textureManager.bindTexture(SPLASH_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.0F);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int logoX = (scaledWidth - DISPLAY_WIDTH) / 2;
        int logoY = (scaledHeight - DISPLAY_HEIGHT) / 2 - 6;
        Gui.drawScaledCustomSizeModalRect(
                logoX,
                logoY,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);

        drawProgressBar(scaledWidth / 2, logoY + DISPLAY_HEIGHT - 8, progress);
        GlStateManager.disableBlend();
        framebuffer.unbindFramebuffer();
        framebuffer.framebufferRender(framebufferWidth, framebufferHeight);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        minecraft.updateDisplay();
    }

    static int calculateScaleFactor(int width, int height, int guiScale, boolean unicode) {
        int maximumScale = guiScale == 0 ? 1000 : guiScale;
        int scaleFactor = 1;

        while (scaleFactor < maximumScale
                && width / (scaleFactor + 1) >= 320
                && height / (scaleFactor + 1) >= 240) {
            scaleFactor++;
        }

        if (unicode && scaleFactor > 1 && scaleFactor % 2 != 0) {
            scaleFactor--;
        }

        return scaleFactor;
    }

    static int scaleDimension(int dimension, int scaleFactor) {
        return (int) Math.ceil((double) dimension / scaleFactor);
    }

    static float clampProgress(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    static int calculateProgressWidth(float progress) {
        return Math.round(PROGRESS_WIDTH * clampProgress(progress));
    }

    static float easeOutExpo(float progress) {
        float clampedProgress = clampProgress(progress);
        if (clampedProgress <= 0.0F || clampedProgress >= 1.0F) {
            return clampedProgress;
        }
        return 1.0F - (float) Math.pow(2.0D, -10.0F * clampedProgress);
    }

    static float interpolateProgress(float startProgress,
                                     float targetProgress,
                                     float animationProgress) {
        float start = clampProgress(startProgress);
        float target = clampProgress(targetProgress);
        return start + (target - start) * easeOutExpo(animationProgress);
    }

    private static long calculateAnimationDurationNanos(float startProgress, float targetProgress) {
        double distance = Math.abs(clampProgress(targetProgress) - clampProgress(startProgress));
        if (distance == 0.0D) {
            return 0L;
        }
        return Math.max(
                MIN_PROGRESS_ANIMATION_NANOS,
                Math.round(FULL_PROGRESS_ANIMATION_NANOS * distance));
    }

    private static void drawProgressBar(int centerX, int y, float progress) {
        int left = centerX - PROGRESS_WIDTH / 2;
        int fillWidth = calculateProgressWidth(progress);

        drawPill(left - 2, y - 2, PROGRESS_WIDTH + 4, PROGRESS_HEIGHT + 4, 0x12FFFFFF);
        drawPill(left - 1, y - 1, PROGRESS_WIDTH + 2, PROGRESS_HEIGHT + 2, 0x38FFFFFF);
        drawPill(left, y, PROGRESS_WIDTH, PROGRESS_HEIGHT, 0xFF111722);

        if (fillWidth > 0) {
            drawPill(left - 1, y - 1, fillWidth + 2, PROGRESS_HEIGHT + 2, 0x24FFFFFF);
            drawHorizontalGradient(left, y, left + fillWidth, y + PROGRESS_HEIGHT,
                    PROGRESS_COLOR, PROGRESS_COLOR);
        }

        int markerX = Math.max(left + 3, Math.min(left + fillWidth, left + PROGRESS_WIDTH - 3));
        drawCatMarker(markerX, y - 6, PROGRESS_COLOR);
    }

    private static void drawPill(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (height < 3 || width < 3) {
            Gui.drawRect(x, y, x + width, y + height, color);
            return;
        }

        Gui.drawRect(x + 1, y, x + width - 1, y + height, color);
        Gui.drawRect(x, y + 1, x + width, y + height - 1, color);
    }

    private static void drawCatMarker(int centerX, int y, int color) {
        Gui.drawRect(centerX - 3, y + 2, centerX + 4, y + 6, color);
        Gui.drawRect(centerX - 3, y, centerX - 1, y + 3, color);
        Gui.drawRect(centerX + 2, y, centerX + 4, y + 3, color);
        Gui.drawRect(centerX - 1, y + 3, centerX, y + 4, 0xCC07101A);
        Gui.drawRect(centerX + 2, y + 3, centerX + 3, y + 4, 0xCC07101A);
    }

    private static void drawHorizontalGradient(int left, int top, int right, int bottom,
                                               int leftColor, int rightColor) {
        if (right <= left) {
            return;
        }

        float leftAlpha = (float) (leftColor >> 24 & 0xFF) / 255.0F;
        float leftRed = (float) (leftColor >> 16 & 0xFF) / 255.0F;
        float leftGreen = (float) (leftColor >> 8 & 0xFF) / 255.0F;
        float leftBlue = (float) (leftColor & 0xFF) / 255.0F;
        float rightAlpha = (float) (rightColor >> 24 & 0xFF) / 255.0F;
        float rightRed = (float) (rightColor >> 16 & 0xFF) / 255.0F;
        float rightGreen = (float) (rightColor >> 8 & 0xFF) / 255.0F;
        float rightBlue = (float) (rightColor & 0xFF) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(right, top, 0.0D).color(rightRed, rightGreen, rightBlue, rightAlpha).endVertex();
        renderer.pos(left, top, 0.0D).color(leftRed, leftGreen, leftBlue, leftAlpha).endVertex();
        renderer.pos(left, bottom, 0.0D).color(leftRed, leftGreen, leftBlue, leftAlpha).endVertex();
        renderer.pos(right, bottom, 0.0D).color(rightRed, rightGreen, rightBlue, rightAlpha).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void configureProjection(int width, int height) {
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
    }

    private static void configureRenderState() {
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
    }
}
