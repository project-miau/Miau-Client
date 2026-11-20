package idle.miau.clickgui.panel;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

public final class RenderAccess {
   private static Consumer<DrawPlan> sink = RenderAccess::execute;
   private static DoubleSupplier clock = () -> (double)System.nanoTime() * 1.0E-9;

   private RenderAccess() {
   }

   public static double now() {
      return clock.getAsDouble();
   }

   public static DoubleSupplier bindClock(DoubleSupplier next) {
      DoubleSupplier previous = clock;
      clock = Objects.requireNonNull(next);
      return previous;
   }

   public static Consumer<DrawPlan> bindSink(Consumer<DrawPlan> next) {
      Consumer<DrawPlan> previous = sink;
      sink = Objects.requireNonNull(next);
      return previous;
   }

   public static void releaseCanvasResources() {
      CanvasFonts.release();
   }

   public static void execute(DrawPlan plan) {
      try (GlStateScope attributes = GlStateScope.open()) {
         GL11.glPushMatrix();
         GuiCanvasState canvas = canvas();
         GuiCanvasState.Stats stats = new GuiCanvasState.Stats();
         GlCanvasBackend backend = new GlCanvasBackend();
         int pushes = 0;
         float alpha = 1.0F;
         float savedItemDepth = Minecraft.getMinecraft().getRenderItem().zLevel;
         Deque<Float> alphas = new ArrayDeque<>();

         try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.disableTexture2D();

            for (DrawPlan.Op op : plan.operations()) {
               float[] v = op.values;
               if (op.kind.equals("push")) {
                  GL11.glPushMatrix();
                  canvas.save();
                  pushes++;
                  alphas.push(alpha);
               } else if (op.kind.equals("pop")) {
                  if (pushes <= 0) {
                     throw new IllegalStateException("Unbalanced drawing matrix");
                  }

                  GL11.glPopMatrix();
                  canvas.restore();
                  pushes--;
                  alpha = alphas.pop();
               } else if (op.kind.equals("translate")) {
                  GL11.glTranslatef(v[0], v[1], v[2]);
                  canvas.translate(v[0], v[1]);
               } else if (op.kind.equals("scale")) {
                  GL11.glScalef(v[0], v[1], v[2]);
                  canvas.scale(v[0], v[1]);
               } else if (op.kind.equals("rotate")) {
                  GL11.glRotatef(v[0], v[1], v[2], v[3]);
               } else if (op.kind.equals("color")) {
                  GlStateManager.color(v[0], v[1], v[2], v[3] * alpha);
                  canvas.fillColor(v);
                  canvas.strokeColor(v);
               } else if (op.kind.equals("opacity")) {
                  alpha = v[0];
                  canvas.selected().alpha = alpha;
               } else if (op.kind.equals("screenClip")) {
                  GL11.glEnable(3089);
                  GL11.glScissor((int)v[0], Minecraft.getMinecraft().displayHeight - (int)(v[1] + v[3]), Math.max(0, (int)v[2]), Math.max(0, (int)v[3]));
               } else if (op.kind.equals("unclip")) {
                  GL11.glDisable(3089);
               } else if (op.kind.equals("gradient")) {
                  GuiCanvasState.Paint paint = GuiCanvasState.linearGradient(
                     v[0], v[1], v[0] + v[2], v[1], new float[]{v[4], v[5], v[6], v[7]}, new float[]{v[8], v[9], v[10], v[11]}
                  );
                  canvas.fillPaint(paint);
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).rectangle(v[0], v[1], v[2], v[3]), false, backend, stats);
               } else if (op.kind.equals("lineWidth")) {
                  GL11.glLineWidth(v[0]);
                  canvas.selected().lineWidth = v[0];
               } else if (op.kind.equals("enable")) {
                  capability((int)v[0], true);
               } else if (op.kind.equals("disable")) {
                  capability((int)v[0], false);
               } else if (op.kind.equals("vertices2") && op.value == Boolean.TRUE) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).command(0.0F, v[1], v[2], 1.0F, v[3], v[4]), true, backend, stats);
               } else if (op.kind.startsWith("vertices")) {
                  int dimensions = op.kind.equals("vertices2") ? 2 : 3;
                  GL11.glBegin((int)v[0]);

                  try {
                     for (int i = 1; i < v.length; i += dimensions) {
                        GL11.glVertex3f(v[i], v[i + 1], dimensions == 3 ? v[i + 2] : 0.0F);
                     }
                  } finally {
                     GL11.glEnd();
                  }
               } else if (op.kind.equals("roundedRect") || op.kind.equals("roundedStroke")) {
                  shape(
                     canvas,
                     new CanvasPath(0.25F, 0.01F, 1.0F).rounded(v[0], v[1], v[2], v[3], v[4], v[4], v[4], v[4]),
                     op.kind.equals("roundedStroke"),
                     backend,
                     stats
                  );
               } else if (op.kind.equals("roundedRectVarying")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).rounded(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]), false, backend, stats);
               } else if (op.kind.equals("circle")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).ellipse(v[0], v[1], v[2], v[2]), false, backend, stats);
               } else if (op.kind.equals("circleStroke")) {
                  shape(canvas, new CanvasPath(0.25F, 0.01F, 1.0F).ellipse(v[0], v[1], v[2], v[2]), true, backend, stats);
               } else if (op.kind.equals("pillGradient")) {
                  pillGradient(canvas.selected().transform, v, alpha);
               } else if (op.kind.equals("itemDepth")) {
                  Minecraft.getMinecraft().getRenderItem().zLevel = v[0];
               } else if (op.kind.equals("text") && op.value instanceof CanvasFonts.Text) {
                  CanvasFonts.render((CanvasFonts.Text)op.value, Float.floatToRawIntBits(v[2]), alpha);
               } else if (op.kind.equals("text")) {
                  GlStateManager.enableTexture2D();
                  GL11.glPushMatrix();

                  try {
                     float factor = v[4] / (float)Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
                     GL11.glTranslatef(v[0], v[1], 0.0F);
                     GL11.glScalef(factor, factor, 1.0F);
                     int color = Float.floatToRawIntBits(v[2]);
                     int opacity = (int)((float)(color >>> 24) * alpha);
                     Minecraft.getMinecraft().fontRendererObj.drawString((String)op.value, 0.0F, 0.0F, opacity << 24 | color & 16777215, v[3] != 0.0F);
                  } finally {
                     GL11.glPopMatrix();
                     GlStateManager.disableTexture2D();
                  }
               } else if (op.kind.equals("item")) {
                  GlStateManager.enableTexture2D();
                  RenderHelper.enableGUIStandardItemLighting();

                  try {
                     Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI((ItemStack)op.value, (int)v[0], (int)v[1]);
                     Minecraft.getMinecraft().getRenderItem().renderItemOverlays(Minecraft.getMinecraft().fontRendererObj, (ItemStack)op.value, (int)v[0], (int)v[1]);
                  } finally {
                     RenderHelper.disableStandardItemLighting();
                     GlStateManager.disableTexture2D();
                  }
               }
            }
         } finally {
            Minecraft.getMinecraft().getRenderItem().zLevel = savedItemDepth;

            while (pushes-- > 0) {
               GL11.glPopMatrix();
            }

            GL11.glPopMatrix();
         }
      }
   }

   private static void pillGradient(float[] transform, float[] v, float alpha) {
      float x = v[0];
      float y = v[1];
      float width = v[2];
      float height = v[3];
      float radius = Math.max(0.0F, Math.min(v[4], Math.min(height, width) * 0.5F));
      int stops = (v.length - 5) / 4;
      if (stops >= 1 && !(width <= 0.0F) && !(height <= 0.0F)) {
         TreeSet<Float> columns = new TreeSet<>();
         int capSteps = Math.max(4, (int)Math.ceil((double)(radius * 2.0F)));

         for (int i = 0; i <= capSteps; i++) {
            float inset = radius - radius * (float)Math.cos((Math.PI / 2) * (double)i / (double)capSteps);
            columns.add(x + inset);
            columns.add(x + width - inset);
         }

         for (int i = 0; i < stops; i++) {
            columns.add(x + (stops == 1 ? 0.0F : width * (float)i / (float)(stops - 1)));
         }

         float middle = y + height * 0.5F;
         float straight = height * 0.5F - radius;
         float[] color = new float[4];
         GL11.glShadeModel(7425);
         GL11.glPushMatrix();
         GL11.glLoadIdentity();
         GL11.glBegin(5);

         try {
            for (float px : columns) {
               float dx = px < x + radius ? x + radius - px : (px > x + width - radius ? px - (x + width - radius) : 0.0F);
               float half = straight + (float)Math.sqrt((double)Math.max(0.0F, radius * radius - dx * dx));
               if (stops == 1) {
                  System.arraycopy(v, 5, color, 0, 4);
               } else {
                  float position = Math.max(0.0F, Math.min(1.0F, (px - x) / width)) * (float)(stops - 1);
                  int index = Math.min(stops - 2, (int)position);
                  float f = position - (float)index;

                  for (int c = 0; c < 4; c++) {
                     color[c] = v[5 + index * 4 + c] + (v[5 + (index + 1) * 4 + c] - v[5 + index * 4 + c]) * f;
                  }
               }

               GL11.glColor4f(color[0], color[1], color[2], color[3] * alpha);
               vertex(transform, px, middle - half);
               vertex(transform, px, middle + half);
            }
         } finally {
            GL11.glEnd();
            GL11.glPopMatrix();
         }
      }
   }

   private static void vertex(float[] m, float x, float y) {
      GL11.glVertex2f(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]);
   }

   private static void capability(int value, boolean enable) {
      switch (value) {
         case 2884:
            if (enable) {
               GlStateManager.enableCull();
            } else {
               GlStateManager.disableCull();
            }
            break;
         case 2896:
            if (enable) {
               GlStateManager.enableLighting();
            } else {
               GlStateManager.disableLighting();
            }
            break;
         case 2929:
            if (enable) {
               GlStateManager.enableDepth();
            } else {
               GlStateManager.disableDepth();
            }
            break;
         case 3008:
            if (enable) {
               GlStateManager.enableAlpha();
            } else {
               GlStateManager.disableAlpha();
            }
            break;
         case 3042:
            if (enable) {
               GlStateManager.enableBlend();
            } else {
               GlStateManager.disableBlend();
            }
            break;
         case 3553:
            if (enable) {
               GlStateManager.enableTexture2D();
            } else {
               GlStateManager.disableTexture2D();
            }
            break;
         default:
            if (enable) {
               GL11.glEnable(value);
            } else {
               GL11.glDisable(value);
            }
      }
   }

   public static GuiCanvasState canvas() {
      GuiCanvasState.State initial = new GuiCanvasState.State();
      initial.transform[0] = initial.transform[3] = 1.0F;
      initial.alpha = 1.0F;
      initial.lineWidth = 1.0F;
      initial.miter = 10.0F;
      initial.cap = 0;
      initial.join = 1;
      initial.shapeAntialias = 1;
      initial.scissorExtent[0] = initial.scissorExtent[1] = -1.0F;
      initial.composite[0] = 770;
      initial.composite[2] = 1;
      initial.composite[1] = initial.composite[3] = 771;
      return new GuiCanvasState(initial);
   }

   private static void shape(GuiCanvasState canvas, CanvasPath path, boolean stroke, GuiCanvasState.Backend backend, GuiCanvasState.Stats stats) {
      path.transform(canvas.selected().transform);
      GL11.glPushMatrix();

      try {
         GL11.glLoadIdentity();
         if (stroke) {
            canvas.stroke(path, backend, stats, true);
         } else {
            canvas.fill(path, backend, stats, true);
         }
      } finally {
         GL11.glPopMatrix();
      }
   }
}
