package idle.miau.clickgui.modern;

import idle.miau.module.Module;

final class MiauModuleRow {
    private final MiauClickGui gui;
    private final MiauRenderer renderer;
    final Module module;
    final MiauSettings settings;
    final MiauAnimation expansion = new MiauAnimation(125, MiauAnimation.Curve.DECELERATE);
    final MiauAnimation enabled = new MiauAnimation(150, MiauAnimation.Curve.DECELERATE);
    boolean expanded;
    float x;
    float y;
    float width = MiauLayout.COLUMN_WIDTH;
    float renderedHeight;

    MiauModuleRow(Module module, MiauClickGui gui, MiauRenderer renderer) {
        this.gui = gui;
        this.renderer = renderer;
        this.module = module;
        settings = new MiauSettings(module, gui);
        enabled.reset(module.isEnabled() ? 1 : 0);
    }

    float height() {
        float progress = expansion.to(expanded ? 1 : 0);
        renderedHeight = MiauLayout.ROW_HEIGHT + (progress > 0 ? progress * (settings.height(renderer, width - 10) + 6) : 0);
        return renderedHeight;
    }

    void draw(float mouseX, float mouseY) {
        renderer.glass(x, y, width, renderedHeight, 7, 7);
        renderer.rect(x, y, width, renderedHeight, 7, 0xb31e1e2d);
        float alpha = enabled.to(module.isEnabled() ? 1 : 0);
        renderer.gradient(x, y, width, renderedHeight, 7, 7,
                MiauRenderer.opacity(MiauRenderer.primary(), alpha * .4f),
                MiauRenderer.opacity(MiauRenderer.secondary(), alpha * .4f), false);
        renderer.clip(x, y, width, renderedHeight, () -> {
            String font = module.isEnabled() ? MiauRenderer.BOLD : MiauRenderer.MEDIUM;
            int color = module.isEnabled() ? -1 : 0xffcccccc;
            String bind = gui.isBinding(this) ? "[...]" : gui.showingBinds() && module.getKeycode() != 0
                    ? "[" + MiauClickGui.keyName(module.getKeycode()) + "]" : null;
            float available = bind == null ? settings.isEmpty() ? width - 18 : width - 31 : width - 18 - renderer.width(MiauRenderer.MEDIUM, bind, 7);
            renderer.text(font, renderer.trim(font, module.getName(), 8.5f, available), x + 9, y + 15, 8.5f, color);
            if (bind != null) {
                renderer.text(MiauRenderer.MEDIUM, bind, x + width - 5 - renderer.width(MiauRenderer.MEDIUM, bind, 7), y + 15, 7, -1);
            } else if (!settings.isEmpty()) {
                renderer.chevron(x + width - 11, y + 12, 10, expansion.value(), color);
            }
            if (expansion.value() > 0) {
                settings.draw(renderer, x + 5, y + MiauLayout.ROW_HEIGHT, width - 10, mouseX, mouseY, true);
            }
        });
    }

    void click(float mouseX, float mouseY, int button) {
        if (mouseY < y + MiauLayout.ROW_HEIGHT) {
            if (button == 0) module.toggle();
            else if (button == 1 && !settings.isEmpty()) {
                expanded = !expanded;
                expansion.to(expanded ? 1 : 0);
                if (!expanded) settings.cancel();
            } else if (button == 2) {
                gui.bind(this);
            }
            return;
        }
        if (expanded) settings.click(mouseX, mouseY, button);
    }
}
