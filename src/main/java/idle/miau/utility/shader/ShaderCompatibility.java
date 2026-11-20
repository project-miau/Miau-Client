package idle.miau.utility.shader;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.Display;

public final class ShaderCompatibility {
    private ShaderCompatibility() {
    }

    public static boolean isAvailable() {
        try {
            return Display.isCreated()
                    && !OpenGlHelper.isReducedRenderMode()
                    && OpenGlHelper.areShadersSupported()
                    && OpenGlHelper.isFramebufferEnabled();
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
