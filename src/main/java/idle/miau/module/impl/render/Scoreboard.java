package idle.miau.module.impl.render;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Scoreboard extends Module {
    public static final String[] STYLES = {"Modern Rounded", "Clean (No BG)", "Vanilla"};
    public static SliderSetting style;
    public static SliderSetting cornerRadius;
    public static SliderSetting backgroundOpacity;
    public static ButtonSetting blur;
    public static ButtonSetting hideRedNumbers;
    public static ButtonSetting customPosition;
    public static SliderSetting offsetX;
    public static SliderSetting offsetY;
    public static ButtonSetting animation;

    private float animatedWidth = 0.0F;
    private float animatedHeight = 0.0F;
    private long lastRenderTime = 0L;

    private boolean isDragging = false;
    private double startedOffsetX = 0;
    private double startedOffsetY = 0;
    private double draggedX = 0;
    private double draggedY = 0;

    public Scoreboard() {
        super("Scoreboard", category.render);
        setDescription("Customizes the sidebar scoreboard style.");
        this.canBeDisabled = false;
        this.setEnabled(true);
        GroupSetting appearance = new GroupSetting("Appearance");
        registerSetting(appearance);
        add(appearance, style = new SliderSetting("Style", 2, STYLES));
        add(appearance, cornerRadius = new SliderSetting("Corner Radius", "px", 5, 1, 10, 0.5));
        add(appearance, backgroundOpacity = new SliderSetting("BG Opacity", 0.4, 0, 1, 0.05));
        add(appearance, hideRedNumbers = new ButtonSetting("Hide Red Numbers", false));
        add(appearance, animation = new ButtonSetting("Animation", false));
        GroupSetting shaders = new GroupSetting("Shaders");
        registerSetting(shaders);
        add(shaders, blur = new ButtonSetting("Blur", false));
        GroupSetting position = new GroupSetting("Position");
        registerSetting(position);
        add(position, customPosition = new ButtonSetting("Custom Position", false));
        add(position, offsetX = new SliderSetting("Offset X", 0, -1000, 1000, 1));
        add(position, offsetY = new SliderSetting("Offset Y", 0, -1000, 1000, 1));
        guiUpdate();
    }

    private void add(GroupSetting group, Setting setting) {
        ModernHudSettings.setGroup(setting, group);
        registerSetting(setting);
    }

    @Override
    public void guiUpdate() {
        cornerRadius.setVisible(style.getInput() == 0, this);
        backgroundOpacity.setVisible(style.getInput() != 1, this);
        offsetX.setVisible(customPosition.isToggled(), this);
        offsetY.setVisible(customPosition.isToggled(), this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        super.guiButtonToggled(setting);
        guiUpdate();
    }

    public static boolean isActive() {
        return ModuleManager.scoreboard != null && ModuleManager.scoreboard.isEnabled();
    }

    public static boolean isBlurActive() {
        return isActive() && blur != null && blur.isToggled();
    }

    public static float getBlurStrength() {
        return HudStyle.blurRadius();
    }

    public static boolean isBloomActive() {
        return isBlurActive();
    }

    public static float getBloomStrength() {
        return HudStyle.shaderRadius();
    }

    public static HudStyle.PanelStyle panelStyle() {
        int selectedStyle = style == null ? 2 : (int) style.getInput();
        float radius = selectedStyle == 0 ? (cornerRadius == null ? 5 : (float) cornerRadius.getInput()) * HudStyle.scale() : 0;
        int background = selectedStyle == 1 ? 0 : Math.round((backgroundOpacity == null ? 0.4f
                : (float) backgroundOpacity.getInput()) * 255) << 24;
        return HudStyle.PanelStyle.shared(radius, background, isBlurActive(), isBloomActive());
    }

    private static class ScoreEntry {
        final String name;
        final String points;

        ScoreEntry(String name, String points) {
            this.name = name;
            this.points = points;
        }
    }

    List<ScoreEntry> getDummyEntries() {
        List<ScoreEntry> list = new ArrayList<ScoreEntry>();
        list.add(new ScoreEntry(EnumChatFormatting.GRAY + "getmiau.today", "1"));
        list.add(new ScoreEntry(EnumChatFormatting.YELLOW + "Drag to reposition", "2"));
        list.add(new ScoreEntry("Status: " + EnumChatFormatting.GREEN + "Active", "3"));
        list.add(new ScoreEntry(EnumChatFormatting.GOLD + "Mimeow Client", "4"));
        return list;
    }

    private void handleDrag(ScaledResolution resolution, float left, float top, float right, float bottom) {
        if (mc == null || !(mc.currentScreen instanceof GuiChat)) {
            isDragging = false;
            return;
        }

        Gui.drawRect((int) left, (int) top, (int) right, (int) bottom, 0x40FFFFFF);

        try {
            if (!Mouse.isCreated()) {
                return;
            }

            int mouseX = Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
            int mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;

            boolean hovering = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
            double scaleFactor = resolution.getScaleFactor() / Math.pow(resolution.getScaleFactor(), 2.0D);
            double currentMouseX = Mouse.getX() * scaleFactor;
            double currentMouseY = (mc.displayHeight - Mouse.getY()) * scaleFactor;

            if (hovering && !isDragging && Mouse.isButtonDown(0)) {
                isDragging = true;
                startedOffsetX = offsetX != null ? offsetX.getInput() : 0.0;
                startedOffsetY = offsetY != null ? offsetY.getInput() : 0.0;
                draggedX = currentMouseX;
                draggedY = currentMouseY;
            }

            if (isDragging) {
                if (!Mouse.isButtonDown(0)) {
                    isDragging = false;
                } else {
                    double deltaX = currentMouseX - draggedX;
                    double deltaY = currentMouseY - draggedY;
                    if (customPosition != null) {
                        customPosition.setEnabled(true);
                    }
                    if (offsetX != null) {
                        offsetX.setValue(startedOffsetX + deltaX);
                    }
                    if (offsetY != null) {
                        offsetY.setValue(startedOffsetY + deltaY);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void renderScoreboard(ScoreObjective objective, ScaledResolution scaledRes, FontRenderer fontRenderer) {
        if (scaledRes == null || fontRenderer == null) {
            return;
        }

        if (objective == null) {
            if (mc != null && mc.currentScreen instanceof GuiChat) {
                renderEntries("Scoreboard", getDummyEntries(), scaledRes, fontRenderer);
            }
            return;
        }

        net.minecraft.scoreboard.Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) {
            if (mc != null && mc.currentScreen instanceof GuiChat) {
                renderEntries(objective.getDisplayName(), getDummyEntries(), scaledRes, fontRenderer);
            }
            return;
        }

        Collection<Score> collection = scoreboard.getSortedScores(objective);
        List<Score> list = Lists.newArrayList(Iterables.filter(collection, new Predicate<Score>() {
            public boolean apply(Score p_apply_1_) {
                return p_apply_1_ != null && p_apply_1_.getPlayerName() != null && !p_apply_1_.getPlayerName().startsWith("#");
            }
        }));

        if (list.size() > 15) {
            collection = Lists.newArrayList(Iterables.skip(list, list.size() - 15));
        } else {
            collection = list;
        }

        List<ScoreEntry> entries = new ArrayList<ScoreEntry>();
        for (Score score : collection) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String formattedName = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            entries.add(new ScoreEntry(formattedName, String.valueOf(score.getScorePoints())));
        }

        if (entries.isEmpty() && mc != null && mc.currentScreen instanceof GuiChat) {
            entries = getDummyEntries();
        }

        renderEntries(objective.getDisplayName(), entries, scaledRes, fontRenderer);
    }

    private void renderEntries(String title, List<ScoreEntry> entries, ScaledResolution resolution, FontRenderer renderer) {
        MiauFontRenderer font = HudStyle.minecraftFont(renderer);
        boolean hideNumbers = hideRedNumbers.isToggled();
        float paddingX = Math.max(3 * HudStyle.scale(), HudStyle.paddingX());
        float paddingY = Math.max(HudStyle.scale(), HudStyle.paddingY());
        float lineHeight = font.getFontHeight() + paddingY * 2;
        int maximumWidth = font.getStringWidth(title);
        for (ScoreEntry entry : entries) {
            maximumWidth = Math.max(maximumWidth, font.getStringWidth(entry.name)
                    + (hideNumbers ? 0 : Math.round(6 * HudStyle.scale()) + font.getStringWidth(entry.points)));
        }
        float targetWidth = maximumWidth + paddingX * 2;
        float targetHeight = (entries.size() + 1) * lineHeight;
        long now = System.currentTimeMillis();
        float delta = lastRenderTime == 0 ? 0.05f : Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        if (animation.isToggled() && animatedWidth > 0) {
            float factor = 1 - (float) Math.exp(-14 * delta);
            animatedWidth += (targetWidth - animatedWidth) * factor;
            animatedHeight += (targetHeight - animatedHeight) * factor;
        } else {
            animatedWidth = targetWidth;
            animatedHeight = targetHeight;
        }
        float width = animatedWidth;
        float height = animatedHeight;
        float left = resolution.getScaledWidth() - width - 3;
        float top = (resolution.getScaledHeight() - height) / 2;
        if (customPosition.isToggled()) {
            left += (float) offsetX.getInput();
            top += (float) offsetY.getInput();
        }
        handleDrag(resolution, left, top, left + width, top + height);
        float x = left;
        float y = top;
        HudStyle.panel(new HudStyle.Panel(x, y, width, height, () -> {
            RenderUtils.scissorPushGui(x, y, width, height);
            try {
                font.drawString(title, x + (width - font.getStringWidth(title)) / 2,
                        y + paddingY, 0xFFFFFFFF, false);
                for (int index = entries.size() - 1; index >= 0; index--) {
                    ScoreEntry entry = entries.get(index);
                    float rowY = y + (entries.size() - index) * lineHeight + paddingY;
                    font.drawString(entry.name, x + paddingX, rowY, 0xFFFFFFFF, false);
                    if (!hideNumbers) font.drawString(EnumChatFormatting.RED + entry.points,
                            x + width - paddingX - font.getStringWidth(entry.points), rowY, 0xFFFFFFFF, false);
                }
            } finally {
                RenderUtils.scissorPop();
            }
        }), panelStyle());
    }
}
