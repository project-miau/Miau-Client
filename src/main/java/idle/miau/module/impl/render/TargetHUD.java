package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Timer;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.BlurUtils;
import idle.miau.utility.shader.RoundedUtils;
import idle.miau.utility.render.HudScale;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public class TargetHUD extends Module {
    private static final String[] ANIMATION_MODES = {"None", "Move in", "Scale in"};
    private static final String[] MODES = {"Raven", "Miau", "Myau"};
    private static final String[] RAVEN_MODES = {"Legacy", "Modern"};
    private static final String[] MYAU_COLORS = {"Default", "HUD"};
    private static final int RAVEN_MODE = 0;
    private static final int MIAU_MODE = 1;
    private static final int MYAU_MODE = 2;
    private static final int MAX_MULTI_TARGETS = 4;
    private static final float MULTI_CARD_GAP = 6.0F;
    private static final int MULTI_CARD_COLUMNS = 2;
    private SliderSetting mode;
    private SliderSetting ravenMode;
    private SliderSetting animation;
    private SliderSetting hudScale;
    private SliderSetting myauColor;
    private SliderSetting myauBackground;
    private ButtonSetting renderEsp;
    private ButtonSetting showStatus;
    private ButtonSetting healthColor;
    private ButtonSetting multiTargets;
    private ButtonSetting myauHead;
    private ButtonSetting myauIndicator;
    private ButtonSetting myauOutline;
    private ButtonSetting myauAnimations;
    private ButtonSetting textShadow;
    private Timer fadeTimer;
    private EntityLivingBase target;
    public EntityLivingBase renderEntity;
    public int posX = 70;
    public int posY = 30;
    private final Map<EntityLivingBase, DamageHealthAnimation> healthAnimations = new WeakHashMap<>();
    private final Map<EntityLivingBase, RavenHealthAnimation> ravenHealthAnimations = new WeakHashMap<>();
    private final Map<EntityLivingBase, MyauHealthAnimation> myauHealthAnimations = new WeakHashMap<>();
    private final Map<EntityLivingBase, ResourceLocation> myauHeadTextures = new WeakHashMap<>();
    private final MiauHudAnimation hudAnimation =
            new MiauHudAnimation(false, System.currentTimeMillis());

    private boolean isDragging;
    private double draggedX, draggedY, startedX, startedY;

    public TargetHUD() {
        super("TargetHUD", category.render);
        this.registerSetting(new DescriptionSetting("Only works with KillAura."));
        this.registerSetting(mode = new SliderSetting("Mode", MIAU_MODE, MODES));
        this.registerSetting(ravenMode = new SliderSetting("Raven mode", 0, RAVEN_MODES));
        this.registerSetting(animation = new SliderSetting("Animation", 2, ANIMATION_MODES));
        this.registerSetting(hudScale = new SliderSetting("Scale", 1, 0.5, 2.0, 0.01));
        this.registerSetting(multiTargets = new ButtonSetting("Multi targets", true));
        this.registerSetting(renderEsp = new ButtonSetting("Render ESP", true));
        this.registerSetting(showStatus = new ButtonSetting("Show win or loss", true));
        this.registerSetting(healthColor = new ButtonSetting("Traditional health color", false));
        this.registerSetting(myauColor = new SliderSetting("Color", 0, MYAU_COLORS));
        this.registerSetting(myauBackground = new SliderSetting("Background", "%", 25, 0, 100, 1));
        this.registerSetting(myauHead = new ButtonSetting("Head", true));
        this.registerSetting(myauIndicator = new ButtonSetting("Indicator", true));
        this.registerSetting(myauOutline = new ButtonSetting("Outline", false));
        this.registerSetting(myauAnimations = new ButtonSetting("Animations", true));
        this.registerSetting(textShadow = new ButtonSetting("Shadow", true));
    }

    public void onDisable() {
        reset();
    }

    @Override
    public void guiUpdate() {
        int selectedMode = (int) mode.getInput();
        boolean raven = selectedMode == RAVEN_MODE;
        boolean miau = selectedMode == MIAU_MODE;
        boolean myau = selectedMode == MYAU_MODE;
        if (ravenMode != null) {
            ravenMode.setVisible(raven, this);
        }
        if (showStatus != null) {
            showStatus.setVisible(raven, this);
        }
        if (healthColor != null) {
            healthColor.setVisible(raven, this);
        }
        if (multiTargets != null) {
            multiTargets.setVisible(miau, this);
        }
        myauColor.setVisible(myau, this);
        myauBackground.setVisible(myau, this);
        myauHead.setVisible(myau, this);
        myauIndicator.setVisible(myau, this);
        myauOutline.setVisible(myau, this);
        myauAnimations.setVisible(myau, this);
        textShadow.setVisible(raven || myau, this);
    }

    @Override
    public void onEnable() {
        reset();
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode || setting == ravenMode) {
            healthAnimations.clear();
            ravenHealthAnimations.clear();
            myauHealthAnimations.clear();
            myauHeadTextures.clear();
            guiUpdate();
        }
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Utils.nullCheck()) {
            reset();
            return;
        }
        if (ev.phase == TickEvent.Phase.END) {
            int selectedMode = (int) mode.getInput();
            long now = System.currentTimeMillis();
            updateAnimatedTarget(selectedMode, now);
            if (target == null) {
                return;
            }
            float progress = getAnimationProgress(now);
            if (progress <= 0.0F && !isNoAnimation()) {
                return;
            }
            beginHudScale();
            beginTargetAnimation(progress);
            try {
                if (selectedMode == MIAU_MODE) {
                    drawMiauTargets(isScaleInAnimation() ? progress : 1.0F);
                } else if (selectedMode == MYAU_MODE) {
                    drawMyauTargetHUD(target, isScaleInAnimation() ? progress : 1.0F);
                } else {
                    drawRavenTargetHUD(target);
                }
            }
            finally {
                endTargetAnimation();
                endHudScale();
            }
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent renderWorldLastEvent) {
        if (!renderEsp.isToggled() || !Utils.nullCheck() || !canRenderWorldEsp(mc.currentScreen)) {
            return;
        }
        if (KillAura.target != null) {
            RenderUtils.renderEntity(KillAura.target, 2, 0.0, 0.0,
                    ArrayListModule.getHudColor(0.0D), false);
        }
        else if (renderEntity != null) {
            RenderUtils.renderEntity(renderEntity, 2, 0.0, 0.0,
                    ArrayListModule.getHudColor(0.0D), false);
        }
    }

    static boolean canRenderWorldEsp(GuiScreen screen) {
        return !(screen instanceof GuiChat);
    }

    private void drawRavenTargetHUD(EntityLivingBase player) {
        double health = player.isDead ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, player.getHealth() / player.getMaxHealth()));
        String healthText = player.getHealth() == (int) player.getHealth()
                ? String.valueOf((int) player.getHealth())
                : String.format(Locale.ROOT, "%.1f", player.getHealth());
        String playerInfo = player.getDisplayName().getFormattedText();
        if (health >= 0.5D) {
            playerInfo += " \u00A7a" + healthText;
        } else if (player.getHealth() >= 0.2F) {
            playerInfo += " \u00A7e" + healthText;
        } else {
            playerInfo += " \u00A7c" + healthText;
        }
        if (showStatus.isToggled()) {
            float selfHealth = Utils.getTotalHealth(mc.thePlayer) / mc.thePlayer.getMaxHealth();
            playerInfo += health <= selfHealth ? " \u00A7aW" : " \u00A7cL";
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        MiauFontRenderer font = targetHudFont(22);
        float padding = 8.0F;
        float width = font.getStringWidth(playerInfo) + padding * 3.0F;
        float height = font.getFontHeight() + 12.0F + padding * 2.0F;
        float x = resolution.getScaledWidth() / 2.0F - width / 2.0F + posX;
        float y = resolution.getScaledHeight() / 2.0F + 7.0F + posY;
        handleMiauDrag(x, y, width, height);

        int alpha = fadeTimer == null ? 255 : 255 - fadeTimer.getValueInt(0, 255, 1);
        if (alpha <= 0) {
            target = null;
            return;
        }

        int maxAlphaOutline = Math.min(alpha, 110);
        int maxAlphaBackground = Math.min(alpha, 210);
        int[] gradientColors = {
                ArrayListModule.getHudColor(0.0D),
                ArrayListModule.getHudColor(48.0D)
        };
        boolean modern = (int) ravenMode.getInput() == 1;
        if (modern) {
            float bloomRadius = fadeTimer == null ? 2.0F : 2.0F * alpha / 255.0F;
            float blurRadius = fadeTimer == null ? 3.0F : 3.0F * alpha / 255.0F;
            boolean bloomRendered = BlurUtils.renderBloom(
                    () -> RoundedUtils.drawRound(x, y, width, height, 8.0F, true,
                            new Color(0, 0, 0, maxAlphaBackground)), 3, bloomRadius);
            boolean blurRendered = BlurUtils.renderBlur(
                    () -> RoundedUtils.drawRound(x, y, width, height, 8.0F, true,
                            Utils.mergeAlpha(Color.BLACK.getRGB(), maxAlphaOutline)), 2, blurRadius);
            if (!bloomRendered && !blurRendered) {
                RenderUtils.drawRoundedRectangle(x, y, x + width, y + height, 8.0F,
                        Utils.mergeAlpha(Color.BLACK.getRGB(), maxAlphaBackground));
            }
        } else {
            RenderUtils.drawRoundedGradientOutlinedRectangle(x, y, x + width, y + height,
                    10.0F, Utils.mergeAlpha(Color.BLACK.getRGB(), maxAlphaOutline),
                    Utils.mergeAlpha(gradientColors[0], alpha),
                    Utils.mergeAlpha(gradientColors[1], alpha));
        }

        float barLeft = x + 6.0F;
        float barRight = x + width - 6.0F;
        float barTop = y + height - 13.0F;
        float barBottom = barTop + 5.0F;
        RenderUtils.drawRoundedRectangle(barLeft, barTop, barRight, barBottom, 4.0F,
                Utils.mergeAlpha(Color.BLACK.getRGB(), maxAlphaOutline));

        int leftColor = Utils.mergeAlpha(gradientColors[0], maxAlphaBackground);
        int rightColor = Utils.mergeAlpha(gradientColors[1], maxAlphaBackground);
        if (healthColor.isToggled()) {
            int traditionalColor = getHealthBlend((float) health, maxAlphaBackground).getRGB();
            leftColor = traditionalColor;
            rightColor = traditionalColor;
        }

        long now = System.currentTimeMillis();
        RavenHealthAnimation animation = ravenHealthAnimationFor(player);
        animation.update((float) health, now, modern ? 500L : 350L, modern);
        float animatedHealth = animation.getOutput();
        float barWidth = barRight - barLeft;
        if (modern) {
            float visibleHealth = Math.min((float) health, animatedHealth);
            if (animatedHealth > 0.0F) {
                Color trailingColor = darken(new Color(rightColor, true), 0.75F, maxAlphaBackground);
                RenderUtils.drawRoundedRectangle(barLeft, barTop,
                        barLeft + barWidth * animatedHealth, barBottom, 4.0F, trailingColor.getRGB());
            }
            if (visibleHealth > 0.0F) {
                RenderUtils.drawRoundedGradientRect(barLeft, barTop,
                        barLeft + barWidth * visibleHealth, barBottom, 4.0F,
                        leftColor, leftColor, rightColor, rightColor);
            }
        } else if (animatedHealth > 0.0F) {
            RenderUtils.drawRoundedGradientRect(barLeft, barTop,
                    barLeft + barWidth * animatedHealth, barBottom, 4.0F,
                    leftColor, leftColor, rightColor, rightColor);
        }

        int textColor = (new Color(220, 220, 220).getRGB() & 0xFFFFFF)
                | Utils.clamp(alpha + 15) << 24;
        GlStateManager.enableBlend();
        font.drawString(playerInfo, x + padding, y + padding,
                textColor, textShadow.isToggled());
        GlStateManager.disableBlend();
    }

    private RavenHealthAnimation ravenHealthAnimationFor(EntityLivingBase entity) {
        RavenHealthAnimation animation = ravenHealthAnimations.get(entity);
        if (animation == null) {
            animation = new RavenHealthAnimation();
            ravenHealthAnimations.put(entity, animation);
        }
        return animation;
    }

    private void drawMyauTargetHUD(EntityLivingBase player, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }

        float selfHealth = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
        float absorption = player.getAbsorptionAmount() / 2.0F;
        float targetHealth = player.getHealth() / 2.0F + absorption;
        float maximumHealth = Math.max(0.5F, player.getMaxHealth() / 2.0F);
        long now = System.currentTimeMillis();

        MyauHealthAnimation valueAnimation = myauHealthAnimationFor(player);
        float healthRatio = valueAnimation.update(targetHealth, maximumHealth,
                myauAnimations.isToggled(), now);
        float rawHealthRatio = Math.max(0.0F, Math.min(1.0F, targetHealth / maximumHealth));
        int alphaValue = Math.max(0, Math.min(255, (int) (255.0F * alpha)));
        Color targetColor = getMyauTargetColor(player, alphaValue);
        Color healthBarColor = (int) myauColor.getInput() == 0
                ? getHealthBlend(healthRatio, alphaValue)
                : targetColor;
        float healthDeltaRatio = Math.max(0.0F,
                Math.min(1.0F, (selfHealth - targetHealth + 1.0F) / 2.0F));
        Color healthDeltaColor = getHealthBlend(healthDeltaRatio, alphaValue);

        ResourceLocation headTexture = getMyauHeadTexture(player);
        String targetNameText = "\u00A7r" + stripMyauName(player) + "\u00A7r";
        String healthText = "\u00A7r\u00A7f" + String.format(Locale.ROOT, "%.1f", targetHealth)
                + (absorption > 0.0F ? "\u00A76" : "\u00A7c") + "\u2764\u00A7r";
        boolean tied = Math.abs(targetHealth - selfHealth) < 0.0001F;
        String statusText = "\u00A7r\u00A7l"
                + (tied ? "D" : targetHealth < selfHealth ? "W" : "L") + "\u00A7r";
        String differenceText = "\u00A7r"
                + (tied ? "0.0" : String.format(Locale.ROOT, "%+.1f", selfHealth - targetHealth))
                + "\u00A7r";

        MiauFontRenderer font = targetHudFont(22);
        int targetNameWidth = font.getStringWidth(targetNameText);
        int healthTextWidth = font.getStringWidth(healthText);
        int statusTextWidth = font.getStringWidth(statusText);
        int differenceTextWidth = font.getStringWidth(differenceText);
        float indicatorNameWidth = myauIndicator.isToggled() ? 2.0F + statusTextWidth + 2.0F : 0.0F;
        float indicatorHealthWidth = myauIndicator.isToggled() ? 2.0F + differenceTextWidth + 2.0F : 0.0F;
        float barContentWidth = Math.max(targetNameWidth + indicatorNameWidth,
                healthTextWidth + indicatorHealthWidth);
        float headIconOffset = myauHead.isToggled() && headTexture != null ? 25.0F : 0.0F;
        float barTotalWidth = Math.max(headIconOffset + 70.0F,
                headIconOffset + 2.0F + barContentWidth + 2.0F);

        float[] origin = getMiauOrigin(barTotalWidth);
        float x = origin[0];
        float y = origin[1];
        handleMiauDrag(x, y, barTotalWidth, 27.0F);

        int backgroundAlpha = Math.max(0, Math.min(255,
                (int) (255.0D * myauBackground.getInput() / 100.0D * alpha)));
        int backgroundColor = Utils.mergeAlpha(Color.BLACK.getRGB(), backgroundAlpha);
        int outlineColor = myauOutline.isToggled() ? targetColor.getRGB() : 0;
        drawMyauOutlineRect(x, y, x + barTotalWidth, y + 27.0F,
                1.5F, backgroundColor, outlineColor);

        float barLeft = x + headIconOffset + 2.0F;
        float barRight = x + barTotalWidth - 2.0F;
        float barTop = y + 22.0F;
        float barBottom = y + 25.0F;
        RenderUtils.drawRect(barLeft, barTop, barRight, barBottom,
                darken(healthBarColor, 0.2F, alphaValue).getRGB());

        DamageHealthAnimation damageAnimation = healthAnimationFor(player);
        if (myauAnimations.isToggled()) {
            damageAnimation.update(rawHealthRatio, now);
        } else {
            damageAnimation.snap(rawHealthRatio);
        }
        float healthBarWidth = barRight - barLeft;
        float damageWidth = healthBarWidth * damageAnimation.getDamage();
        if (damageWidth > 0.0F) {
            RenderUtils.drawRect(barLeft, barTop, barLeft + damageWidth, barBottom,
                    withAlpha(healthBarColor.getRGB(), (int) (95.0F * alpha)).getRGB());
        }
        if (healthRatio > 0.0F) {
            RenderUtils.drawRect(barLeft, barTop, barLeft + healthRatio * healthBarWidth,
                    barBottom, healthBarColor.getRGB());
        }

        int textColor = Utils.mergeAlpha(Color.WHITE.getRGB(), alphaValue);
        font.drawString(targetNameText, x + headIconOffset + 2.0F,
                y + 2.0F, textColor, textShadow.isToggled());
        font.drawString(healthText, x + headIconOffset + 2.0F,
                y + 12.0F, textColor, textShadow.isToggled());
        if (myauIndicator.isToggled()) {
            font.drawString(statusText,
                    x + barTotalWidth - 2.0F - statusTextWidth, y + 2.0F,
                    healthDeltaColor.getRGB(), textShadow.isToggled());
            font.drawString(differenceText,
                    x + barTotalWidth - 2.0F - differenceTextWidth, y + 12.0F,
                    darken(healthDeltaColor, 0.8F, alphaValue).getRGB(), textShadow.isToggled());
        }
        if (myauHead.isToggled() && headTexture != null) {
            drawMyauHead(headTexture, x + 2.0F, y + 2.0F, alpha);
        }
    }

    private MyauHealthAnimation myauHealthAnimationFor(EntityLivingBase entity) {
        MyauHealthAnimation animation = myauHealthAnimations.get(entity);
        if (animation == null) {
            animation = new MyauHealthAnimation();
            myauHealthAnimations.put(entity, animation);
        }
        return animation;
    }

    private ResourceLocation getMyauHeadTexture(EntityLivingBase entity) {
        ResourceLocation texture = myauHeadTextures.get(entity);
        if (entity instanceof EntityPlayer && mc.getNetHandler() != null) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entity.getName());
            if (playerInfo != null) {
                texture = playerInfo.getLocationSkin();
                myauHeadTextures.put(entity, texture);
            }
        }
        return texture;
    }

    private Color getMyauTargetColor(EntityLivingBase entity, int alpha) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (Utils.isFriended(player)) {
                return new Color(0, 170, 0, alpha);
            }
            if (Utils.isEnemy(player)) {
                return new Color(170, 0, 0, alpha);
            }
        }
        if ((int) myauColor.getInput() == 0) {
            float ratio = Math.max(0.0F,
                    Math.min(1.0F, entity.getHealth() / Math.max(1.0F, entity.getMaxHealth())));
            return getHealthBlend(ratio, alpha);
        }
        return withAlpha(ArrayListModule.getHudColor(0.0D), alpha);
    }

    private String stripMyauName(EntityLivingBase entity) {
        if (entity.getDisplayName() == null) {
            return "";
        }
        return entity.getDisplayName().getFormattedText()
                .replaceAll("\\u00A7\\S$", "")
                .replaceAll("(?i)\\u00A7r", "\u00A7f")
                .trim();
    }

    private void drawMyauHead(ResourceLocation texture, float x, float y, float alpha) {
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.color(1.0F, 1.0F, 1.0F,
                Math.max(0.0F, Math.min(1.0F, alpha)));
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y,
                8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y,
                40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    private void drawMyauOutlineRect(float left, float top, float right, float bottom,
                                     float lineWidth, int backgroundColor, int lineColor) {
        RenderUtils.drawRect(left, top, right, bottom, backgroundColor);
        if ((lineColor >>> 24) == 0) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f((lineColor >> 16 & 255) / 255.0F,
                (lineColor >> 8 & 255) / 255.0F,
                (lineColor & 255) / 255.0F,
                (lineColor >>> 24) / 255.0F);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private Color getHealthBlend(float ratio, int alpha) {
        float percent = Math.max(0.0F, Math.min(1.0F, ratio));
        Color color;
        if (percent >= 0.9F) {
            color = Color.GREEN;
        } else if (percent >= 0.55F) {
            color = interpolateColor(Color.YELLOW, Color.GREEN, (percent - 0.55F) / 0.35F);
        } else if (percent >= 0.45F) {
            color = Color.YELLOW;
        } else if (percent >= 0.1F) {
            color = interpolateColor(Color.RED, Color.YELLOW, (percent - 0.1F) / 0.35F);
        } else {
            color = Color.RED;
        }
        return withAlpha(color.getRGB(), alpha);
    }

    private Color interpolateColor(Color start, Color end, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return new Color(
                (int) (start.getRed() + clamped * (end.getRed() - start.getRed())),
                (int) (start.getGreen() + clamped * (end.getGreen() - start.getGreen())),
                (int) (start.getBlue() + clamped * (end.getBlue() - start.getBlue())));
    }

    private Color darken(Color color, float factor, int alpha) {
        float clamped = Math.max(0.0F, Math.min(1.0F, factor));
        return new Color((int) (color.getRed() * clamped),
                (int) (color.getGreen() * clamped),
                (int) (color.getBlue() * clamped),
                Math.max(0, Math.min(255, alpha)));
    }

    private void drawMiauTargets(float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        List<EntityLivingBase> targets = collectMiauTargets();
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() == 1) {
            drawMiauTargetHUD(targets.get(0), alpha);
            return;
        }
        drawMiauMultiTargetHUD(targets, alpha);
    }

    private List<EntityLivingBase> collectMiauTargets() {
        List<EntityLivingBase> result = new ArrayList<>();
        if (mc.currentScreen instanceof GuiChat) {
            if (target != null) {
                result.add(target);
            }
            return result;
        }
        if (multiTargets.isToggled()) {
            if (isValidHudTarget(target)) {
                result.add(target);
            }
            KillAura aura = (KillAura) ModuleManager.getModule(KillAura.class);
            if (aura != null && aura.isEnabled()) {
                for (EntityLivingBase candidate : KillAura.targetList) {
                    if (result.size() >= MAX_MULTI_TARGETS) {
                        break;
                    }
                    if (candidate == target || !isValidHudTarget(candidate)) {
                        continue;
                    }
                    result.add(candidate);
                }
            }
        }
        else if (isValidHudTarget(target)) {
            result.add(target);
        }
        return result;
    }

    private static boolean isValidHudTarget(EntityLivingBase entity) {
        return entity != null && !entity.isDead && entity.getHealth() > 0.0F;
    }

    private void drawMiauMultiTargetHUD(List<EntityLivingBase> targets, float alpha) {
        MiauFontRenderer titleFont = targetHudFont(26);
        float cardWidth = 145.0F;
        for (EntityLivingBase entity : targets) {
            cardWidth = Math.max(cardWidth, titleFont.getStringWidth(entity.getName()) + 40.0F);
        }
        float cardHeight = 37.0F;
        int columns = Math.min(targets.size(), MULTI_CARD_COLUMNS);
        int rows = (targets.size() + columns - 1) / columns;
        float totalWidth = columns * cardWidth + (columns - 1) * MULTI_CARD_GAP;
        float totalHeight = rows * cardHeight + (rows - 1) * MULTI_CARD_GAP;

        float[] origin = getMiauOrigin(totalWidth);
        handleMiauDrag(origin[0], origin[1], totalWidth, totalHeight);

        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = multiTargetScale(targets.size());
        float maxWidth = resolution.getScaledWidth() * 0.5F;
        float maxHeight = resolution.getScaledHeight() * 0.35F;
        scale = Math.min(scale, Math.min(maxWidth / totalWidth, maxHeight / totalHeight));
        scale = Math.max(scale, 0.35F);

        float pivotX = origin[0] + totalWidth / 2.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(pivotX, origin[1], 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-pivotX, -origin[1], 0.0F);
        try {
            for (int index = 0; index < targets.size(); index++) {
                int column = index % columns;
                int row = index / columns;
                float x = origin[0] + column * (cardWidth + MULTI_CARD_GAP);
                float y = origin[1] + row * (cardHeight + MULTI_CARD_GAP);
                drawMiauCard(targets.get(index), x, y, cardWidth, alpha, healthAnimationFor(targets.get(index)));
            }
        }
        finally {
            GlStateManager.popMatrix();
        }
    }

    static float multiTargetScale(int count) {
        if (count <= 1) {
            return 1.0F;
        }
        if (count == 2) {
            return 0.9F;
        }
        if (count == 3) {
            return 0.8F;
        }
        return 0.72F;
    }

    private DamageHealthAnimation healthAnimationFor(EntityLivingBase entity) {
        DamageHealthAnimation animation = healthAnimations.get(entity);
        if (animation == null) {
            animation = new DamageHealthAnimation();
            healthAnimations.put(entity, animation);
        }
        return animation;
    }

    private void drawMiauTargetHUD(EntityLivingBase player, float alpha) {
        MiauFontRenderer titleFont = targetHudFont(26);
        float width = Math.max(145.0F, titleFont.getStringWidth(player.getName()) + 40.0F);
        float[] origin = getMiauOrigin(width);
        handleMiauDrag(origin[0], origin[1], width, 37.0F);
        drawMiauCard(player, origin[0], origin[1], width, alpha, healthAnimationFor(player));
    }

    private void drawMiauCard(EntityLivingBase player, float x, float y, float width, float alpha,
                              DamageHealthAnimation healthAnimation) {
        MiauFontRenderer titleFont = targetHudFont(26);
        MiauFontRenderer fallbackFont = targetHudFont(32);
        float height = 37.0F;
        if (alpha <= 0.0F) {
            return;
        }

        Color[] colors = getTargetHudColors((int) (255.0F * alpha));
        int textColor = withAlpha(Color.WHITE.getRGB(), (int) (255.0F * alpha)).getRGB();
        RoundedUtils.drawRound(x, y, width, height, 4.0F, new Color(20, 18, 18, (int) (90.0F * alpha)));
        if (player instanceof AbstractClientPlayer) {
            drawTargetFace(player, x + 3.0F, y + 3.0F, 31.0F, 4.0F, 1.0F, alpha);
            GlStateManager.disableBlend();
        } else {
            drawCentered(fallbackFont, "?", x + 20.0F,
                    y + 17.0F - fallbackFont.getFontHeight() / 2.0F, textColor, true);
        }
        titleFont.drawStringWithShadow(player.getName(), x + 39.0F, y + 5.0F, textColor);

        float healthPercent = getHealthPercent(player);
        float healthBarWidth = width - 44.0F;
        healthAnimation.update(healthPercent, System.currentTimeMillis());
        float healthWidth = healthBarWidth * healthAnimation.getHealth();
        float damageWidth = healthBarWidth * healthAnimation.getDamage();
        float barY = y + height - 12.0F;
        RoundedUtils.drawRound(x + 39.0F, barY, healthBarWidth, 3.0F, 1.5F,
                new Color(0, 0, 0, (int) (alpha * 110.0F)));
        if (damageWidth > 0.0F) {
            RoundedUtils.drawGradientHorizontal(x + 39.0F, barY, damageWidth, 3.0F, 1.5F,
                    withAlpha(colors[0].getRGB(), (int) (95.0F * alpha)),
                    withAlpha(colors[1].getRGB(), (int) (95.0F * alpha)));
        }
        if (healthWidth > 0.0F) {
            RoundedUtils.drawGradientHorizontal(x + 39.0F, barY, healthWidth, 3.0F, 1.5F, colors[0], colors[1]);
        }
    }

    private MiauFontRenderer targetHudFont(int sourceSize) {
        return FontManager.getHudRenderer(ArrayListModule.getSelectedFontName(), sourceSize / 20.0F);
    }

    private void drawCentered(MiauFontRenderer fontRenderer, String text, float centerX, float y, int color, boolean shadow) {
        fontRenderer.drawString(text, centerX - fontRenderer.getStringWidth(text) / 2.0F, y, color, shadow);
    }

    private void updateAnimatedTarget(int selectedMode, long now) {
        if (target == mc.thePlayer && !(mc.currentScreen instanceof GuiChat)) {
            target = null;
            renderEntity = null;
            hudAnimation.snapTo(false, now);
            fadeTimer = null;
            return;
        }

        EntityLivingBase nextTarget = findNextTarget(selectedMode);
        if (isNoAnimation()) {
            target = nextTarget;
            renderEntity = target;
            hudAnimation.snapTo(target != null, now);
            fadeTimer = null;
            return;
        }

        if (target == null) {
            if (nextTarget != null) {
                target = nextTarget;
                renderEntity = target;
                hudAnimation.setVisible(true, now);
            }
        } else if (target == nextTarget) {
            hudAnimation.setVisible(true, now);
        } else {
            hudAnimation.setVisible(false, now);
            if (hudAnimation.isFinishedHidden(now)) {
                target = null;
            }
        }

        if (target == null) {
            renderEntity = null;
        }
        fadeTimer = null;
    }

    private EntityLivingBase findNextTarget(int selectedMode) {
        if (mc.currentScreen instanceof GuiChat) {
            return mc.thePlayer;
        }
        if (mc.currentScreen != null) {
            return null;
        }

        EntityLivingBase candidate;
        if (selectedMode == MIAU_MODE || selectedMode == MYAU_MODE) {
            KillAura aura = (KillAura) ModuleManager.getModule(KillAura.class);
            candidate = aura != null && aura.isEnabled() ? KillAura.target : null;
        } else {
            candidate = KillAura.attackingEntity;
        }
        return candidate != null && !candidate.isDead && candidate.getHealth() > 0.0F
                ? candidate : null;
    }

    private boolean isNoAnimation() {
        return animation != null && (int) animation.getInput() == 0;
    }

    private boolean isScaleInAnimation() {
        return animation == null || (int) animation.getInput() == 2;
    }

    private float getAnimationProgress(long now) {
        return isNoAnimation() ? 1.0F : (float) hudAnimation.getOutput(now);
    }

    private void beginTargetAnimation(float progress) {
        GlStateManager.pushMatrix();
        if (isNoAnimation()) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc);
        float centerX = getScaleAnchorX(resolution);
        float centerY = getScaleAnchorY(resolution) + 20.0F;
        if (isScaleInAnimation()) {
            GlStateManager.translate(centerX, centerY, 0.0F);
            GlStateManager.scale(progress, progress, 1.0F);
            GlStateManager.translate(-centerX, -centerY, 0.0F);
        } else {
            GlStateManager.translate(
                    getMoveInOffset(centerX, resolution.getScaledWidth(), progress),
                    0.0F,
                    0.0F);
        }
    }

    private void endTargetAnimation() {
        GlStateManager.popMatrix();
    }

    static float getMoveInOffset(float centerX, int screenWidth, float progress) {
        float hiddenDistance = centerX <= screenWidth / 2.0F
                ? -screenWidth : screenWidth;
        return hiddenDistance * (1.0F - Math.max(0.0F, Math.min(1.0F, progress)));
    }

    private float[] getMiauOrigin(float width) {
        ScaledResolution resolution = new ScaledResolution(mc);
        return new float[]{resolution.getScaledWidth() / 2.0F - width / 2.0F + posX, resolution.getScaledHeight() / 2.0F + 15.0F + posY};
    }

    private void handleMiauDrag(float x, float y, float width, float height) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
            float mouseX = getLocalMouseX(resolution);
            float mouseY = getLocalMouseY(resolution);
        boolean hovering = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        if (hovering && !isDragging && Mouse.isButtonDown(0)) {
            isDragging = true;
            startedX = posX;
            startedY = posY;
            draggedX = getLocalMouseX(resolution);
            draggedY = getLocalMouseY(resolution);
        }
        if (isDragging) {
            if (!Mouse.isButtonDown(0)) {
                isDragging = false;
            } else {
                posX = (int) (startedX + getLocalMouseX(resolution) - draggedX);
                posY = (int) (startedY + getLocalMouseY(resolution) - draggedY);
            }
        }
    }

    private float getHudRenderScale() {
        return HudScale.toRenderScale(hudScale == null ? HudScale.USER_BASELINE : hudScale.getInput());
    }

    private float getScaleAnchorX(ScaledResolution resolution) {
        return resolution.getScaledWidth() / 2.0F + posX;
    }

    private float getScaleAnchorY(ScaledResolution resolution) {
        return resolution.getScaledHeight() / 2.0F + 15.0F + posY;
    }

    private float getLocalMouseX(ScaledResolution resolution) {
        float screenX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float anchorX = getScaleAnchorX(resolution);
        return anchorX + (screenX - anchorX) / getHudRenderScale();
    }

    private float getLocalMouseY(ScaledResolution resolution) {
        float screenY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
        float anchorY = getScaleAnchorY(resolution);
        return anchorY + (screenY - anchorY) / getHudRenderScale();
    }

    private void beginHudScale() {
        ScaledResolution resolution = new ScaledResolution(mc);
        float anchorX = getScaleAnchorX(resolution);
        float anchorY = getScaleAnchorY(resolution);
        float scale = getHudRenderScale();
        GlStateManager.pushMatrix();
        GlStateManager.translate(anchorX, anchorY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-anchorX, -anchorY, 0.0F);
    }

    private void endHudScale() {
        GlStateManager.popMatrix();
    }

    private void drawTargetFace(EntityLivingBase player, float x, float y, float size, float radius,
                                float brightness, float alpha) {
        if (player instanceof AbstractClientPlayer) {
            AbstractClientPlayer clientPlayer = (AbstractClientPlayer) player;
            boolean useStencil = radius > 0.0F
                    && !OpenGlHelper.isReducedRenderMode()
                    && OpenGlHelper.isFramebufferEnabled();
            if (useStencil) {
                beginStencilWrite();
                RoundedUtils.drawRound(x, y, size, size, Math.min(radius, size / 2.0F), Color.WHITE);
                beginStencilRead();
            }
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(clientPlayer.getLocationSkin());
            float safeBrightness = Math.max(0.0F, Math.min(1.0F, brightness));
            GlStateManager.color(1.0F, safeBrightness, safeBrightness,
                    Math.max(0.0F, Math.min(1.0F, alpha)));
            Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8.0F, 8.0F, 8, 8, (int) size, (int) size, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 40.0F, 8.0F, 8, 8, (int) size, (int) size, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            if (useStencil) {
                endStencil();
            }
        }
    }

    private void beginStencilWrite() {
        net.minecraft.client.shader.Framebuffer framebuffer = mc.getFramebuffer();
        framebuffer.bindFramebuffer(false);
        if (framebuffer.depthBuffer > -1) {
            EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
            int packedBuffer = EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, packedBuffer);
            EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT, mc.displayWidth, mc.displayHeight);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, packedBuffer);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, packedBuffer);
            framebuffer.depthBuffer = -1;
        }
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        GL11.glColorMask(false, false, false, false);
    }

    private void beginStencilRead() {
        GL11.glColorMask(true, true, true, true);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    private void endStencil() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private Color[] getTargetHudColors(int alpha) {
        int[] gradient = {
                ArrayListModule.getHudColor(0.0D),
                ArrayListModule.getHudColor(48.0D)
        };
        return new Color[]{withAlpha(gradient[0], alpha), withAlpha(gradient[1], alpha)};
    }

    private Color withAlpha(int color, int alpha) {
        return new Color(color >> 16 & 255, color >> 8 & 255, color & 255, Math.max(0, Math.min(255, alpha)));
    }

    private float getHealthPercent(EntityLivingBase player) {
        float absorption = Math.max(0.0F, player.getAbsorptionAmount());
        float maximum = Math.max(1.0F, player.getMaxHealth() + absorption);
        return Math.max(0.0F, Math.min(1.0F, (player.getHealth() + absorption) / maximum));
    }

    static final class RavenHealthAnimation {
        private static final float EPSILON = 0.0001F;
        private boolean initialized;
        private boolean modern;
        private float health;
        private float output;
        private float transitionFrom;
        private float transitionTo;
        private long transitionStartedAt;
        private long duration;

        void update(float value, long now, long duration, boolean modern) {
            float nextHealth = clamp(value);
            if (!initialized) {
                initialized = true;
                this.modern = modern;
                health = nextHealth;
                output = nextHealth;
                transitionFrom = nextHealth;
                transitionTo = nextHealth;
                transitionStartedAt = now;
                this.duration = Math.max(1L, duration);
                return;
            }

            output = calculate(now);
            if (Math.abs(nextHealth - health) > EPSILON || this.modern != modern) {
                transitionFrom = output;
                transitionTo = nextHealth;
                transitionStartedAt = now;
                this.duration = Math.max(1L, duration);
                this.modern = modern;
            }
            health = nextHealth;
            output = calculate(now);
        }

        float getOutput() {
            return output;
        }

        private float calculate(long now) {
            float progress = Math.max(0.0F, Math.min(1.0F,
                    (now - transitionStartedAt) / (float) duration));
            float eased = modern ? quadInOut(progress) : cubicInOut(progress);
            return transitionFrom + (transitionTo - transitionFrom) * eased;
        }

        private static float quadInOut(float progress) {
            return progress < 0.5F
                    ? 2.0F * progress * progress
                    : -1.0F + (4.0F - 2.0F * progress) * progress;
        }

        private static float cubicInOut(float progress) {
            return progress < 0.5F
                    ? 4.0F * progress * progress * progress
                    : (progress - 1.0F) * (2.0F * progress - 2.0F)
                    * (2.0F * progress - 2.0F) + 1.0F;
        }

        private static float clamp(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }

    static final class MyauHealthAnimation {
        private static final long DURATION = 150L;
        private static final float EPSILON = 0.0001F;
        private boolean initialized;
        private boolean transitioning;
        private float oldHealth;
        private float newHealth;
        private float maximumHealth = 1.0F;
        private long transitionStartedAt;

        float update(float health, float maximumHealth, boolean animated, long now) {
            float safeHealth = Math.max(0.0F, health);
            float safeMaximum = Math.max(0.5F, maximumHealth);
            if (!initialized || !animated) {
                initialized = true;
                transitioning = false;
                oldHealth = safeHealth;
                newHealth = safeHealth;
                this.maximumHealth = safeMaximum;
                transitionStartedAt = now;
                return ratio(safeHealth, safeMaximum);
            }

            if (!transitioning || now - transitionStartedAt >= DURATION) {
                oldHealth = newHealth;
                newHealth = safeHealth;
                this.maximumHealth = safeMaximum;
                transitioning = Math.abs(oldHealth - newHealth) > EPSILON;
                if (transitioning) {
                    transitionStartedAt = now;
                }
            }

            float progress = transitioning
                    ? Math.max(0.0F, Math.min(1.0F,
                    (now - transitionStartedAt) / (float) DURATION))
                    : 1.0F;
            float renderedHealth = oldHealth + (newHealth - oldHealth) * progress;
            return ratio(renderedHealth, this.maximumHealth);
        }

        private static float ratio(float health, float maximumHealth) {
            return Math.max(0.0F, Math.min(1.0F, health / maximumHealth));
        }
    }

    static final class DamageHealthAnimation {
        private static final float EPSILON = 0.0001F;
        private final long delay;
        private final long duration;
        private boolean initialized;
        private float health;
        private float damage;
        private float transitionFrom;
        private float transitionTo;
        private long transitionStartedAt;

        DamageHealthAnimation() {
            this(100L, 550L);
        }

        DamageHealthAnimation(long delay, long duration) {
            this.delay = Math.max(0L, delay);
            this.duration = Math.max(1L, duration);
        }

        void update(float value, long now) {
            float nextHealth = clamp(value);
            if (!initialized) {
                initialized = true;
                health = nextHealth;
                damage = nextHealth;
                transitionFrom = nextHealth;
                transitionTo = nextHealth;
                transitionStartedAt = now;
                return;
            }

            float animatedDamage = calculateDamage(now);
            if (nextHealth < health - EPSILON) {
                damage = Math.max(animatedDamage, health);
                startTransition(damage, nextHealth, now);
            } else if (nextHealth > health + EPSILON) {
                if (nextHealth >= animatedDamage) {
                    damage = nextHealth;
                    startTransition(nextHealth, nextHealth, now);
                } else {
                    damage = animatedDamage;
                    startTransition(animatedDamage, nextHealth, now - delay);
                }
            } else {
                damage = animatedDamage;
            }
            health = nextHealth;
            damage = Math.max(health, damage);
        }

        float getHealth() {
            return health;
        }

        float getDamage() {
            return damage;
        }

        void snap(float value) {
            float nextHealth = clamp(value);
            initialized = true;
            health = nextHealth;
            damage = nextHealth;
            transitionFrom = nextHealth;
            transitionTo = nextHealth;
            transitionStartedAt = 0L;
        }

        private void startTransition(float from, float to, long now) {
            transitionFrom = from;
            transitionTo = to;
            transitionStartedAt = now;
        }

        private float calculateDamage(long now) {
            long elapsed = now - transitionStartedAt - delay;
            if (elapsed <= 0L) {
                return transitionFrom;
            }
            float progress = Math.min(1.0F, elapsed / (float) duration);
            float eased = progress * progress * (3.0F - 2.0F * progress);
            return transitionFrom + (transitionTo - transitionFrom) * eased;
        }

        private static float clamp(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }

    private void reset() {
        fadeTimer = null;
        target = null;
        renderEntity = null;
        healthAnimations.clear();
        ravenHealthAnimations.clear();
        myauHealthAnimations.clear();
        myauHeadTextures.clear();
        hudAnimation.snapTo(false, System.currentTimeMillis());
    }
}
