package idle.miau.clickgui.miau;

import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class MiauStyle {
    static final int FRAME = 0xFF292929;
    static final int DARK = 0xFF191919;
    static final int SEPARATOR = 0xFF101010;
    static final int ACTIVE = 0x96191919;
    static final int ACTIVE_HOVER = 0xB2191919;
    static final int HOVER = 0x96191919;
    static final int MODULE_ROW_EVEN = 0xFF262626;
    static final int MODULE_ROW_ODD = 0xFF202020;
    static final int SETTING_BACKGROUND = 0xFF141414;
    static final int SETTING_ROW_EVEN = 0xFF161616;
    static final int SETTING_ROW_ODD = 0xFF111111;
    static final int WHITE = 0xFFFFFFFF;
    static final int MUTED = 0xFF9E9E9E;
    static final int ROW_HEIGHT = 15;

    private MiauStyle() {
    }

    static MiauFontRenderer categoryFont() {
        return FontManager.getMiauCategoryRenderer();
    }

    static MiauFontRenderer moduleFont() {
        return FontManager.getMiauModuleRenderer();
    }

    static MiauFontRenderer settingFont() {
        return FontManager.getMiauSettingRenderer();
    }

    static boolean hovered(float x, float y, float width, float height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    static int centeredTextY(int top, int height, MiauFontRenderer font) {
        return top + Math.max(1, (height - font.getFontHeight()) / 2);
    }

    static void hoverRow(float x, float y, float width, int mouseX, int mouseY) {
        if (hovered(x, y, width, ROW_HEIGHT, mouseX, mouseY)) {
            Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + ROW_HEIGHT), HOVER);
        }
    }

    static int accent(double x, double y) {
        return ArrayListModule.getHudColorAtPosition(x, y);
    }

    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    static int moduleRowColor(int index) {
        return (index & 1) == 0 ? MODULE_ROW_EVEN : MODULE_ROW_ODD;
    }

    static int settingRowColor(int index) {
        return (index & 1) == 0 ? SETTING_ROW_EVEN : SETTING_ROW_ODD;
    }

    static void glColor(int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.color(red, green, blue, alpha);
    }

    static boolean drawTexture(String name, float x, float y, int width, int height, int color) {
        ResourceLocation texture = RenderUtils.getIcon("/assets/idle.miau/textures/gui/miau/" + name + ".png");
        if (texture == null) {
            return false;
        }
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderUtils.prepareGuiTextureRenderState();
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        glColor(color);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, width, height, width, height);
        RenderUtils.restoreGuiRenderState(depthEnabled, blendEnabled, depthMask);
        return true;
    }

    static String displayName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String normalized = name.replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    static String trim(String text, MiauFontRenderer font, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int limit = Math.max(0, maxWidth - font.getStringWidth(suffix));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = builder.toString() + text.charAt(i);
            if (font.getStringWidth(next) > limit) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder.append(suffix).toString();
    }
}
