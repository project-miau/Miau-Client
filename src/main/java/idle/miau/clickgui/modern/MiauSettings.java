package idle.miau.clickgui.modern;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.SettingGroups;
import idle.miau.module.setting.impl.*;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class MiauSettings {
    private final List<MiauSetting> rows = new ArrayList<>();

    MiauSettings(Module module, MiauClickGui gui) {
        this(module, gui, module.getSettings(), null,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private MiauSettings(Module module, MiauClickGui gui, List<Setting> all, GroupSetting parent, Set<Setting> added) {
        for (Setting setting : all) {
            if (setting instanceof DescriptionSetting || added.contains(setting) || SettingGroups.parentOf(setting, all) != parent) {
                continue;
            }
            added.add(setting);
            if (setting instanceof GroupSetting group) {
                rows.add(new MiauGroup(group, module, gui, new MiauSettings(module, gui, all, group, added)));
            } else {
                rows.add(create(setting, module, gui));
            }
        }
    }

    private static MiauSetting create(Setting setting, Module owner, MiauClickGui gui) {
        if (setting instanceof ButtonSetting button) return new MiauBoolean(button, owner, gui);
        if (setting instanceof SliderSetting slider) {
            return slider.isString ? new MiauMode(slider, owner, gui) : new MiauNumber(slider, owner, gui);
        }
        if (setting instanceof RangeSetting range) return new MiauRange(range, owner, gui);
        if (setting instanceof ColorSetting color) return new MiauColor(color, owner, gui);
        if (setting instanceof MultiSelectSetting multi) return new MiauMulti(multi, owner, gui);
        if (setting instanceof TextSetting text) return new MiauText(text, owner, gui);
        if (setting instanceof KeySetting key) return new MiauKey(key, owner, gui);
        if (setting instanceof BlockListSetting || setting instanceof StringListSetting || setting instanceof PlayerListSetting) {
            return new MiauList(setting, owner, gui);
        }
        return new MiauSetting(setting, owner, gui);
    }

    float height(MiauRenderer renderer, float width) {
        float height = 0;
        for (MiauSetting row : rows) {
            if (row.setting.visible) {
                row.width = width;
                height += row.height(renderer);
            } else {
                row.cancel();
            }
        }
        return height;
    }

    void draw(MiauRenderer renderer, float x, float y, float width, float mouseX, float mouseY, boolean last) {
        List<MiauSetting> visible = rows.stream().filter(row -> row.setting.visible).toList();
        float nextY = y;
        for (int i = 0; i < visible.size(); i++) {
            MiauSetting row = visible.get(i);
            row.x = x;
            row.y = nextY;
            row.width = width;
            row.last = last && i == visible.size() - 1;
            row.renderedHeight = row.height(renderer);
            row.draw(renderer, mouseX, mouseY);
            nextY += row.renderedHeight;
        }
    }

    boolean click(float mouseX, float mouseY, int button) {
        for (MiauSetting row : rows) {
            if (row.setting.visible && row.hit(mouseX, mouseY) && row.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    void release() {
        rows.forEach(MiauSetting::release);
    }

    void cancel() {
        rows.forEach(MiauSetting::cancel);
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}

class MiauSetting {
    final Setting setting;
    final Module owner;
    final MiauClickGui gui;
    float x;
    float y;
    float width;
    float renderedHeight;
    boolean last;

    MiauSetting(Setting setting, Module owner, MiauClickGui gui) {
        this.setting = setting;
        this.owner = owner;
        this.gui = gui;
    }

    float height(MiauRenderer renderer) {
        return 17;
    }

    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        label(renderer, 10.5f);
    }

    void background(MiauRenderer renderer) {
        renderer.varying(x, y, width, renderedHeight, 0, last ? 5 : 0, 0x40000000);
    }

    void label(MiauRenderer renderer, float baseline) {
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, setting.getName(), 7, width - 10),
                x + 5, y + baseline, 7, -1);
    }

    boolean hit(float mouseX, float mouseY) {
        return inside(mouseX, mouseY, x, y, width, renderedHeight);
    }

    static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    boolean click(float mouseX, float mouseY, int button) { return false; }
    boolean key(char character, int code) { return false; }
    void release() {}
    void cancel() { gui.unfocus(this); }

    void changed() {
        owner.onCommandSettingChanged(setting);
        owner.guiUpdate();
        Miau.markCurrentConfigUnsaved();
    }

    static String number(double value, String suffix) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + (suffix == null ? "" : suffix);
    }

    static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
}

final class MiauBoolean extends MiauSetting {
    private final ButtonSetting button;
    private final MiauAnimation toggle = new MiauAnimation(150, MiauAnimation.Curve.DECELERATE);

    MiauBoolean(ButtonSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        button = setting;
        toggle.reset(button.isToggled() ? 1 : 0);
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, setting.getName(), 7,
                button.isMethodButton ? width - 10 : width - 29), x + 5, y + 10.5f, 7, -1);
        if (button.isMethodButton) return;
        float progress = toggle.to(button.isToggled() ? 1 : 0);
        int color = blend(0xff3c3c3c, MiauRenderer.primary(), progress);
        renderer.gradient(x + width - 20.5f, y + 4.55f, 17, 8.5f, 4.25f, 4.25f,
                color, MiauRenderer.darker(color, .4f), true);
        renderer.gradient(x + width - 19.65f + progress * 8.075f, y + 5.4f, 6.8f, 6.8f, 3.4f, 3.4f,
                -1, 0xffe5e5e5, true);
    }

    private static int blend(int from, int to, float progress) {
        int r = (int) ((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * progress);
        int g = (int) ((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * progress);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * progress);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    @Override
    boolean click(float mouseX, float mouseY, int mouseButton) {
        if (mouseButton != 0) return false;
        if (button.isMethodButton) {
            button.runMethod();
            return true;
        }
        button.toggle();
        owner.guiButtonToggled(button);
        changed();
        return true;
    }
}

final class MiauMode extends MiauSetting {
    private final SliderSetting mode;
    private final MiauAnimation expansion = new MiauAnimation(125, MiauAnimation.Curve.DECELERATE);
    private boolean opened;

    MiauMode(SliderSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        mode = setting;
    }

    @Override
    float height(MiauRenderer renderer) {
        return 32 + Math.max(0, mode.getOptions().length - 1) * 13 * expansion.to(opened ? 1 : 0);
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        label(renderer, 9.5f);
        renderer.rect(x + 3, y + 13.5f, width - 7, renderedHeight - 17, 4, 0x40000000);
        int selected = (int) mode.getInput();
        String[] options = mode.getOptions();
        if (selected >= 0 && selected < options.length) {
            renderer.text(MiauRenderer.BOLD, renderer.trim(MiauRenderer.BOLD, options[selected], 7, width - 27),
                    x + 7, y + 23.5f, 7, -1);
        }
        renderer.chevron(x + width - 12, y + 21.5f, 9, expansion.value(), -1);
        if (expansion.value() <= 0) return;
        renderer.clip(x, y + 28, width, renderedHeight - 28, () -> {
            float next = y + 36;
            for (int i = 0; i < options.length; i++) {
                if (i == selected) continue;
                renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, options[i], 7, width - 14),
                        x + 7, next, 7, -1);
                next += 13;
            }
        });
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (mouseY < y + 32 && button == 1) {
            opened = !opened;
            expansion.to(opened ? 1 : 0);
            return true;
        }
        if (!opened || button != 0 || mouseY < y + 26.5f) return false;
        int clicked = (int) ((mouseY - y - 26.5f) / 13);
        int selected = (int) mode.getInput();
        int option = clicked >= selected ? clicked + 1 : clicked;
        if (option >= mode.getOptions().length) return false;
        mode.setValueWithEvent(option);
        owner.onSlide(mode);
        opened = false;
        expansion.to(0);
        changed();
        return true;
    }
}

final class MiauGroup extends MiauSetting {
    private final GroupSetting group;
    private final MiauSettings children;
    private final MiauAnimation expansion = new MiauAnimation(125, MiauAnimation.Curve.DECELERATE);

    MiauGroup(GroupSetting setting, Module owner, MiauClickGui gui, MiauSettings children) {
        super(setting, owner, gui);
        group = setting;
        this.children = children;
    }

    @Override
    float height(MiauRenderer renderer) {
        float progress = expansion.to(group.isOpened() ? 1 : 0);
        return 22 + (progress > 0 ? children.height(renderer, width) * progress : 0);
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        float progress = expansion.value();
        float padding = 3 * (1 - progress);
        renderer.rect(x + padding, y + padding, width - padding * 2, 22 - padding * 2, 4 * (1 - progress), 0x33000000);
        String name = renderer.trim(MiauRenderer.BOLD, setting.getName(), 7, width - 28);
        renderer.text(MiauRenderer.BOLD, name, x + (width - renderer.width(MiauRenderer.BOLD, name, 7)) / 2,
                y + 13.5f, 7, -1);
        renderer.chevron(x + width - 13, y + 11, 12, progress, -1);
        if (progress > 0) {
            renderer.clip(x, y + 22, width, renderedHeight - 22,
                    () -> children.draw(renderer, x, y + 22, width, mouseX, mouseY, last));
        }
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (mouseY < y + 22) {
            group.setOpened(!group.isOpened());
            expansion.to(group.isOpened() ? 1 : 0);
            children.cancel();
            owner.guiUpdate();
            return true;
        }
        return group.isOpened() && children.click(mouseX, mouseY, button);
    }

    @Override
    void release() { children.release(); }

    @Override
    void cancel() {
        super.cancel();
        children.cancel();
    }
}

final class MiauNumber extends MiauSetting {
    private final SliderSetting slider;
    private final MiauAnimation fill = new MiauAnimation(50, MiauAnimation.Curve.LINEAR);
    private boolean dragging;
    private boolean initialized;
    private double pending;

    MiauNumber(SliderSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        slider = setting;
    }

    @Override
    float height(MiauRenderer renderer) { return 26; }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        if (dragging) update(mouseX);
        background(renderer);
        label(renderer, 8.5f);
        double value = deferred() && dragging ? pending : slider.getInput();
        float destination = clamp((float) ((value - slider.getMin()) / Math.max(.000001, slider.getMax() - slider.getMin()))) * (width - 12);
        if (!initialized) {
            fill.reset(destination);
            initialized = true;
        }
        float length = fill.to(destination);
        track(renderer, x + 6, y + 13, width - 12, 0, length);
        String text = value == -1 && slider.canBeDisabled ? "Disabled"
                : value == slider.getMin() && slider.getMinString() != null ? slider.getMinString() : number(value, slider.getSuffix());
        handle(renderer, x + 6 + length, y, text);
    }

    static void track(MiauRenderer renderer, float x, float y, float width, float from, float to) {
        renderer.rect(x, y, width, 2.5f, 1.25f, 0xff373737);
        if (to - from > 1) renderer.gradient(x + from, y, to - from, 2.5f, 1.25f, 1.25f,
                MiauRenderer.primary(), MiauRenderer.darker(MiauRenderer.primary(), .5f), true);
    }

    static void handle(MiauRenderer renderer, float x, float y, String text) {
        renderer.gradient(x - 1, y + 11.7f, 2, 5, 1, 1, -1, 0xffe5e5e5, true);
        renderer.text(MiauRenderer.MEDIUM, text, x - renderer.width(MiauRenderer.MEDIUM, text, 5.5f) / 2,
                y + 22, 5.5f, 0xccffffff);
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (button == 1 && slider.canBeDisabled) {
            slider.setValueRawWithEvent(slider.getInput() == -1 ? slider.getMin() : -1);
            owner.onSlide(slider);
            changed();
            return true;
        }
        if (button != 0) return false;
        dragging = true;
        pending = slider.getInput();
        update(mouseX);
        return true;
    }

    private boolean deferred() { return slider == idle.miau.module.impl.client.ClickGui.guiScale; }

    private void update(float mouseX) {
        double value = slider.getMin() + clamp((mouseX - x - 6) / (width - 12)) * (slider.getMax() - slider.getMin());
        if (deferred()) pending = Math.round(value / slider.getIntervals()) * slider.getIntervals();
        else if (value != slider.getInput()) {
            slider.setValueWithEvent(value);
            owner.onSlide(slider);
            changed();
        }
    }

    @Override
    void release() {
        if (dragging && deferred()) {
            slider.setValueWithEvent(pending);
            owner.onSlide(slider);
            changed();
        }
        dragging = false;
    }

    @Override
    void cancel() { dragging = false; super.cancel(); }
}

final class MiauRange extends MiauSetting {
    private final RangeSetting range;
    private final MiauAnimation low = new MiauAnimation(50, MiauAnimation.Curve.LINEAR);
    private final MiauAnimation high = new MiauAnimation(50, MiauAnimation.Curve.LINEAR);
    private int dragging;
    private boolean initialized;

    MiauRange(RangeSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        range = setting;
    }

    @Override
    float height(MiauRenderer renderer) { return 26; }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        if (dragging != 0) update(mouseX);
        background(renderer);
        label(renderer, 8.5f);
        float first = position(range.getMinimum());
        float second = position(range.getMaximum());
        if (!initialized) {
            low.reset(first);
            high.reset(second);
            initialized = true;
        }
        float left = low.to(first);
        float right = high.to(second);
        MiauNumber.track(renderer, x + 6, y + 13, width - 12, left, right);
        MiauNumber.handle(renderer, x + 6 + left, y, number(range.getMinimum(), range.getSuffix()));
        MiauNumber.handle(renderer, x + 6 + right, y, number(range.getMaximum(), range.getSuffix()));
    }

    private float position(double value) {
        return clamp((float) ((value - range.getMinBound()) / Math.max(.000001, range.getMaxBound() - range.getMinBound()))) * (width - 12);
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (button != 0) return false;
        float pointer = mouseX - x - 6;
        float low = position(range.getMinimum());
        float high = position(range.getMaximum());
        dragging = Math.abs(pointer - low) < Math.abs(pointer - high) || low == high && pointer < low ? 1 : 2;
        update(mouseX);
        return true;
    }

    private void update(float mouseX) {
        double value = range.getMinBound() + clamp((mouseX - x - 6) / (width - 12)) * (range.getMaxBound() - range.getMinBound());
        if (dragging == 1) range.setMinimum(Math.min(value, range.getMaximum()));
        else range.setMaximum(Math.max(value, range.getMinimum()));
        changed();
    }

    @Override
    void release() { dragging = 0; }

    @Override
    void cancel() { dragging = 0; super.cancel(); }
}

final class MiauColor extends MiauSetting {
    private final ColorSetting color;
    private final MiauAnimation expansion = new MiauAnimation(125, MiauAnimation.Curve.DECELERATE);
    private boolean opened;
    private int dragging;
    private float hue;

    MiauColor(ColorSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        color = setting;
        hue = color.getHue() / 360f;
    }

    @Override
    float height(MiauRenderer renderer) { return 17 + (color.hasAlpha() ? 63 : 50) * expansion.to(opened ? 1 : 0); }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        if (dragging != 0) update(mouseX, mouseY);
        else if (color.getSaturation() > 0) hue = color.getHue() / 360f;
        background(renderer);
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, setting.getName(), 7, width - 31),
                x + 5, y + 10.5f, 7, -1);
        renderer.rect(x + width - 22, y + 3.5f, 18, 10, 3, color.getColor());
        if (expansion.value() <= 0) return;
        renderer.clip(x, y + 17, width, renderedHeight - 17, () -> {
            renderer.gradient(x + 5, y + 17, 65, 50, 0, 0, -1, Color.HSBtoRGB(hue, 1, 1), false);
            renderer.gradient(x + 5, y + 17, 65, 50, 0, 0, 0, 0xff000000, true);
            renderer.outline(x + 5 + color.getSaturation() * 65 - 1.5f,
                    y + 17 + (1 - color.getBrightness()) * 50 - 1.5f, 3, 3, 1.5f, .75f, -1);
            for (int i = 0; i < 6; i++) {
                renderer.gradient(x + 75, y + 17 + i * 50f / 6, 8, 50f / 6 + .1f, 0, 0,
                        Color.HSBtoRGB(i / 6f, 1, 1), Color.HSBtoRGB((i + 1) / 6f, 1, 1), true);
            }
            renderer.outline(x + 74.5f, y + 17 + hue * 50 - 1, 9, 2, .5f, .75f, -1);
            if (color.hasAlpha()) {
                renderer.gradient(x + 5, y + 72, 78, 4, 2, 2, 0x00606060, color.getColor() | 0xff000000, false);
                renderer.rect(x + 5 + color.getAlpha() / 255f * 78 - 1, y + 71, 2, 6, 1, -1);
            }
        });
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (mouseY < y + 17) {
            if (button != 1) return false;
            opened = !opened;
            expansion.to(opened ? 1 : 0);
            dragging = 0;
            return true;
        }
        if (!opened || button != 0) return false;
        if (inside(mouseX, mouseY, x + 5, y + 17, 65, 50)) dragging = 1;
        else if (inside(mouseX, mouseY, x + 75, y + 17, 8, 50)) dragging = 2;
        else if (color.hasAlpha() && inside(mouseX, mouseY, x + 5, y + 69, 78, 10)) dragging = 3;
        if (dragging == 0) return false;
        update(mouseX, mouseY);
        return true;
    }

    private void update(float mouseX, float mouseY) {
        if (dragging == 1) color.setFromHSB(hue * 360, clamp((mouseX - x - 5) / 65), 1 - clamp((mouseY - y - 17) / 50));
        else if (dragging == 2) {
            hue = clamp((mouseY - y - 17) / 50);
            color.setFromHSB(hue * 360, color.getSaturation(), color.getBrightness());
        } else color.setAlpha(Math.round(clamp((mouseX - x - 5) / 78) * 255));
        changed();
    }

    @Override
    void release() { dragging = 0; }

    @Override
    void cancel() { dragging = 0; super.cancel(); }
}

final class MiauMulti extends MiauSetting {
    private final MultiSelectSetting multi;
    private final List<Chip> chips = new ArrayList<>();

    MiauMulti(MultiSelectSetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        multi = setting;
    }

    @Override
    float height(MiauRenderer renderer) {
        chips.clear();
        float nextX = 2;
        float nextY = 0;
        for (String option : multi.getOptions()) {
            float size = Math.min(width - 12, renderer.width(MiauRenderer.MEDIUM, option, 6) + 8.75f);
            if (nextX > 2 && nextX + size > width - 10) {
                nextX = 2;
                nextY += 10;
            }
            chips.add(new Chip(option, nextX, nextY, size - 4));
            nextX += size - 2.5f;
        }
        return 28.5f + nextY;
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        label(renderer, 8.5f);
        renderer.outline(x + 5, y + 13, width - 10, renderedHeight - 17, 2.5f, 1.5f, 0xff505050);
        renderer.rect(x + 5, y + 13, width - 10, renderedHeight - 17, 2.5f, 0xff191919);
        for (Chip chip : chips) {
            renderer.rect(x + 4.5f + chip.x, y + 14.5f + chip.y, chip.width, 8.5f, 2.5f,
                    multi.isSelected(chip.name) ? MiauRenderer.opacity(MiauRenderer.primary(), .4f) : 0xff333333);
            renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, chip.name, 6, chip.width - 4),
                    x + 7 + chip.x, y + 21 + chip.y, 6, multi.isSelected(chip.name) ? 0xe6ffffff : 0xffcccccc);
        }
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (button != 0) return false;
        for (Chip chip : chips) {
            if (inside(mouseX, mouseY, x + 4.5f + chip.x, y + 14.5f + chip.y, chip.width, 8.5f)) {
                multi.toggle(chip.name);
                changed();
                return true;
            }
        }
        return false;
    }

    private record Chip(String name, float x, float y, float width) {}
}

final class MiauKey extends MiauSetting {
    private final KeySetting key;

    MiauKey(KeySetting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui);
        key = setting;
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        String value = gui.focused(this) ? "[...]" : "[" + MiauClickGui.keyName(key.getKey()) + "]";
        float valueWidth = renderer.width(MiauRenderer.MEDIUM, value, 6);
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, setting.getName(), 7, width - valueWidth - 14),
                x + 5, y + 10.5f, 7, -1);
        renderer.text(MiauRenderer.MEDIUM, value, x + width - valueWidth - 5, y + 10.5f, 6, 0xffcccccc);
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        gui.focus(this);
        return true;
    }

    @Override
    boolean key(char character, int code) {
        key.setKey(code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_DELETE ? 0 : code);
        gui.unfocus(this);
        changed();
        return true;
    }
}
