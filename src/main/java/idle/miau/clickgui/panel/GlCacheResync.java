package idle.miau.clickgui.panel;

import net.minecraft.client.renderer.GlStateManager;

final class GlCacheResync {
   private GlCacheResync() {
   }

   static void run() {
      GlStateManager.resetColor();
      GlStateManager.enableTexture2D();
      GlStateManager.disableBlend();
      GlStateManager.enableAlpha();
      GlStateManager.alphaFunc(516, 0.1F);
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      GlStateManager.shadeModel(7424);
   }
}
