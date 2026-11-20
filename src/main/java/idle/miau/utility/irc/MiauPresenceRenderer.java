package idle.miau.utility.irc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class MiauPresenceRenderer {
    private static final ResourceLocation LOGO = new ResourceLocation("miau", "images/cat_presence.png");
    private static final int GAP = 1;

    private MiauPresenceRenderer() {
    }

    public static int width(int size) {
        return size + GAP;
    }

    public static void draw(float x, float y, int size, int color) {
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float alpha = (color >>> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue, alpha);
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        Gui.drawModalRectWithCustomSizedTexture((int)x, (int)y, 0.0F, 0.0F, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
