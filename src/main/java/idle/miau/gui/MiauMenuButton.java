package idle.miau.gui;

import java.awt.Color;

import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class MiauMenuButton extends GuiButton {
    private static final String HOVER_INDICATOR = "<";
    private static final ResourceLocation HOVER_SOUND =
            new ResourceLocation("miau", "gui.menu_button_hover");
    private static final float HOVER_INDICATOR_GAP = 3.0F;
    private static final float ENTRANCE_DELAY_PER_ITEM_SECONDS = 0.035F;
    private static final float ENTRANCE_DURATION_SECONDS = 0.22F;
    private static final Color NORMAL_TEXT = new Color(235, 237, 242);
    private static final Color HOVER_TEXT = Color.WHITE;

    private final int animationOrder;
    private final MiauFontRenderer fontRenderer;
    private final boolean rightAligned;
    private final long animationStartedAt;
    private long lastFrameAt;
    private float hoverProgress;
    private boolean hoverSoundActive;

    public MiauMenuButton(int id, int x, int y, int width, int height,
                          String label, int animationOrder,
                          MiauFontRenderer fontRenderer) {
        this(id, x, y, width, height, label, animationOrder, fontRenderer, false);
    }

    public MiauMenuButton(int id, int x, int y, int width, int height,
                          String label, int animationOrder,
                          MiauFontRenderer fontRenderer, boolean rightAligned) {
        super(id, x, y, width, height, label);
        this.animationOrder = animationOrder;
        this.fontRenderer = fontRenderer;
        this.rightAligned = rightAligned;
        this.animationStartedAt = System.nanoTime();
        this.lastFrameAt = this.animationStartedAt;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible) {
            this.hoverSoundActive = false;
            return;
        }

        long now = System.nanoTime();
        float deltaSeconds = Math.min((now - this.lastFrameAt) / 1_000_000_000.0F, 0.05F);
        this.lastFrameAt = now;
        this.hovered = mouseX >= this.xPosition
                && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;

        float hoverTarget = this.enabled && this.hovered ? 1.0F : 0.0F;
        this.hoverProgress += (hoverTarget - this.hoverProgress) * Math.min(1.0F, deltaSeconds * 14.0F);

        float elapsedSeconds = (now - this.animationStartedAt) / 1_000_000_000.0F;
        float entrance = calculateEntranceProgress(elapsedSeconds, this.animationOrder);
        if (this.updateHoverSoundState(
                this.enabled && this.hovered && entrance > 0.0F)) {
            minecraft.getSoundHandler().playSound(
                    PositionedSoundRecord.create(HOVER_SOUND));
        }
        if (entrance <= 0.0F) {
            return;
        }

        float centerX = this.xPosition + this.width / 2.0F;
        float centerY = this.yPosition + this.height / 2.0F;
        float scale = 0.97F + entrance * 0.03F;
        Color text = this.enabled
                ? interpolate(NORMAL_TEXT, HOVER_TEXT, this.hoverProgress)
                : new Color(125, 128, 138);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY + (1.0F - entrance) * 4.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);

        int textWidth = this.fontRenderer.getStringWidth(this.displayString);
        int indicatorWidth = this.rightAligned
                ? this.fontRenderer.getStringWidth(HOVER_INDICATOR)
                : 0;
        float indicatorSpace = this.rightAligned
                ? (indicatorWidth + HOVER_INDICATOR_GAP) * this.hoverProgress
                : 0.0F;
        float textX = this.xPosition
                + (this.rightAligned
                ? this.width - textWidth - indicatorSpace
                : 0.0F);
        float textY = this.yPosition
                + (this.height - this.fontRenderer.getFontHeight()) / 2.0F;
        this.fontRenderer.drawString(this.displayString, textX, textY, text.getRGB(), false);

        if (this.rightAligned && this.hoverProgress > 0.01F) {
            int indicatorAlpha = Math.max(1, Math.round(255.0F * this.hoverProgress));
            Color indicatorColor = new Color(
                    text.getRed(),
                    text.getGreen(),
                    text.getBlue(),
                    indicatorAlpha);
            float indicatorRight = this.xPosition + this.width;

            GlStateManager.pushMatrix();
            GlStateManager.translate(indicatorRight, centerY, 0.0F);
            GlStateManager.scale(this.hoverProgress, this.hoverProgress, 1.0F);
            GlStateManager.translate(-indicatorRight, -centerY, 0.0F);
            this.fontRenderer.drawString(
                    HOVER_INDICATOR,
                    indicatorRight - indicatorWidth,
                    textY,
                    indicatorColor.getRGB(),
                    false);
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    boolean updateHoverSoundState(boolean active) {
        boolean shouldPlay = active && !this.hoverSoundActive;
        this.hoverSoundActive = active;
        return shouldPlay;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static float calculateEntranceProgress(float elapsedSeconds, int animationOrder) {
        float delaySeconds = Math.max(0, animationOrder)
                * ENTRANCE_DELAY_PER_ITEM_SECONDS;
        float progress = clamp(
                (elapsedSeconds - delaySeconds) / ENTRANCE_DURATION_SECONDS);
        return 1.0F - (float) Math.pow(1.0F - progress, 3.0D);
    }

    private static Color interpolate(Color start, Color end, float amount) {
        float progress = clamp(amount);
        return new Color(
                Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress),
                Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress),
                Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress),
                Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * progress));
    }
}
