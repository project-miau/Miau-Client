package idle.miau.utility.shader;

import idle.miau.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class BlurUtils {
    private static Framebuffer stencilFrameBufferBlur;
    private static Framebuffer stencilFrameBufferBloom;

    public static boolean prepareBlur() {
        if (!ShaderCompatibility.isAvailable()) {
            return false;
        }
        stencilFrameBufferBlur = RenderUtils.createFrameBuffer(stencilFrameBufferBlur);
        if (stencilFrameBufferBlur == null) {
            return false;
        }
        stencilFrameBufferBlur.framebufferClear();
        stencilFrameBufferBlur.bindFramebuffer(false);
        return true;
    }

    public static boolean prepareBloom() {
        if (!ShaderCompatibility.isAvailable()) {
            return false;
        }
        stencilFrameBufferBloom = RenderUtils.createFrameBuffer(stencilFrameBufferBloom);
        if (stencilFrameBufferBloom == null) {
            return false;
        }
        stencilFrameBufferBloom.framebufferClear();
        stencilFrameBufferBloom.bindFramebuffer(false);
        return true;
    }

    public static boolean blurEnd(int passes, float radius) {
        return renderEffect(passes, radius, false);
    }

    public static boolean bloomEnd(int passes, float radius) {
        return renderEffect(passes, radius, true);
    }

    private static boolean renderEffect(int passes, float radius, boolean bloom) {
        Framebuffer mask = bloom ? stencilFrameBufferBloom : stencilFrameBufferBlur;
        if (mask == null || !ShaderCompatibility.isAvailable()) {
            return false;
        }
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            if (!bloom) {
                return KawaseBlur.renderBlur(mask.framebufferTexture, passes, radius);
            }
            KawaseBloom.renderBlur(mask.framebufferTexture, passes, radius);
            return true;
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        finally {
            restoreDepth(depthEnabled, depthWrite);
        }
    }

    public static boolean renderBlur(Runnable renderMask, int passes, float radius) {
        return renderMasked(renderMask, passes, radius, false);
    }

    public static boolean renderBloom(Runnable renderMask, int passes, float radius) {
        return renderMasked(renderMask, passes, radius, true);
    }

    private static boolean renderMasked(Runnable renderMask, int passes, float radius, boolean bloom) {
        if (renderMask == null || !ShaderCompatibility.isAvailable()) {
            return false;
        }
        boolean prepared = false;
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            prepared = bloom ? prepareBloom() : prepareBlur();
            if (!prepared) {
                return false;
            }
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            renderMask.run();
            return bloom ? bloomEnd(passes, radius) : blurEnd(passes, radius);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        finally {
            if (prepared) {
                try {
                    GL20.glUseProgram(0);
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
                    GlStateManager.bindTexture(0);
                    Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
                    restoreDepth(depthEnabled, depthWrite);
                    RenderUtils.resetColor();
                }
                catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
    }

    private static void restoreDepth(boolean enabled, boolean write) {
        GlStateManager.depthMask(write);
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
    }
}
