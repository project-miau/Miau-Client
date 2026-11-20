package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.RoundedUtils;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

final class MiauButtonComponent extends MiauSettingComponent {
    private static final float SWITCH_WIDTH = 16.0F;
    private static final float SWITCH_HEIGHT = 8.0F;
    private static final float SWITCH_KNOB = 6.0F;

    private final ButtonSetting button;

    MiauButtonComponent(ButtonSetting setting, Module owner) {
        super(setting, owner);
        button = setting;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauStyle.hoverRow(x - 3, y, width, mouseX, mouseY);
        MiauFontRenderer font = MiauStyle.settingFont();
        font.drawString(MiauStyle.trim(button.getName(), font, (int) width - 28),
                x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
        if (button.isMethodButton) {
            font.drawString(">", x + width - 10, y + 3, MiauStyle.WHITE, false);
            return;
        }

        float trackX = x + width - SWITCH_WIDTH - 4.0F;
        float trackY = y + (MiauStyle.ROW_HEIGHT - SWITCH_HEIGHT) / 2.0F;
        if (button.isToggled()) {
            int accent = MiauStyle.accent(trackX + SWITCH_WIDTH * 0.5D, trackY + SWITCH_HEIGHT * 0.5D);
            RoundedUtils.drawRound(trackX, trackY, SWITCH_WIDTH, SWITCH_HEIGHT,
                    SWITCH_HEIGHT / 2.0F, MiauStyle.withAlpha(accent, 200));
            RoundedUtils.drawRound(
                    trackX + SWITCH_WIDTH - SWITCH_KNOB - 1.0F,
                    trackY + 1.0F,
                    SWITCH_KNOB,
                    SWITCH_KNOB,
                    SWITCH_KNOB / 2.0F,
                    MiauStyle.WHITE);
        }
        else {
            RoundedUtils.drawRound(trackX, trackY, SWITCH_WIDTH, SWITCH_HEIGHT,
                    SWITCH_HEIGHT / 2.0F, 0xFF333333);
            RoundedUtils.drawRound(trackX + 1.0F, trackY + 1.0F,
                    SWITCH_KNOB, SWITCH_KNOB, SWITCH_KNOB / 2.0F, MiauStyle.MUTED);
        }
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!hovered(mouseX, mouseY) || mouseButton != 0) {
            return false;
        }
        if (button.isMethodButton) {
            button.runMethod();
        }
        else {
            button.toggle();
        }
        owner.guiButtonToggled(button);
        markChanged();
        return true;
    }
}

final class MiauDropdownComponent extends MiauSettingComponent {
    private final SliderSetting slider;
    private final MultiSelectSetting multiSelect;
    private boolean expanded;

    MiauDropdownComponent(SliderSetting setting, Module owner) {
        super(setting, owner);
        slider = setting;
        multiSelect = null;
    }

    MiauDropdownComponent(MultiSelectSetting setting, Module owner) {
        super(setting, owner);
        slider = null;
        multiSelect = setting;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauFontRenderer font = MiauStyle.settingFont();
        int accent = MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D);
        if (MiauStyle.hovered(x - 3, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY)) {
            RoundedUtils.drawRound(x - 3, y, width, MiauStyle.ROW_HEIGHT, 2.0F, MiauStyle.HOVER);
        }

        String name = setting.getName();
        String value = summary();
        int valueWidth = font.getStringWidth(value);
        font.drawString(
                MiauStyle.trim(name, font, (int) width - valueWidth - 18),
                x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
        font.drawString(
                MiauStyle.trim(value, font, Math.max(20, (int) width / 2)),
                x + width - 16 - valueWidth,
                MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), accent, false);
        font.drawString(expanded ? "v" : ">", x + width - 10, y + 3, MiauStyle.MUTED, false);
        if (!expanded) {
            return;
        }
        String[] options = options();
        RoundedUtils.drawRound(x - 3, y + MiauStyle.ROW_HEIGHT,
                width, MiauStyle.ROW_HEIGHT * options.length, 3.0F,
                MiauStyle.withAlpha(MiauStyle.DARK, 250));
        for (int index = 0; index < options.length; index++) {
            float optionY = y + MiauStyle.ROW_HEIGHT * (index + 1);
            boolean selected = selected(index, options[index]);
            boolean hovered = MiauStyle.hovered(x - 3, optionY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY);
            if (hovered) {
                RoundedUtils.drawRound(x - 2, optionY + 1, width - 2, MiauStyle.ROW_HEIGHT - 2, 2.0F, MiauStyle.HOVER);
            }
            String option = MiauStyle.trim(options[index].replace('_', ' '), font, (int) width - 26);
            font.drawString(option, x + 6,
                    MiauStyle.centeredTextY((int) optionY, MiauStyle.ROW_HEIGHT, font),
                    selected ? accent : hovered ? MiauStyle.WHITE : MiauStyle.MUTED, false);
            if (selected) {
                RoundedUtils.drawRound(x + width - 13,
                        optionY + (MiauStyle.ROW_HEIGHT - 6.0F) / 2.0F,
                        6.0F, 6.0F, 3.0F, accent);
            }
        }
    }

    @Override
    int getHeight() {
        return MiauStyle.ROW_HEIGHT * (expanded ? options().length + 1 : 1);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (MiauStyle.hovered(x - 3, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        if (!expanded) {
            return false;
        }
        String[] options = options();
        for (int index = 0; index < options.length; index++) {
            float optionY = y + MiauStyle.ROW_HEIGHT * (index + 1);
            if (!MiauStyle.hovered(x - 3, optionY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY)) {
                continue;
            }
            if (slider != null) {
                slider.setValueWithEvent(index);
                owner.onSlide(slider);
                expanded = false;
            }
            else {
                multiSelect.toggle(options[index]);
                owner.onCommandSettingChanged(multiSelect);
                owner.guiUpdate();
            }
            markChanged();
            return true;
        }
        return false;
    }

    @Override
    void onGuiClosed() {
        expanded = false;
    }

    private String[] options() {
        return slider == null ? multiSelect.getOptions() : slider.getOptions();
    }

    private boolean selected(int index, String option) {
        return slider == null ? multiSelect.isSelected(option) : (int) slider.getInput() == index;
    }

    private String summary() {
        if (slider != null) {
            String[] options = slider.getOptions();
            if (options.length == 0) {
                return "None";
            }
            int index = Math.max(0, Math.min(options.length - 1, (int) slider.getInput()));
            return options[index].replace('_', ' ');
        }
        int count = multiSelect.getSelected().size();
        if (count == 0) {
            return "None";
        }
        if (count == 1) {
            return multiSelect.getSelected().get(0).replace('_', ' ');
        }
        return count + " selected";
    }
}

final class MiauSliderComponent extends MiauSettingComponent {
    private final SliderSetting slider;
    private final boolean deferScaleChange;
    private boolean dragging;
    private double pendingValue;

    MiauSliderComponent(SliderSetting setting, Module owner) {
        super(setting, owner);
        slider = setting;
        deferScaleChange = owner instanceof idle.miau.module.impl.client.ClickGui
                && setting == idle.miau.module.impl.client.ClickGui.guiScale;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauFontRenderer font = MiauStyle.settingFont();
        boolean over = hovered(mouseX, mouseY);
        if (over) {
            Gui.drawRect((int) (x - 3), (int) y, (int) (x - 3 + width), (int) (y + MiauStyle.ROW_HEIGHT), MiauStyle.HOVER);
        }
        if (dragging) {
            updateValue(mouseX);
        }
        double value = deferScaleChange && dragging ? pendingValue : slider.getInput();
        double ratio = value == -1 && slider.canBeDisabled ? 0.0D : (value - slider.getMin()) / Math.max(0.000001D, slider.getMax() - slider.getMin());
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        Gui.drawRect((int) (x - 3), (int) y, (int) (x - 3 + width * ratio), (int) (y + MiauStyle.ROW_HEIGHT), MiauStyle.withAlpha(MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D), 110));
        String rendered;
        if (value == -1 && slider.canBeDisabled) {
            rendered = "Disabled";
        }
        else if (slider.getMinString() != null && value == slider.getMin()) {
            rendered = slider.getMinString();
        }
        else {
            rendered = Utils.asWholeNum(value) + slider.getSuffix();
        }
        String label = MiauStyle.trim(slider.getName() + ": " + rendered, font, (int) width - 6);
        font.drawString(label, x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!hovered(mouseX, mouseY) || button != 0) {
            return false;
        }
        dragging = true;
        updateValue(mouseX);
        return true;
    }

    @Override
    void mouseReleased(int mouseX, int mouseY, int button) {
        if (!dragging || button != 0) {
            return;
        }
        updateValue(mouseX);
        dragging = false;
        slider.setValueWithEvent(deferScaleChange ? pendingValue : slider.getInput());
        owner.onSlide(slider);
        markChanged();
    }

    @Override
    void onGuiClosed() {
        dragging = false;
    }

    private void updateValue(int mouseX) {
        double offset = Math.max(0.0D, Math.min(width, mouseX - (x - 3)));
        if (offset == 0.0D && slider.canBeDisabled) {
            if (deferScaleChange) {
                pendingValue = -1.0D;
            }
            else {
                slider.setValueRaw(-1.0D);
            }
        }
        else {
            double value = slider.getMin() + offset / width * (slider.getMax() - slider.getMin());
            if (deferScaleChange) {
                double interval = slider.getIntervals();
                pendingValue = Math.round(value / interval) * interval;
            }
            else {
                slider.setValue(value);
            }
        }
        if (!deferScaleChange) {
            markChanged();
        }
    }
}

final class MiauRangeComponent extends MiauSettingComponent {
    private final RangeSetting range;
    private Handle handle;

    MiauRangeComponent(RangeSetting setting, Module owner) {
        super(setting, owner);
        range = setting;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        if (hovered(mouseX, mouseY)) {
            Gui.drawRect((int) (x - 3), (int) y, (int) (x - 3 + width),
                    (int) (y + MiauStyle.ROW_HEIGHT), MiauStyle.HOVER);
        }
        if (handle != null) {
            updateValue(mouseX);
        }
        double span = Math.max(0.000001D, range.getMaxBound() - range.getMinBound());
        double minRatio = (range.getMinimum() - range.getMinBound()) / span;
        double maxRatio = (range.getMaximum() - range.getMinBound()) / span;
        float left = (float) (x - 3 + width * minRatio);
        float right = (float) (x - 3 + width * maxRatio);
        Gui.drawRect((int) left, (int) y, (int) right, (int) (y + MiauStyle.ROW_HEIGHT),
                MiauStyle.withAlpha(MiauStyle.accent(x + width * 0.5D,
                        y + MiauStyle.ROW_HEIGHT * 0.5D), 110));
        String suffix = range.getSuffix() != null ? range.getSuffix() : "";
        String label = range.getName() + ": " + Utils.asWholeNum(range.getMinimum())
                + " - " + Utils.asWholeNum(range.getMaximum()) + suffix;
        MiauFontRenderer font = MiauStyle.settingFont();
        font.drawString(
                MiauStyle.trim(label, font, (int) width - 6),
                x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!hovered(mouseX, mouseY) || button != 0) {
            return false;
        }
        double value = valueAt(mouseX);
        if (range.getMinimum() == range.getMaximum()) {
            handle = value < range.getMinimum() ? Handle.MINIMUM : Handle.MAXIMUM;
        }
        else {
            handle = Math.abs(value - range.getMinimum()) <= Math.abs(value - range.getMaximum())
                    ? Handle.MINIMUM : Handle.MAXIMUM;
        }
        updateValue(mouseX);
        return true;
    }

    @Override
    void mouseReleased(int mouseX, int mouseY, int button) {
        if (handle == null || button != 0) {
            return;
        }
        handle = null;
        owner.onCommandSettingChanged(range);
        markChanged();
    }

    @Override
    void onGuiClosed() {
        handle = null;
    }

    private void updateValue(int mouseX) {
        double value = valueAt(mouseX);
        if (handle == Handle.MINIMUM) {
            range.setMinimum(value);
        }
        else {
            range.setMaximum(value);
        }
        markChanged();
    }

    private double valueAt(int mouseX) {
        double offset = Math.max(0.0D, Math.min(width, mouseX - (x - 3)));
        return range.getMinBound() + offset / width * (range.getMaxBound() - range.getMinBound());
    }

    private enum Handle {
        MINIMUM,
        MAXIMUM
    }
}

final class MiauTextComponent extends MiauSettingComponent {
    private final TextSetting textSetting;
    private final MiauTextField field;

    MiauTextComponent(TextSetting setting, Module owner) {
        super(setting, owner);
        textSetting = setting;
        field = new MiauTextField(setting.getPlaceholder().isEmpty() ? setting.getName() : setting.getPlaceholder(), setting.getMaxLength());
        field.setText(setting.getText());
    }

    @Override
    void draw(int mouseX, int mouseY) {
        if (!field.isFocused() && !field.getText().equals(textSetting.getText())) {
            field.setText(textSetting.getText());
        }
        field.setMasked(textSetting.isMasked());
        field.draw(x, y + 1, width - 6);
        if (field.isFocused()) {
            String tooltip = MiauStyle.trim(textSetting.getName(), MiauStyle.moduleFont(), 110);
            int tooltipWidth = MiauStyle.moduleFont().getStringWidth(tooltip) + 4;
            Gui.drawRect(mouseX + 5, mouseY - 3, mouseX + 5 + tooltipWidth, mouseY + 9, MiauStyle.DARK);
            MiauStyle.moduleFont().drawString(tooltip, mouseX + 7, mouseY - 2, MiauStyle.WHITE, false);
        }
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && hovered(mouseX, mouseY)) {
            field.focus();
            if (textSetting.isSensitive()) {
                textSetting.revealTemporarily(3000L);
            }
            return true;
        }
        field.unfocus();
        return false;
    }

    @Override
    boolean keyTyped(char typedChar, int keyCode) {
        if (!field.isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            textSetting.setText(field.getText());
            textSetting.submit();
            field.unfocus();
            markChanged();
            return true;
        }
        boolean handled = field.keyTyped(typedChar, keyCode);
        textSetting.setText(field.getText());
        if (handled) {
            markChanged();
        }
        return true;
    }

    @Override
    void clearTextFocus() {
        if (field.isFocused()) {
            textSetting.setText(field.getText());
        }
        field.unfocus();
    }
}

final class MiauKeyComponent extends MiauSettingComponent {
    private final KeySetting keySetting;
    private boolean binding;

    MiauKeyComponent(KeySetting setting, Module owner) {
        super(setting, owner);
        keySetting = setting;
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauStyle.hoverRow(x - 3, y, width, mouseX, mouseY);
        MiauFontRenderer font = MiauStyle.settingFont();
        String value = binding ? "Press a key..." : MiauModuleRow.keyName(keySetting.getKey());
        int valueWidth = font.getStringWidth(value);
        int chipWidth = valueWidth + 8;
        int accent = MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D);
        RoundedUtils.drawRound(x + width - chipWidth - 3.0F, y + 3.0F,
                chipWidth, MiauStyle.ROW_HEIGHT - 6.0F, 3.0F,
                MiauStyle.withAlpha(MiauStyle.DARK, 255));
        font.drawString(MiauStyle.trim(keySetting.getName(), font,
                        (int) width - chipWidth - 12),
                x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
        font.drawString(value, x + width - 3.0F - valueWidth,
                MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font),
                binding ? MiauStyle.WHITE : accent, false);
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!hovered(mouseX, mouseY)) {
            return false;
        }
        if (binding && button > 1) {
            keySetting.setKey(button + 1000);
            binding = false;
            markChanged();
        }
        else if (button == 0) {
            binding = !binding;
        }
        return true;
    }

    @Override
    boolean keyTyped(char typedChar, int keyCode) {
        if (!binding) {
            return false;
        }
        keySetting.setKey(keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_0 ? 0 : keyCode);
        binding = false;
        markChanged();
        return true;
    }

    @Override
    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (!binding) {
            return false;
        }
        keySetting.setKey(wheel > 0 ? 1069 : 1070);
        binding = false;
        markChanged();
        return true;
    }

    @Override
    void onGuiClosed() {
        binding = false;
    }
}

final class MiauGroupComponent extends MiauSettingComponent {
    private final GroupSetting group;
    private final List<MiauSettingComponent> children = new ArrayList<MiauSettingComponent>();

    MiauGroupComponent(GroupSetting setting, Module owner, List<Setting> ownerSettings) {
        super(setting, owner);
        group = setting;
        for (Setting child : ownerSettings) {
            if (MiauSettingComponent.groupOf(child) == group) {
                children.add(MiauSettingComponent.create(child, owner, ownerSettings));
            }
        }
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauStyle.hoverRow(x - 3, y, width, mouseX, mouseY);
        MiauFontRenderer font = MiauStyle.settingFont();
        font.drawString(MiauStyle.trim(group.getName(), font, (int) width - 18),
                x + 2, MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.WHITE, false);
        int color = MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D);
        if (!MiauStyle.drawTexture(group.isOpened() ? "expand" : "collapse", x + width - 12, y + 5, 8, 5, color)) {
            font.drawString(group.isOpened() ? "-" : "+", x + width - 10, y + 3, color, false);
        }
        if (!group.isOpened()) {
            return;
        }
        int childY = (int) y + MiauStyle.ROW_HEIGHT;
        for (MiauSettingComponent child : children) {
            if (!child.isVisible()) {
                continue;
            }
            child.setBounds(x + 3, childY, width - 3);
            child.draw(mouseX, mouseY);
            childY += child.getHeight();
        }
    }

    @Override
    int getHeight() {
        int height = MiauStyle.ROW_HEIGHT;
        if (group.isOpened()) {
            for (MiauSettingComponent child : children) {
                if (child.isVisible()) {
                    height += child.getHeight();
                }
            }
        }
        return height;
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (MiauStyle.hovered(x - 3, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY) && (button == 0 || button == 1)) {
            group.setOpened(!group.isOpened());
            return true;
        }
        if (!group.isOpened()) {
            return false;
        }
        for (MiauSettingComponent child : children) {
            if (child.isVisible() && child.mouseClicked(mouseX, mouseY, button)) {
                for (MiauSettingComponent other : children) {
                    if (other != child) {
                        other.clearTextFocus();
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    void mouseReleased(int mouseX, int mouseY, int button) {
        for (MiauSettingComponent child : children) {
            child.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    boolean keyTyped(char typedChar, int keyCode) {
        for (MiauSettingComponent child : children) {
            if (child.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        for (MiauSettingComponent child : children) {
            if (child.isVisible() && child.mouseWheel(mouseX, mouseY, wheel)) {
                return true;
            }
        }
        return false;
    }

    @Override
    void clearTextFocus() {
        for (MiauSettingComponent child : children) {
            child.clearTextFocus();
        }
    }

    @Override
    void onGuiClosed() {
        for (MiauSettingComponent child : children) {
            child.onGuiClosed();
        }
    }
}

final class MiauDescriptionComponent extends MiauSettingComponent {
    MiauDescriptionComponent(Setting setting, Module owner) {
        super(setting, owner);
    }

    @Override
    void draw(int mouseX, int mouseY) {
        String text = setting instanceof DescriptionSetting ? ((DescriptionSetting) setting).getDesc() : setting.getName();
        MiauFontRenderer font = MiauStyle.settingFont();
        font.drawString(MiauStyle.trim(text, font, (int) width - 7), x + 2,
                MiauStyle.centeredTextY((int) y, MiauStyle.ROW_HEIGHT, font), MiauStyle.MUTED, false);
    }
}
