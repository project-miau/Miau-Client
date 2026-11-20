package idle.miau.utility.render;

import idle.miau.performance.MiauDrawCalls;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class ExplosionRenderer {
    private static final int LATITUDES = 16;
    private static final int LONGITUDES = 32;
    private static final Vec3[][] VERTICES = createVertices();

    private ExplosionRenderer() {
    }

    public static void renderSphere(Vec3 center, double radius, int color, float fillAlpha,
                                    float lineAlpha, boolean throughWalls) {
        if (radius <= 0.0D) {
            return;
        }

        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(center.xCoord, center.yCoord, center.zCoord);
            GL11.glScaled(radius, radius, radius);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (throughWalls) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glDepthMask(false);

            if (fillAlpha > 0.0F) {
                GL11.glColor4f(red, green, blue, fillAlpha);
                GL11.glBegin(GL11.GL_TRIANGLES);
                for (int latitude = 0; latitude < LATITUDES; latitude++) {
                    for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                        vertex(latitude, longitude);
                        vertex(latitude + 1, longitude);
                        vertex(latitude + 1, longitude + 1);
                        vertex(latitude, longitude);
                        vertex(latitude + 1, longitude + 1);
                        vertex(latitude, longitude + 1);
                    }
                }
                MiauDrawCalls.glEnd();
            }

            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1.0F);
            GL11.glColor4f(red, green, blue, lineAlpha);
            GL11.glBegin(GL11.GL_LINES);
            for (int latitude = 0; latitude < LATITUDES; latitude++) {
                for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                    vertex(latitude, longitude);
                    vertex(latitude, longitude + 1);
                    vertex(latitude, longitude);
                    vertex(latitude + 1, longitude);
                    vertex(latitude, longitude);
                    vertex(latitude + 1, longitude + 1);
                }
            }
            MiauDrawCalls.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static Vec3[][] createVertices() {
        Vec3[][] vertices = new Vec3[LATITUDES + 1][LONGITUDES + 1];
        for (int latitude = 0; latitude <= LATITUDES; latitude++) {
            double pitch = Math.PI * latitude / LATITUDES;
            double horizontal = Math.sin(pitch);
            for (int longitude = 0; longitude <= LONGITUDES; longitude++) {
                double yaw = Math.PI * 2.0D * longitude / LONGITUDES;
                vertices[latitude][longitude] = new Vec3(
                        horizontal * Math.cos(yaw), Math.cos(pitch), horizontal * Math.sin(yaw));
            }
        }
        return vertices;
    }

    private static void vertex(int latitude, int longitude) {
        Vec3 vertex = VERTICES[latitude][longitude];
        GL11.glVertex3d(vertex.xCoord, vertex.yCoord, vertex.zCoord);
    }
}
