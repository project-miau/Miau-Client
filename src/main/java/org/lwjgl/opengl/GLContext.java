
package org.lwjgl.opengl;

public class GLContext {
    private static final ThreadLocal<ContextCapabilities> capabilities = new ThreadLocal<>();

    public static ContextCapabilities getCapabilities() {
        GLCapabilities current = GL.getCapabilities();
        ContextCapabilities cached = capabilities.get();
        if (cached == null || cached.cap != current) {
            cached = new ContextCapabilities(current);
            capabilities.set(cached);
        }
        return cached;
    }
}
