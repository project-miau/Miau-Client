package idle.miau.clickgui.modern;

abstract class MiauPanel {
    final MiauClickGui gui;
    float x;
    float y;
    float width;
    float height;

    MiauPanel(MiauClickGui gui) {
        this.gui = gui;
    }

    final void draw(MiauRenderer renderer, float mouseX, float mouseY, float screenWidth, float screenHeight) {
        width = Math.min(420, screenWidth - 24);
        height = Math.min(320, Math.max(80, screenHeight - 64));
        x = (screenWidth - width) / 2;
        y = Math.max(MiauLayout.COLUMN_TOP, (screenHeight - height) / 2);
        renderer.glass(x, y, width, height, 5, 5);
        renderer.varying(x, y, width, 26, 5, 0, 0xd90f0f0f);
        renderer.varying(x, y + 26, width, height - 26, 0, 5, 0xb31e1e2d);
        drawContent(renderer, mouseX, mouseY);
    }

    final void heading(MiauRenderer renderer, String title, String icon, String detail) {
        renderer.text(MiauRenderer.OUTLINED, icon, x + 10, y + 18, 12, MiauRenderer.primary());
        renderer.text(MiauRenderer.BOLD, title, x + 29, y + 17, 10, -1);
        renderer.text(MiauRenderer.MEDIUM, detail,
                x + width - 10 - renderer.width(MiauRenderer.MEDIUM, detail, 8), y + 17, 8, 0xffaaaaaa);
    }

    final void footer(MiauRenderer renderer, String text) {
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, text, 8, width - 20),
                x + 10, y + height - 9, 8, 0xffaaaaaa);
    }

    static void button(MiauRenderer renderer, String label, float x, float y, float width, float height,
                       float mouseX, float mouseY, boolean selected) {
        if (selected) {
            renderer.gradient(x, y, width, height, 3, 3,
                    MiauRenderer.opacity(MiauRenderer.primary(), .4f),
                    MiauRenderer.opacity(MiauRenderer.secondary(), .4f), false);
        } else {
            renderer.rect(x, y, width, height, 3,
                    MiauSetting.inside(mouseX, mouseY, x, y, width, height) ? 0x25ffffff : 0x40000000);
        }
        renderer.text(selected ? MiauRenderer.BOLD : MiauRenderer.MEDIUM, label,
                x + (width - renderer.width(MiauRenderer.MEDIUM, label, 8)) / 2,
                y + height / 2 + 3, 8, selected ? -1 : 0xffcccccc);
    }

    static void scrollbar(MiauRenderer renderer, float x, float top, float height, float content, float offset) {
        if (content <= height || height <= 0) return;
        float thumb = Math.max(12, height * height / content);
        float y = top + (height - thumb) * Math.min(1, Math.max(0, offset / (content - height)));
        renderer.rect(x, top, 2, height, 1, 0x30000000);
        renderer.rect(x, y, 2, thumb, 1, MiauRenderer.opacity(MiauRenderer.primary(), .7f));
    }

    abstract void open();
    abstract void drawContent(MiauRenderer renderer, float mouseX, float mouseY);
    abstract void click(float mouseX, float mouseY, int button);
    abstract void scroll(float mouseX, float mouseY, float steps);
    abstract void cancel();
}
