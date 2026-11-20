package idle.miau.clickgui.modern;

import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

class MiauText extends MiauSetting {
    final GuiTextField field;
    private final TextSetting text;
    private float scroll;
    private MiauRenderer renderer;

    MiauText(TextSetting setting, Module owner, MiauClickGui gui) {
        this(setting, owner, gui, setting.getMaxLength());
    }

    MiauText(Setting setting, Module owner, MiauClickGui gui, int maximum) {
        super(setting, owner, gui);
        text = setting instanceof TextSetting value ? value : null;
        field = new GuiTextField(0, Minecraft.getMinecraft().fontRendererObj, 0, 0, 100, 10);
        field.setMaxStringLength(maximum);
        field.setText(text == null ? "" : text.getText());
    }

    @Override
    float height(MiauRenderer renderer) { return 26; }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        label(renderer, 8.5f);
        drawField(renderer, y + 13);
    }

    void drawField(MiauRenderer renderer, float fieldY) {
        this.renderer = renderer;
        if (text != null && !gui.focused(this) && !text.getText().equals(field.getText())) {
            field.setText(text.getText());
        }
        renderer.outline(x + 5, fieldY, width - 10, 10, 2.5f, 1.5f, 0xff505050);
        renderer.rect(x + 5, fieldY, width - 10, 10, 2.5f, 0xff191919);
        String value = text != null && text.isMasked() ? "•".repeat(field.getText().length()) : field.getText();
        int cursor = Math.min(field.getCursorPosition(), value.length());
        float cursorX = renderer.width(MiauRenderer.MEDIUM, value.substring(0, cursor), 7);
        if (gui.focused(this)) {
            if (cursorX - scroll > width - 17) scroll = cursorX - width + 17;
            if (cursorX < scroll) scroll = cursorX;
        } else scroll = 0;
        renderer.clip(x + 6, fieldY, width - 12, 10, () -> {
            int selection = Math.min(field.getSelectionEnd(), value.length());
            if (gui.focused(this) && cursor != selection) {
                float first = renderer.width(MiauRenderer.MEDIUM, value.substring(0, Math.min(cursor, selection)), 7);
                float last = renderer.width(MiauRenderer.MEDIUM, value.substring(0, Math.max(cursor, selection)), 7);
                renderer.rect(x + 7 + first - scroll, fieldY + 1.5f, last - first, 7, 0, 0xff245292);
            }
            String shown = value.isEmpty() && text != null && !gui.focused(this) ? text.getPlaceholder() : value;
            renderer.text(MiauRenderer.MEDIUM, shown, x + 7 - scroll, fieldY + 7, 7, 0xff808080);
            if (gui.focused(this) && cursor == selection && System.currentTimeMillis() % 1000 > 400) {
                renderer.rect(x + 7.5f + cursorX - scroll, fieldY + 1.5f, .5f, 7, 0, -1);
            }
        });
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (button != 0) return false;
        focusAt(mouseX);
        return true;
    }

    void focusAt(float mouseX) {
        gui.focus(this);
        field.setFocused(true);
        if (renderer == null) return;
        String value = text != null && text.isMasked() ? "•".repeat(field.getText().length()) : field.getText();
        float pointer = mouseX - x - 7 + scroll;
        int cursor = 0;
        for (int index = 1; index <= value.length(); index++) {
            float before = renderer.width(MiauRenderer.MEDIUM, value.substring(0, index - 1), 7);
            float after = renderer.width(MiauRenderer.MEDIUM, value.substring(0, index), 7);
            if (pointer < (before + after) / 2) break;
            cursor = index;
        }
        field.setCursorPosition(cursor);
    }

    @Override
    boolean key(char character, int code) {
        if (code == Keyboard.KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            if (text != null) text.submit();
            cancel();
            return true;
        }
        if (text != null && text.isSensitive() && GuiScreen.isCtrlKeyDown()
                && (code == Keyboard.KEY_C || code == Keyboard.KEY_X)) return true;
        if (field.textboxKeyTyped(character, code)) {
            if (text != null) {
                text.setText(field.getText());
                changed();
            }
            return true;
        }
        return true;
    }

    @Override
    void cancel() {
        field.setFocused(false);
        super.cancel();
    }
}
