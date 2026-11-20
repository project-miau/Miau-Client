package idle.miau.clickgui.miau;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.utility.RenderUtils;
import net.minecraft.client.gui.Gui;

import java.awt.Color;

final class MiauColorComponent extends MiauSettingComponent {
    private final ColorSetting color;
    private boolean draggingColor;
    private boolean draggingHue;
    private boolean draggingAlpha;

    MiauColorComponent(ColorSetting setting, Module owner) {
        super(setting, owner);
        color = setting;
    }

    @Override
    int getHeight() {
        return color.hasAlpha() ? 136 : 125;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        float left = x + 2;
        float pickerTop = y + 4;
        float pickerWidth = Math.max(20, width - 10);
        float pickerHeight = 96;
        float hueTop = pickerTop + pickerHeight + 8;
        int hueColor = Color.HSBtoRGB(color.getHue() / 360.0F, 1.0F, 1.0F);

        RenderUtils.drawHorizontalGradientRect(left, pickerTop, left + pickerWidth, pickerTop + pickerHeight, 0xFFFFFFFF, 0xFF000000 | hueColor);
        RenderUtils.drawVerticalGradientRect(left, pickerTop, left + pickerWidth, pickerTop + pickerHeight, 0x00000000, 0xFF000000);
        drawHueBar(left, hueTop, pickerWidth, 8);

        float colorX = left + color.getSaturation() * pickerWidth;
        float colorY = pickerTop + (1.0F - color.getBrightness()) * pickerHeight;
        outlineMarker(colorX, colorY);
        outlineVertical(left + color.getHue() / 360.0F * pickerWidth, hueTop, 8);

        if (color.hasAlpha()) {
            float alphaTop = hueTop + 12;
            Gui.drawRect((int) left, (int) alphaTop, (int) (left + pickerWidth), (int) (alphaTop + 8), 0xFF191919);
            RenderUtils.drawHorizontalGradientRect(left, alphaTop, left + pickerWidth, alphaTop + 8, color.getRGB(), color.getColor());
            outlineVertical(left + color.getAlpha() / 255.0F * pickerWidth, alphaTop, 8);
        }

        if (draggingColor) {
            float saturation = clamp((mouseX - left) / pickerWidth);
            float brightness = 1.0F - clamp((mouseY - pickerTop) / pickerHeight);
            color.setFromHSB(color.getHue(), saturation, brightness);
            markChanged();
        }
        if (draggingHue) {
            color.setHue(clamp((mouseX - left) / pickerWidth) * 360.0F);
            markChanged();
        }
        if (draggingAlpha && color.hasAlpha()) {
            color.setAlpha(Math.round(clamp((mouseX - left) / pickerWidth) * 255.0F));
            markChanged();
        }

        if (hovered(mouseX, mouseY)) {
            String label = MiauStyle.trim(color.getName(), MiauStyle.moduleFont(), 110);
            int labelWidth = MiauStyle.moduleFont().getStringWidth(label) + 4;
            Gui.drawRect(mouseX + 5, mouseY - 3, mouseX + 5 + labelWidth, mouseY + 9, MiauStyle.DARK);
            MiauStyle.moduleFont().drawString(label, mouseX + 7, mouseY - 2, MiauStyle.WHITE, false);
        }
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !hovered(mouseX, mouseY)) {
            return false;
        }
        float left = x + 2;
        float pickerTop = y + 4;
        float pickerWidth = Math.max(20, width - 10);
        float hueTop = pickerTop + 104;
        draggingColor = MiauStyle.hovered(left, pickerTop, pickerWidth, 96, mouseX, mouseY);
        draggingHue = MiauStyle.hovered(left, hueTop, pickerWidth, 8, mouseX, mouseY);
        draggingAlpha = color.hasAlpha() && MiauStyle.hovered(left, hueTop + 12, pickerWidth, 8, mouseX, mouseY);
        return draggingColor || draggingHue || draggingAlpha;
    }

    @Override
    void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            draggingColor = false;
            draggingHue = false;
            draggingAlpha = false;
        }
    }

    @Override
    void onGuiClosed() {
        draggingColor = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    private static void drawHueBar(float left, float top, float width, float height) {
        for (int i = 0; i < 6; i++) {
            float start = i / 6.0F;
            float end = (i + 1) / 6.0F;
            RenderUtils.drawHorizontalGradientRect(
                    left + width * start,
                    top,
                    left + width * end,
                    top + height,
                    0xFF000000 | Color.HSBtoRGB(start, 1.0F, 1.0F),
                    0xFF000000 | Color.HSBtoRGB(end, 1.0F, 1.0F));
        }
    }

    private static void outlineMarker(float x, float y) {
        Gui.drawRect((int) x - 2, (int) y - 2, (int) x + 3, (int) y - 1, MiauStyle.WHITE);
        Gui.drawRect((int) x - 2, (int) y + 2, (int) x + 3, (int) y + 3, MiauStyle.WHITE);
        Gui.drawRect((int) x - 2, (int) y - 2, (int) x - 1, (int) y + 3, MiauStyle.WHITE);
        Gui.drawRect((int) x + 2, (int) y - 2, (int) x + 3, (int) y + 3, MiauStyle.WHITE);
    }

    private static void outlineVertical(float x, float top, float height) {
        Gui.drawRect((int) x - 1, (int) top - 1, (int) x + 2, (int) (top + height + 1), MiauStyle.WHITE);
        Gui.drawRect((int) x, (int) top, (int) x + 1, (int) (top + height), MiauStyle.DARK);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
