package keystrokesmod.mixin.impl.accessor;

public interface IAccessorGlStateManager {
    static int getActiveTextureUnit() {
        return org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
    }
}
