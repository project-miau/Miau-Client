package idle.miau.utility.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

public final class MainMenuBackgroundShader {
    static final String FRAGMENT_RESOURCE =
            "minecraft:miau/shaders/main_menu/background.frag";

    private static final Logger LOGGER = LogManager.getLogger(MainMenuBackgroundShader.class);
    private static ShaderUtils shader;
    private static boolean unavailable;

    private MainMenuBackgroundShader() {
    }

    public static boolean render(float elapsedSeconds) {
        return render(elapsedSeconds, 0xFF40BFFF, 0xFFFF8099);
    }

    public static boolean render(float elapsedSeconds, int primaryColor, int secondaryColor) {
        ShaderUtils activeShader = getShader();
        if (activeShader == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0) {
            return false;
        }

        float time = Float.isFinite(elapsedSeconds)
                ? Math.max(0.0F, elapsedSeconds)
                : 0.0F;
        boolean shaderBound = false;
        try {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableAlpha();

            activeShader.init();
            shaderBound = true;
            activeShader.setUniformf(
                    "resolution", minecraft.displayWidth, minecraft.displayHeight);
            activeShader.setUniformf("time", time);
            setColorUniform(activeShader, "primaryColor", primaryColor);
            setColorUniform(activeShader, "secondaryColor", secondaryColor);
            ShaderUtils.drawQuads();
            return true;
        }
        catch (RuntimeException | LinkageError exception) {
            markUnavailable("The Rise main-menu background shader failed while rendering", exception);
            return false;
        }
        finally {
            if (shaderBound) {
                try {
                    activeShader.unload();
                }
                catch (RuntimeException | LinkageError ignored) {
                }
            }
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void setColorUniform(ShaderUtils activeShader, String name, int color) {
        activeShader.setUniformf(
                name,
                colorChannel(color, 16),
                colorChannel(color, 8),
                colorChannel(color, 0));
    }

    static float colorChannel(int color, int shift) {
        return (color >> shift & 0xFF) / 255.0F;
    }

    private static synchronized ShaderUtils getShader() {
        if (shader != null) {
            return shader;
        }
        if (unavailable || !isSupported()) {
            return null;
        }

        try {
            shader = new ShaderUtils(FRAGMENT_RESOURCE);
            return shader;
        }
        catch (RuntimeException | LinkageError exception) {
            markUnavailable("The Rise main-menu background shader is unavailable", exception);
            return null;
        }
    }

    private static boolean isSupported() {
        try {
            return hasRequiredCapabilities(Display.isCreated(), OpenGlHelper.openGL21);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static boolean hasRequiredCapabilities(boolean displayCreated, boolean openGL21) {
        return displayCreated && openGL21;
    }

    private static synchronized void markUnavailable(String message, Throwable exception) {
        if (!unavailable) {
            unavailable = true;
            LOGGER.warn(message, exception);
        }
    }
}
