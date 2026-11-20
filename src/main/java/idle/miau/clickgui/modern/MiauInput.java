package idle.miau.clickgui.modern;

import idle.miau.module.setting.impl.TextSetting;

final class MiauInput extends MiauText {
    private final TextSetting value;

    MiauInput(String label, String placeholder, MiauClickGui gui, Runnable submit) {
        this(new TextSetting(label, "", placeholder, 32, submit), gui);
    }

    private MiauInput(TextSetting value, MiauClickGui gui) {
        super(value, null, gui);
        this.value = value;
    }

    void drawAt(MiauRenderer renderer, float x, float y, float width, float mouseX, float mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        renderedHeight = 26;
        draw(renderer, mouseX, mouseY);
    }

    String text() { return field.getText().trim(); }

    void clear() {
        value.setText("");
        field.setText("");
    }

    @Override
    void changed() {}
}
