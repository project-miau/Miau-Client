package keystrokesmod.utility;

import idle.miau.performance.MiauDrawCalls;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

public class RenderUtils extends idle.miau.utility.RenderUtils {
    private static final Frustum frustum = new Frustum();

    public static void glColor(int n) {
        GL11.glColor4f((float) (n >> 16 & 0xFF) / 255.0f, (float) (n >> 8 & 0xFF) / 255.0f, (float) (n & 0xFF) / 255.0f, (float) (n >> 24 & 0xFF) / 255.0f);
    }

    public static boolean isInViewFrustum(Entity entity) {
        if (entity == null) return false;
        return isInViewFrustum(entity.getEntityBoundingBox()) || entity.ignoreFrustumCheck;
    }

    public static boolean isInViewFrustum(AxisAlignedBB bb) {
        if (bb == null) return false;
        Entity view = mc.getRenderViewEntity();
        if (view == null) return true;
        frustum.setPosition(view.posX, view.posY, view.posZ);
        return frustum.isBoundingBoxInFrustum(bb);
    }

    public static void drawRoundedRectangle(float x, float y, float x2, float y2, float radius, int color) {
        if (x2 <= x || y2 <= y) return;
        radius = Math.max(0.0f, Math.min(radius, Math.min(x2 - x, y2 - y)));
        x *= 2.0f;
        y *= 2.0f;
        x2 *= 2.0f;
        y2 *= 2.0f;
        GL11.glPushMatrix();
        GL11.glPushAttrib(1048575);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glBegin(9);
        glColor(color);
        for (int i = 0; i <= 90; i += 3) {
            double n7 = (float) i * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x + radius) + Math.sin(n7) * (double) radius * -1.0, (double) (y + radius) + Math.cos(n7) * (double) radius * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            double n8 = (float) j * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x + radius) + Math.sin(n8) * (double) radius * -1.0, (double) (y2 - radius) + Math.cos(n8) * (double) radius * -1.0);
        }
        for (int k = 0; k <= 90; ++k) {
            double n9 = (float) k * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x2 - radius) + Math.sin(n9) * (double) radius, (double) (y2 - radius) + Math.cos(n9) * (double) radius);
        }
        for (int l = 90; l <= 180; ++l) {
            double n10 = (float) l * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x2 - radius) + Math.sin(n10) * (double) radius, (double) (y + radius) + Math.cos(n10) * (double) radius);
        }
        MiauDrawCalls.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GlStateManager.resetColor();
    }

    public static void drawRoundedGradientRect(float x, float y, float x2, float y2, float radius, int n6, int n7, int n8, int n9) {
        if (x2 <= x) return;
        float width = x2 - x;
        if (width < 3.0f) radius = Math.min(radius, width / 2.0f);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glShadeModel(7425);
        GL11.glPushMatrix();
        GL11.glPushAttrib(1048575);
        GL11.glScaled(0.5, 0.5, 0.5);
        x *= 2.0f;
        y *= 2.0f;
        x2 *= 2.0f;
        y2 *= 2.0f;
        glColor(n6);
        GL11.glBegin(9);
        for (int i = 0; i <= 90; i += 3) {
            double n10 = (float) i * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x + radius) + Math.sin(n10) * (double) radius * -1.0, (double) (y + radius) + Math.cos(n10) * (double) radius * -1.0);
        }
        glColor(n7);
        for (int j = 90; j <= 180; j += 3) {
            double n11 = (float) j * ((float) Math.PI / 180);
            GL11.glVertex2d((double) (x + radius) + Math.sin(n11) * (double) radius * -1.0, (double) (y2 - radius) + Math.cos(n11) * (double) radius * -1.0);
        }
        if ((double) (x2 - x) >= 4.5) {
            glColor(n8);
            for (int k = 0; k <= 90; k += 3) {
                double n12 = (float) k * ((float) Math.PI / 180);
                GL11.glVertex2d((double) (x2 - radius) + Math.sin(n12) * (double) radius, (double) (y2 - radius) + Math.cos(n12) * (double) radius);
            }
            glColor(n9);
            for (int l = 90; l <= 180; l += 3) {
                double n13 = (float) l * ((float) Math.PI / 180);
                GL11.glVertex2d((double) (x2 - radius) + Math.sin(n13) * (double) radius, (double) (y + radius) + Math.cos(n13) * (double) radius);
            }
        }
        MiauDrawCalls.glEnd();
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GlStateManager.resetColor();
    }
}
