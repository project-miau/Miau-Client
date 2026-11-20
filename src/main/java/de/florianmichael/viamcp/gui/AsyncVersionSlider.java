/*
 * This file is derived from ViaMCP and is licensed under GPL-3.0.
 * https://github.com/ViaVersionMCP/ViaMCP
 */
package de.florianmichael.viamcp.gui;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AsyncVersionSlider extends GuiButton {
    private final List<ProtocolVersion> values;
    private float sliderValue;
    private boolean dragging;

    public AsyncVersionSlider(int buttonId, int x, int y, int width, int height) {
        super(buttonId, x, y, Math.max(width, 110), height, "");
        values = new ArrayList<>(ViaLoadingBase.PROTOCOLS);
        Collections.reverse(values);

        ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
        int selected = Math.max(0, values.indexOf(target));
        sliderValue = values.size() <= 1 ? 0.0F : (float) selected / (float) (values.size() - 1);
        updateDisplay(selected);
        enabled = values.size() > 1;
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }

        if (dragging) {
            selectFromMouse(mouseX);
        }

        mc.getTextureManager().bindTexture(buttonTextures);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int handleX = xPosition + (int) (sliderValue * (float) (width - 8));
        drawTexturedModalRect(handleX, yPosition, 0, 66, 4, 20);
        drawTexturedModalRect(handleX + 4, yPosition, 196, 66, 4, 20);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) {
            return false;
        }
        selectFromMouse(mouseX);
        dragging = true;
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    private void selectFromMouse(int mouseX) {
        if (values.isEmpty()) {
            return;
        }
        sliderValue = (float) (mouseX - (xPosition + 4)) / (float) (width - 8);
        sliderValue = MathHelper.clamp_float(sliderValue, 0.0F, 1.0F);
        int selected = Math.min(values.size() - 1,
                (int) Math.ceil(sliderValue * (values.size() - 1)));
        ProtocolVersion version = values.get(selected);
        updateDisplay(selected);
        ViaLoadingBase.getInstance().reload(version);
    }

    private void updateDisplay(int selected) {
        displayString = values.isEmpty() ? "Via unavailable" : values.get(selected).getName();
    }
}
