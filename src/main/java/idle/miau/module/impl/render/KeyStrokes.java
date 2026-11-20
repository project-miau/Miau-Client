package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class KeyStrokes extends Module {
    private final SliderSetting posX = new SliderSetting("Position X", 0.8, 0.0, 1.0, 0.01);
    private final SliderSetting posY = new SliderSetting("Position Y", 0.8, 0.0, 1.0, 0.01);
    private final ButtonSetting space = new ButtonSetting("Space", true);

    private boolean isDragging = false;
    private double startedX = 0.0;
    private double startedY = 0.0;
    private double draggedX = 0.0;
    private double draggedY = 0.0;

    private final List<Key> keys = new ArrayList<>();
    private final float scale = 22.0f;
    private final float gap = 3.0f;

    public KeyStrokes() {
        super("KeyStrokes", category.render);
        this.registerSetting(posX);
        this.registerSetting(posY);
        this.registerSetting(space);

        keys.add(new Key("W", mc.gameSettings.keyBindForward, scale + gap, 0.0f, scale, scale));
        keys.add(new Key("A", mc.gameSettings.keyBindLeft, 0.0f, scale + gap, scale, scale));
        keys.add(new Key("S", mc.gameSettings.keyBindBack, scale + gap, scale + gap, scale, scale));
        keys.add(new Key("D", mc.gameSettings.keyBindRight, scale * 2.0f + gap * 2.0f, scale + gap, scale, scale));
        keys.add(new Key("Space", mc.gameSettings.keyBindJump, 0.0f, (scale + gap) * 2.0f, scale * 3.0f + gap * 2.0f, scale));
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat) || mc.gameSettings.showDebugInfo) {
            return;
        }

        render();
    }

    private void render() {
        ScaledResolution resolution = new ScaledResolution(mc);
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        float drawX = (float) (posX.getInput() * scaledWidth);
        float drawY = (float) (posY.getInput() * scaledHeight);

        float renderScale = HudStyle.scale();
        float width = (scale * 3.0f + gap * 2.0f) * renderScale;
        float height = ((scale + gap) * 2.0f + (space.isToggled() ? scale : 0.0f)) * renderScale;

        if (mc.currentScreen instanceof net.minecraft.client.gui.GuiChat) {
            int mouseX = org.lwjgl.input.Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
            int mouseY = resolution.getScaledHeight() - org.lwjgl.input.Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;

            float left = drawX;
            float right = drawX + width;
            float top = drawY;
            float bottom = drawY + height;

            net.minecraft.client.gui.Gui.drawRect((int) left, (int) top, (int) right, (int) bottom, 0x40FFFFFF);

            boolean hovering = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
            double scaleFactor = resolution.getScaleFactor() / Math.pow(resolution.getScaleFactor(), 2.0D);
            if (hovering && !isDragging && org.lwjgl.input.Mouse.isButtonDown(0)) {
                isDragging = true;
                startedX = posX.getInput();
                startedY = posY.getInput();
                draggedX = org.lwjgl.input.Mouse.getX() * scaleFactor;
                draggedY = (mc.displayHeight - org.lwjgl.input.Mouse.getY()) * scaleFactor;
            }
            if (isDragging) {
                if (!org.lwjgl.input.Mouse.isButtonDown(0)) {
                    isDragging = false;
                } else {
                    double mx = org.lwjgl.input.Mouse.getX() * scaleFactor;
                    double my = (mc.displayHeight - org.lwjgl.input.Mouse.getY()) * scaleFactor;

                    double deltaX = (mx - draggedX) / resolution.getScaledWidth();
                    double deltaY = (my - draggedY) / resolution.getScaledHeight();

                    posX.setValue(Math.max(0.0, Math.min(1.0, startedX + deltaX)));
                    posY.setValue(Math.max(0.0, Math.min(1.0, startedY + deltaY)));

                    drawX = (float) (posX.getInput() * scaledWidth);
                    drawY = (float) (posY.getInput() * scaledHeight);
                }
            }
        }

        MiauFontRenderer font = HudStyle.font();
        List<HudStyle.Panel> panels = new ArrayList<>();
        for (Key key : keys) {
            if (key.name.equals("Space") && !space.isToggled()) continue;
            panels.add(key.panel(drawX, drawY, renderScale, font));
        }
        HudStyle.panels(panels);
    }

    private static class Key {
        private final String name;
        private final KeyBinding keyBinding;
        private final float xOffset;
        private final float yOffset;
        private final float width;
        private final float height;
        private float alpha;

        public Key(String name, KeyBinding keyBinding, float xOffset, float yOffset, float width, float height) {
            this.name = name;
            this.keyBinding = keyBinding;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.width = width;
            this.height = height;
        }

        public HudStyle.Panel panel(float x, float y, float scale, MiauFontRenderer font) {
            alpha += ((keyBinding.isKeyDown() ? 1 : 0) - alpha) * 0.2f;
            float left = x + xOffset * scale;
            float top = y + yOffset * scale;
            float keyWidth = width * scale;
            float keyHeight = height * scale;
            return new HudStyle.Panel(left, top, keyWidth, keyHeight, 1, () ->
                    font.drawString(name, left + (keyWidth - font.getStringWidth(name)) / 2,
                            top + (keyHeight - font.getFontHeight()) / 2, 0xFFFFFFFF, false), () -> {
                if (alpha > 0.01f) idle.miau.utility.RenderUtils.drawRoundedRectangle(
                        left, top, left + keyWidth, top + keyHeight, HudStyle.radius(),
                        idle.miau.utility.Utils.mergeAlpha(0xFFFFFFFF, Math.round(alpha * 90)));
            });
        }
    }
}
