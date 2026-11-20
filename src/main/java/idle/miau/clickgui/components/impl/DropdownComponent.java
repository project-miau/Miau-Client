package idle.miau.clickgui.components.impl;

import idle.miau.Miau;
import idle.miau.clickgui.components.Component;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.font.MiauFontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public final class DropdownComponent extends Component {
    private static final float HEADER_HEIGHT = 16.0F;
    private static final float OPTION_HEIGHT = 12.0F;

    private final ModuleComponent moduleComponent;
    private final SliderSetting sliderSetting;
    private final MultiSelectSetting multiSelectSetting;
    public final Setting setting;
    public float o;
    public float xOffset;
    public boolean expanded;
    private float x;
    private float y;

    public DropdownComponent(SliderSetting setting, ModuleComponent moduleComponent, float o) {
        this.setting = setting;
        this.sliderSetting = setting;
        this.multiSelectSetting = null;
        this.moduleComponent = moduleComponent;
        this.o = o;
    }

    public DropdownComponent(MultiSelectSetting setting, ModuleComponent moduleComponent, float o) {
        this.setting = setting;
        this.sliderSetting = null;
        this.multiSelectSetting = setting;
        this.moduleComponent = moduleComponent;
        this.o = o;
    }

    @Override
    public void render() {
        float left = moduleComponent.categoryComponent.getX() + 4.0F + xOffset / 2.0F;
        float top = moduleComponent.categoryComponent.getY() + o;
        float right = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - 4.0F;
        RenderUtils.drawRect(left, top + 1.0F, right, top + HEADER_HEIGHT - 1.0F, 0x40202028);
        drawText("[" + (expanded ? "v" : ">") + "] " + setting.getName() + ": " + getSummary(), left + 1.0F, top + 4.0F, -1);
        if (!expanded) {
            return;
        }
        String[] options = getOptions();
        for (int index = 0; index < options.length; index++) {
            float optionTop = top + HEADER_HEIGHT + index * OPTION_HEIGHT;
            boolean selected = isSelected(index, options[index]);
            if (selected) {
                int color = Color.getHSBColor((float) (System.currentTimeMillis() % 11000L) / 11000.0F, 0.65F, 0.9F).getRGB();
                RenderUtils.drawRect(left + 2.0F, optionTop, right, optionTop + OPTION_HEIGHT, Utils.mergeAlpha(color, 85));
            }
            String marker = multiSelectSetting == null ? selected ? "(*) " : "( ) " : selected ? "[x] " : "[ ] ";
            drawText(marker + options[index].replace('_', ' '), left + 4.0F, optionTop + 2.0F, selected ? -1 : 0xFFC7C7C7);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        x = moduleComponent.categoryComponent.getX();
        y = moduleComponent.categoryComponent.getModuleY() + o;
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this) || button != 0) {
            return false;
        }
        float width = moduleComponent.categoryComponent.getWidth();
        if (mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + HEADER_HEIGHT) {
            expanded = !expanded;
            moduleComponent.updateSettingPositions();
            return true;
        }
        if (!expanded || mouseX <= x || mouseX >= x + width) {
            return false;
        }
        String[] options = getOptions();
        for (int index = 0; index < options.length; index++) {
            float optionTop = y + HEADER_HEIGHT + index * OPTION_HEIGHT;
            if (mouseY <= optionTop || mouseY >= optionTop + OPTION_HEIGHT) {
                continue;
            }
            if (sliderSetting != null) {
                sliderSetting.setValueWithEvent(index);
                moduleComponent.mod.onSlide(sliderSetting);
                expanded = false;
                moduleComponent.updateSettingPositions();
            }
            else {
                multiSelectSetting.toggle(options[index]);
                moduleComponent.mod.onCommandSettingChanged(multiSelectSetting);
                moduleComponent.mod.guiUpdate();
            }
            markChanged();
            return true;
        }
        return false;
    }

    @Override
    public void updateHeight(float value) {
        o = value;
    }

    @Override
    public float getHeightF() {
        return HEADER_HEIGHT + (expanded ? getOptions().length * OPTION_HEIGHT : 0.0F);
    }

    @Override
    public float getOffset() {
        return o;
    }

    @Override
    public boolean isBaseVisible() {
        return setting.visible;
    }

    @Override
    public void onGuiClosed() {
        expanded = false;
    }

    public void restoreExpandedState(boolean value) {
        expanded = value;
    }

    private String[] getOptions() {
        return sliderSetting != null ? sliderSetting.getOptions() : multiSelectSetting.getOptions();
    }

    private boolean isSelected(int index, String option) {
        return sliderSetting != null ? (int) sliderSetting.getInput() == index : multiSelectSetting.isSelected(option);
    }

    private String getSummary() {
        if (sliderSetting != null) {
            String[] options = sliderSetting.getOptions();
            if (options.length == 0) {
                return "None";
            }
            int index = Math.max(0, Math.min(options.length - 1, (int) sliderSetting.getInput()));
            return options[index].replace('_', ' ');
        }
        int selected = multiSelectSetting.getSelected().size();
        if (selected == 0) {
            return "None";
        }
        if (selected == 1) {
            return multiSelectSetting.getSelected().get(0).replace('_', ' ');
        }
        return selected + " selected";
    }

    private void drawText(String text, float x, float y, int color) {
        MiauFontRenderer renderer = idle.miau.module.impl.client.ClickGui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        renderer.drawString(text, x * 2.0F, y * 2.0F, color, true);
        GL11.glPopMatrix();
    }

    private void markChanged() {
        if (Miau.currentConfig != null) {
            Miau.currentConfig.getModule().saved = false;
        }
    }
}
