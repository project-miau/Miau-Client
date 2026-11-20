package idle.miau.clickgui.modern;

import idle.miau.module.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MiauModuleList {
    private final MiauClickGui gui;
    private final List<MiauModuleRow> rows = new ArrayList<>();
    private final MiauAnimation scroll = new MiauAnimation(250, MiauAnimation.Curve.EXPO);
    private float x;
    private float y;
    private float width;
    private float height;
    private float contentHeight;
    private float target;

    MiauModuleList(MiauClickGui gui) {
        this.gui = gui;
    }

    void setModules(List<Module> modules) {
        List<Module> sorted = new ArrayList<>(modules);
        sorted.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, MiauModuleRow> previous = new HashMap<>();
        rows.forEach(row -> previous.put(row.module.getConfigId() + "\u0000" + row.module.getName(), row));
        rows.clear();
        for (Module module : sorted) {
            MiauModuleRow row = gui.row(module);
            MiauModuleRow old = previous.get(module.getConfigId() + "\u0000" + module.getName());
            if (old != null && old != row) {
                row.expanded = old.expanded;
                row.expansion.reset(old.expansion.value());
                row.expansion.to(row.expanded ? 1 : 0);
            }
            rows.add(row);
        }
    }

    void draw(MiauRenderer renderer, float x, float y, float width, float height, float mouseX, float mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = Math.max(0, height);
        contentHeight = 0;
        for (MiauModuleRow row : rows) {
            row.width = width - 6;
            contentHeight += row.height() + MiauLayout.ROW_GAP;
        }
        if (!rows.isEmpty()) contentHeight -= MiauLayout.ROW_GAP;
        clamp();
        float offset = Math.max(-maximum(), Math.min(0, scroll.value()));
        renderer.clip(x, y, width, this.height, () -> {
            float rowY = y + offset;
            for (int index = 0; index < rows.size(); index++) {
                MiauModuleRow row = rows.get(index);
                row.x = x;
                row.y = rowY;
                row.draw(mouseX, mouseY);
                rowY += row.renderedHeight + MiauLayout.ROW_GAP;
            }
        });
        if (rows.isEmpty()) {
            renderer.text(MiauRenderer.MEDIUM, "No entries found", x + 10, y + 18, 9, 0xffaaaaaa);
        }
        MiauPanel.scrollbar(renderer, x + width - 2, y, this.height, contentHeight, -offset);
    }

    void click(float mouseX, float mouseY, int button) {
        if (!MiauSetting.inside(mouseX, mouseY, x, y, width - 6, height)) return;
        for (MiauModuleRow row : new ArrayList<>(rows)) {
            if (MiauSetting.inside(mouseX, mouseY, row.x, row.y, row.width, row.renderedHeight)) {
                row.click(mouseX, mouseY, button);
                return;
            }
        }
    }

    void scroll(float mouseX, float mouseY, float steps) {
        if (!MiauSetting.inside(mouseX, mouseY, x, y, width, height)) return;
        target += steps * 50;
        clamp();
        scroll.to(target);
    }

    void cancel() {
        rows.forEach(row -> row.settings.cancel());
    }

    private float maximum() { return Math.max(0, contentHeight - height); }

    private void clamp() {
        float bounded = Math.max(-maximum(), Math.min(0, target));
        if (bounded != target) {
            target = bounded;
            scroll.to(target);
        }
    }
}
