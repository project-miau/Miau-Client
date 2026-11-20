package idle.miau.module.impl.render;

import idle.miau.Miau;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.render.HudScale;
import idle.miau.utility.shader.BlurUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PotionHUD extends Module {
    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private final ButtonSetting lowerCase = new ButtonSetting("Potion Lower case", false);
    private final ButtonSetting icons = new ButtonSetting("Potion Icons", true);
    private final ButtonSetting levels = new ButtonSetting("Potion Levels", true);
    private final ButtonSetting durations = new ButtonSetting("Potion Durations", true);
    private final ButtonSetting hideLongEffects = new ButtonSetting("Potion Hide long effects", true);
    private double positionX = 1;
    private double positionY = 1;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    public PotionHUD() {
        super("PotionHUD", category.render);
        setDescription("Displays active potion effects and durations.");
        registerSetting(lowerCase);
        registerSetting(icons);
        registerSetting(levels);
        registerSetting(durations);
        registerSetting(hideLongEffects);
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Utils.nullCheck() && !mc.gameSettings.showDebugInfo
                && (mc.currentScreen == null || mc.currentScreen instanceof GuiChat)) renderFromHud();
    }

    public void renderFromHud() {
        if (!Utils.nullCheck()) return;
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = HudStyle.scale() / (float) HudScale.LEGACY_VISUAL_BASELINE;
        MiauFontRenderer font = FontManager.getHudRenderer("ProductSans-Bold", 0.8f * scale);
        MiauFontRenderer regular = FontManager.getHudRenderer("ProductSans", 0.9f * scale);
        List<EffectRow> rows = new ArrayList<>();
        for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
            Potion potion = potionFor(effect);
            if (potion == null || hideLongEffects.isToggled() && effect.getDuration() > 24000) continue;
            String name = I18n.format(potion.getName());
            if (lowerCase.isToggled()) name = name.toLowerCase(Locale.ROOT);
            if (levels.isToggled()) name += " " + romanLevel(effect.getAmplifier() + 1);
            String duration = durations.isToggled() ? " " + Potion.getDurationString(effect) : "";
            rows.add(row(potion, name, duration, font, regular));
        }
        rows.sort(Comparator.comparingDouble((EffectRow row) -> row.sortWidth).reversed()
                .thenComparing(row -> row.name));
        boolean editing = mc.currentScreen instanceof GuiChat;
        if (rows.isEmpty()) {
            dragging = false;
            if (!editing) return;
            rows.add(row(Potion.moveSpeed, previewName(Potion.moveSpeed, 2), durations.isToggled() ? " 1:30" : "", font, regular));
            rows.add(row(Potion.regeneration, previewName(Potion.regeneration, 1), durations.isToggled() ? " 0:45" : "", font, regular));
            rows.sort(Comparator.comparingDouble((EffectRow row) -> row.sortWidth).reversed().thenComparing(row -> row.name));
        }
        float iconOffset = icons.isToggled() ? 19 * scale : 0;
        float iconTop = icons.isToggled() ? 3.7f * scale : 0;
        float rowHeight = Math.max(font.getFontHeight() + iconTop, icons.isToggled() ? 13 * scale : 0);
        float rowStep = regular.getLineHeight() + 5 * scale;
        float width = (float) rows.stream().mapToDouble(row -> row.width).max().orElse(0) + iconOffset;
        float height = (rows.size() - 1) * rowStep + rowHeight;
        float margin = 5 * scale;
        float maximumRight = Math.max(0, resolution.getScaledWidth() - margin);
        float maximumBottom = resolution.getScaledHeight();
        float right = (float) (positionX * resolution.getScaledWidth()) - margin;
        float bottom = (float) (positionY * resolution.getScaledHeight());
        right = clampEdge(right, width, maximumRight);
        bottom = clampEdge(bottom, height, maximumBottom);
        if (editing && Mouse.isCreated()) {
            float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
            float mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1;
            if (!Mouse.isButtonDown(0)) dragging = false;
            else if (!dragging && mouseX >= right - width && mouseX <= right
                    && mouseY >= bottom - height && mouseY <= bottom) {
                dragging = true;
                dragOffsetX = mouseX - right;
                dragOffsetY = mouseY - bottom;
            }
            if (dragging) {
                right = clampEdge(mouseX - dragOffsetX, width, maximumRight);
                bottom = clampEdge(mouseY - dragOffsetY, height, maximumBottom);
                setPosition((right + margin) / resolution.getScaledWidth(), bottom / resolution.getScaledHeight());
                Miau.markCurrentConfigUnsaved();
            }
        } else dragging = false;
        float textRight = right;
        float textBottom = bottom - rowHeight + iconTop;
        HudStyle.PanelStyle shaders = HudStyle.sharedPanelStyle();
        if (shaders.bloom()) {
            BlurUtils.renderBloom(() -> drawText(rows, font, textRight, textBottom, rowStep, shaders.bloomColor()),
                    shaders.bloomPasses(), shaders.bloomRadius());
        }
        drawText(rows, font, textRight, textBottom, rowStep, 0xFFCCCCCC);
        if (icons.isToggled()) {
            for (int index = 0; index < rows.size(); index++) {
                EffectRow row = rows.get(index);
                if (row.potion.hasStatusIcon()) drawPotionIcon(row.potion, right - row.width - iconOffset + 4 * scale,
                        textBottom - index * rowStep - iconTop, 13 * scale);
            }
        }
    }

    private static float clampEdge(float edge, float size, float maximum) {
        return Math.max(Math.min(size, maximum), Math.min(maximum, edge));
    }

    private String previewName(Potion potion, int level) {
        String name = I18n.format(potion.getName());
        if (lowerCase.isToggled()) name = name.toLowerCase(Locale.ROOT);
        return levels.isToggled() ? name + " " + romanLevel(level) : name;
    }

    private static EffectRow row(Potion potion, String name, String duration, MiauFontRenderer font, MiauFontRenderer regular) {
        float nameWidth = font.getStringWidth(name);
        return new EffectRow(potion, name, duration, nameWidth + regular.getStringWidth(duration),
                nameWidth + font.getStringWidth(duration));
    }

    private static void drawText(List<EffectRow> rows, MiauFontRenderer font, float right, float bottom, float step, int color) {
        for (int index = 0; index < rows.size(); index++) {
            EffectRow row = rows.get(index);
            float x = right - row.width;
            float y = bottom - index * step;
            font.drawString(row.name, x, y, color, false);
            font.drawString(row.duration, x + font.getStringWidth(row.name), y, color, false);
        }
    }

    static String romanLevel(int level) {
        if (level <= 0) return String.valueOf(level);
        int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = Math.min(255, level);
        for (int index = 0; index < values.length; index++) {
            while (remaining >= values[index]) {
                result.append(symbols[index]);
                remaining -= values[index];
            }
        }
        return result.toString();
    }

    private static Potion potionFor(PotionEffect effect) {
        int id = effect.getPotionID();
        return id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
    }

    public double getPositionX() {
        return positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public void setPosition(double x, double y) {
        if (Double.isFinite(x)) positionX = Math.max(0, Math.min(1, x));
        if (Double.isFinite(y)) positionY = Math.max(0, Math.min(1, y));
    }

    private record EffectRow(Potion potion, String name, String duration, float width, float sortWidth) {
    }

    private void drawPotionIcon(Potion potion, float x, float y, float iconWidth) {
        int iconIndex = potion.getStatusIconIndex();
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            float iconScale = iconWidth / 18.0F;
            GlStateManager.scale(iconScale, iconScale, 1.0F);

            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GlStateManager.disableTexture2D();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.resetColor();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

            if (scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GlStateManager.bindTexture(0);
            mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
            Gui.drawScaledCustomSizeModalRect(
                    0,
                    0,
                    iconIndex % 8 * 18,
                    198 + iconIndex / 8 * 19,
                    18,
                    18,
                    18,
                    18,
                    256.0F,
                    256.0F);
        }
        finally {
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            GlStateManager.depthMask(depthWrite);
            if (depthEnabled) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableAlpha();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GlStateManager.enableTexture2D();
            GlStateManager.resetColor();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
