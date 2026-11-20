package idle.miau.clickgui.components.impl;

import idle.miau.Miau;
import idle.miau.clickgui.components.Component;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public final class RangeComponent extends Component {
    public final RangeSetting rangeSetting;
    private final ModuleComponent moduleComponent;
    private float offset;
    private float x;
    private float y;
    public float xOffset;
    private Handle heldHandle;

    public RangeComponent(
            RangeSetting rangeSetting, ModuleComponent moduleComponent, float offset) {
        this.rangeSetting = rangeSetting;
        this.moduleComponent = moduleComponent;
        this.offset = offset;
    }

    @Override
    public void render() {
        float left = trackLeft();
        float right = trackRight();
        float top = moduleComponent.categoryComponent.getY() + offset + 11.0F;
        float bottom = top + 4.0F;
        RenderUtils.drawRoundedRectangle(left, top, right, bottom, 4.0F, -12302777);

        float minimumX = positionOf(rangeSetting.getMinimum());
        float maximumX = positionOf(rangeSetting.getMaximum());
        int color = Color.getHSBColor(
                (float) (System.currentTimeMillis() % 11000L) / 11000.0F,
                0.75F, 0.9F).getRGB();
        RenderUtils.drawRoundedRectangle(
                minimumX, top, Math.max(minimumX + 1.0F, maximumX), bottom,
                4.0F, color);
        RenderUtils.drawRoundedRectangle(
                minimumX - 1.0F, top - 1.0F, minimumX + 1.0F, bottom + 1.0F,
                2.0F, color);
        RenderUtils.drawRoundedRectangle(
                maximumX - 1.0F, top - 1.0F, maximumX + 1.0F, bottom + 1.0F,
                2.0F, color);

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String suffix = rangeSetting.getSuffix() != null ? rangeSetting.getSuffix() : "";
        String value = Utils.asWholeNum(rangeSetting.getMinimum())
                + " - " + Utils.asWholeNum(rangeSetting.getMaximum()) + suffix;
        idle.miau.module.impl.client.ClickGui.getClickGuiSettingFontRenderer().drawString(
                rangeSetting.getName() + ": \u00a7b" + value,
                (moduleComponent.categoryComponent.getX() + 4.0F) * 2.0F + xOffset,
                (moduleComponent.categoryComponent.getY() + offset + 3.0F) * 2.0F,
                -1, true);
        GL11.glPopMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        x = moduleComponent.categoryComponent.getX();
        y = moduleComponent.categoryComponent.getModuleY() + offset;
        if (heldHandle != null) {
            updateValue(mouseX);
            if (Miau.currentConfig != null) {
                Miau.currentConfig.getModule().saved = false;
            }
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (button != 0
                || !moduleComponent.isOpened
                || !moduleComponent.isVisible(this)
                || mouseX <= x
                || mouseX >= x + moduleComponent.categoryComponent.getWidth()
                || mouseY <= y
                || mouseY >= y + 16.0F) {
            return false;
        }
        double value = valueAt(mouseX);
        if (rangeSetting.getMinimum() == rangeSetting.getMaximum()) {
            heldHandle = value < rangeSetting.getMinimum()
                    ? Handle.MINIMUM : Handle.MAXIMUM;
        }
        else {
            heldHandle = Math.abs(value - rangeSetting.getMinimum())
                    <= Math.abs(value - rangeSetting.getMaximum())
                    ? Handle.MINIMUM : Handle.MAXIMUM;
        }
        updateValue(mouseX);
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0 || heldHandle == null) {
            return;
        }
        heldHandle = null;
        moduleComponent.mod.onCommandSettingChanged(rangeSetting);
    }

    @Override
    public void onGuiClosed() {
        heldHandle = null;
    }

    @Override
    public void updateHeight(float offset) {
        this.offset = offset;
    }

    @Override
    public float getOffset() {
        return offset;
    }

    @Override
    public float getHeightF() {
        return 16.0F;
    }

    @Override
    public boolean isBaseVisible() {
        return rangeSetting.visible;
    }

    private void updateValue(int mouseX) {
        double value = valueAt(mouseX);
        if (heldHandle == Handle.MINIMUM) {
            rangeSetting.setMinimum(value);
        }
        else {
            rangeSetting.setMaximum(value);
        }
    }

    private double valueAt(int mouseX) {
        double left = trackLeft();
        double width = Math.max(1.0D, trackRight() - left);
        double position = Math.max(0.0D, Math.min(width, mouseX - left));
        return rangeSetting.getMinBound()
                + position / width * (rangeSetting.getMaxBound() - rangeSetting.getMinBound());
    }

    private float positionOf(double value) {
        double span = Math.max(0.000001D,
                rangeSetting.getMaxBound() - rangeSetting.getMinBound());
        return trackLeft() + (float) ((value - rangeSetting.getMinBound()) / span)
                * (trackRight() - trackLeft());
    }

    private float trackLeft() {
        return moduleComponent.categoryComponent.getX() + 4.0F + xOffset / 2.0F;
    }

    private float trackRight() {
        return moduleComponent.categoryComponent.getX()
                + moduleComponent.categoryComponent.getWidth() - 4.0F;
    }

    private enum Handle {
        MINIMUM,
        MAXIMUM
    }
}
