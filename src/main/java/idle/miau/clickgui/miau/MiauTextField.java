package idle.miau.clickgui.miau;

import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

final class MiauTextField {
    private static int nextId;

    private final GuiTextField field;
    private final String placeholder;
    private boolean masked;

    MiauTextField(String placeholder, int maxLength) {
        this.placeholder = placeholder == null ? "" : placeholder;
        field = new GuiTextField(nextId++, Minecraft.getMinecraft().fontRendererObj, 0, 0, 112, 14);
        field.setMaxStringLength(Math.max(1, maxLength));
        field.setEnableBackgroundDrawing(false);
        field.setCanLoseFocus(true);
    }

    void draw(float x, float y, float width) {
        RoundedUtils.drawRound(x, y, width, 14.0F, 3.0F,
                field.isFocused() ? 0xFF202020 : MiauStyle.DARK);
        int underline = field.isFocused()
                ? MiauStyle.accent(x + width * 0.5D, y + 7.0D)
                : 0xFF333333;
        RoundedUtils.drawRound(x, y + 12.0F, width, 2.0F, 1.0F, underline);
        String value = field.getText();
        if (masked && !value.isEmpty()) {
            StringBuilder hidden = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                hidden.append('\u2022');
            }
            value = hidden.toString();
        }
        if (value.isEmpty() && !field.isFocused()) {
            value = placeholder;
        }
        MiauFontRenderer font = MiauStyle.settingFont();
        value = trimFromLeft(value, Math.max(0, (int) width - 5));
        int color = field.getText().isEmpty() && !field.isFocused() ? MiauStyle.MUTED : MiauStyle.WHITE;
        if (field.isFocused() && System.currentTimeMillis() / 500L % 2L == 0L) {
            value += "_";
        }
        font.drawString(value, x + 3, y + 3, color, false);
        field.updateCursorCounter();
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!field.isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            field.setFocused(false);
            return true;
        }
        return field.textboxKeyTyped(typedChar, keyCode);
    }

    void focus() {
        field.setFocused(true);
        field.setCursorPositionEnd();
    }

    void unfocus() {
        field.setFocused(false);
    }

    boolean isFocused() {
        return field.isFocused();
    }

    String getText() {
        return field.getText();
    }

    void setText(String text) {
        field.setText(text == null ? "" : text);
    }

    void setMasked(boolean masked) {
        this.masked = masked;
    }

    private static String trimFromLeft(String text, int maxWidth) {
        MiauFontRenderer font = MiauStyle.settingFont();
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        int start = 0;
        while (start < text.length() && font.getStringWidth(text.substring(start)) > maxWidth) {
            start++;
        }
        return text.substring(start);
    }
}
