package idle.miau.module.impl.render;

import idle.miau.Miau;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.BlurUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public final class Watermark extends Module {
    private static final String CLIENT_NAME = "Miau";
    private final ButtonSetting glow = new ButtonSetting("Glow shadow", true);
    private final SliderSetting scale = new SliderSetting("Scale", 2.0, 0.5, 3.0, 0.1);

    private double positionX = 0.02;
    private double positionY = 0.02;
    private boolean isDragging;
    private double startedX;
    private double startedY;
    private double draggedX;
    private double draggedY;

    public Watermark() {
        super("Watermark", category.render);
        setDescription("Meowww !?!?!??!");
        registerSetting(glow);
        registerSetting(scale);
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public void setPosition(double x, double y) {
        if (Double.isFinite(x)) {
            positionX = Math.max(0.0D, Math.min(1.0D, x));
        }
        if (Double.isFinite(y)) {
            positionY = Math.max(0.0D, Math.min(1.0D, y));
        }
    }

    @Override
    public void onDisable() {
        isDragging = false;
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!Utils.nullCheck() || mc.gameSettings.showDebugInfo
                || (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat))) {
            isDragging = false;
            return;
        }
        render();
    }

    private void render() {
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        MiauFontRenderer font = FontManager.getHudRenderer("MetalGothic", (float) scale.getInput());
        float width = font.getStringWidth(CLIENT_NAME);
        float height = font.getFontHeight();

        updateDragging(resolution, width, height);
        float x = (float) (positionX * scaledWidth);
        float y = (float) (positionY * scaledHeight);
        if (mc.currentScreen instanceof GuiChat) {
            Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), 0x40FFFFFF);
        }

        MiauFontRenderer.GlyphColorProvider colors = (character, xOffset, glyphWidth, formattingColor) ->
                ArrayListModule.getHudColorAtPosition(x + xOffset, y + xOffset);
        if (glow.isToggled()) {
            BlurUtils.renderBloom(() -> font.drawGlyphString(CLIENT_NAME, x, y, colors, false), 2, 2.0F);
        }
        font.drawGlyphString(CLIENT_NAME, x, y, colors, true);
    }

    private void updateDragging(ScaledResolution resolution, float width, float height) {
        if (!(mc.currentScreen instanceof GuiChat) || !Mouse.isButtonDown(0)) {
            isDragging = false;
            return;
        }

        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        double mouseX = Mouse.getX() * scaledWidth / (double) mc.displayWidth;
        double mouseY = scaledHeight - Mouse.getY() * scaledHeight / (double) mc.displayHeight - 1.0D;
        double x = positionX * scaledWidth;
        double y = positionY * scaledHeight;

        if (!isDragging) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
                return;
            }
            isDragging = true;
            startedX = positionX;
            startedY = positionY;
            draggedX = mouseX;
            draggedY = mouseY;
        }

        double previousX = positionX;
        double previousY = positionY;
        setPosition(startedX + (mouseX - draggedX) / scaledWidth, startedY + (mouseY - draggedY) / scaledHeight);
        if (Double.compare(previousX, positionX) != 0 || Double.compare(previousY, positionY) != 0) {
            Miau.markCurrentConfigUnsaved();
        }
    }
}
