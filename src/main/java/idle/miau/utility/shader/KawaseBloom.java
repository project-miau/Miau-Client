package idle.miau.utility.shader;

import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class KawaseBloom implements IMinecraftInstance {
    public static ShaderUtils kawaseDown = new ShaderUtils("kawaseDownBloom");
    public static ShaderUtils kawaseUp = new ShaderUtils("kawaseUpBloom");
    public static Framebuffer framebuffer = new Framebuffer(1, 1, false);
    private static int currentIterations;

    private static final List<Framebuffer> framebufferList = new ArrayList<>();

    private static void initFramebuffers(float iterations) {
        for (Framebuffer framebuffer : framebufferList) {
            framebuffer.deleteFramebuffer();
        }
        framebufferList.clear();

        framebufferList.add(framebuffer = RenderUtils.createFrameBuffer(null, false));

        for (int i = 1; i <= iterations; i++) {
            int width = Math.max(1, (int) (mc.displayWidth / Math.pow(2, i)));
            int height = Math.max(1, (int) (mc.displayHeight / Math.pow(2, i)));
            Framebuffer currentBuffer = new Framebuffer(width, height, false);
            currentBuffer.setFramebufferFilter(GL_LINEAR);

            GlStateManager.bindTexture(currentBuffer.framebufferTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GlStateManager.bindTexture(0);

            framebufferList.add(currentBuffer);
        }
    }

    public static void renderBlur(int framebufferTexture, int iterations, float offset) {
        if (iterations < 1) {
            mc.getFramebuffer().bindFramebuffer(true);
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);

        try {
            if (currentIterations != iterations || (framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight)) {
                initFramebuffers(iterations);
                currentIterations = iterations;
            }
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);

            RenderUtils.setAlphaLimit(0);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL_ONE, GL_ONE);

            GL11.glClearColor(0, 0, 0, 0);

            float currentOffset = offset;
            renderFBO(framebufferList.get(1), framebufferTexture, kawaseDown, currentOffset);

            for (int i = 1; i < iterations; i++) {
                currentOffset = offset / (float) Math.pow(1.5, i);
                renderFBO(framebufferList.get(i + 1), framebufferList.get(i).framebufferTexture, kawaseDown, currentOffset);
            }

            for (int i = iterations; i > 1; i--) {
                currentOffset = offset / (float) Math.pow(1.5, i - 1);
                renderFBO(framebufferList.get(i - 1), framebufferList.get(i).framebufferTexture, kawaseUp, currentOffset);
            }

            Framebuffer lastBuffer = framebufferList.get(0);
            lastBuffer.framebufferClear();
            lastBuffer.bindFramebuffer(true);
            kawaseUp.init();
            kawaseUp.setUniformf("offset", offset, offset);
            kawaseUp.setUniformi("inTexture", 0);
            kawaseUp.setUniformi("check", 1);
            kawaseUp.setUniformi("textureToCheck", 1);
            kawaseUp.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight);
            kawaseUp.setUniformf("iResolution", lastBuffer.framebufferWidth, lastBuffer.framebufferHeight);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
            RenderUtils.bindTexture(framebufferTexture);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            RenderUtils.bindTexture(framebufferList.get(1).framebufferTexture);
            ShaderUtils.drawQuads();
            kawaseUp.unload();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
            GlStateManager.bindTexture(0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);

            GlStateManager.clearColor(0, 0, 0, 0);
            mc.getFramebuffer().bindFramebuffer(false);
            RenderUtils.bindTexture(framebufferList.get(0).framebufferTexture);
            RenderUtils.setAlphaLimit(0);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            RenderUtils.resetColor();
            ShaderUtils.drawQuads();
            GlStateManager.bindTexture(0);
            RenderUtils.setAlphaLimit(0);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    private static void renderFBO(Framebuffer framebuffer, int framebufferTexture, ShaderUtils shader, float offset) {
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);
        shader.init();
        RenderUtils.bindTexture(framebufferTexture);
        shader.setUniformf("offset", offset, offset);
        shader.setUniformi("inTexture", 0);
        shader.setUniformi("check", 0);
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight);
        shader.setUniformf("iResolution", framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        ShaderUtils.drawQuads();
        shader.unload();
    }
}
