package idle.miau.mcp.lwjgl;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class LegacyOpenGL {
    private LegacyOpenGL() {
    }

    public static void glShaderSourceARB(int shader, ByteBuffer source) {
        ARBShaderObjects.glShaderSourceARB(shader, MemoryUtil.memUTF8(source));
    }

    public static void glShaderSource(int shader, ByteBuffer source) {
        GL20.glShaderSource(shader, MemoryUtil.memUTF8(source));
    }

    public static void glShaderSource(int shader, CharSequence source) {
        GL20.glShaderSource(shader, source);
    }

    public static void glGetInteger(int parameter, IntBuffer values) {
        GL11.glGetIntegerv(parameter, values);
    }

    public static void glGetFloat(int parameter, FloatBuffer values) {
        GL11.glGetFloatv(parameter, values);
    }
}
