package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

import idle.miau.module.impl.combat.Velocity;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.MiauThemes;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.render.HudScale;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.StringUtils;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.game.TickEvent.RenderTickEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class ArrayListModule extends Module {
    private static final String[] RENDER_MODES = new String[] { "Static", "Gradient", "Rainbow", "Breathe" };
    public static SliderSetting hudMode;
    private static ModernHudSettings modernSettings;
    private final ModernArrayListRenderer modernRenderer;
    private final CompactArrayListRenderer compactRenderer;
    private boolean compactActive;
    private static final String[] ANIMATION_MODES =
            new String[] { "None", "Move in", "Scale in" };
    private static final double FADE_TIME_SCALE = 600.0;
    private static final double FADE_Y_SCALE = 0.06;

    public static SliderSetting colorMode;
    public static SliderSetting hudTheme;
    public static ColorSetting hudColor;
    public static ColorSetting hudColor2;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    private static SliderSetting animation;
    public final ButtonSetting showArrayList = new ButtonSetting("ArrayList", true);
    public final ButtonSetting showPotionHud = new ButtonSetting("Potion HUD", true);
    private final PotionHUD potionComponent = new PotionHUD();
    private final GroupSetting potionSettings = new GroupSetting("Potion settings");
    private static ButtonSetting textShadow;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    public static ButtonSetting showInfo;
    private static final float DEFAULT_POS_X = 5.0f;
    private static final float DEFAULT_POS_Y = 70.0f;
    public static float posX = DEFAULT_POS_X;
    public static float posY = DEFAULT_POS_Y;
    private static float relativePosX = Float.NaN;
    private static float relativePosY = Float.NaN;
    public static boolean isDragging = false;
    public static float startedX = 0.0f;
    public static float startedY = 0.0f;
    public static double draggedX = 0.0;
    public static double draggedY = 0.0;

    private static final String DEFAULT_HUD_FONT = "ProductSans";
    private static final String[] HUD_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final MiauThemes[] HUD_COLOR_THEMES = buildColorThemes();
    private static final String[] HUD_THEMES = buildThemeOptions();

    private boolean canShowInfo;
    private String lastHudFontName = "";
    private float lastHudFontScale = -1.0f;
    private final Map<Module, AnimatedModuleState> animationStates =
            new IdentityHashMap<Module, AnimatedModuleState>();

    public ArrayListModule() {
        super("HUD", Module.category.render);
        setDescription("Customize the module list and appearance of client HUDs.");
        this.registerSetting(hudMode = new SliderSetting("Mode", 1, new String[]{"Legacy", "Modern", "Slinky"}));
        this.registerSetting(showArrayList);
        colorMode = new SliderSetting("Render mode", 0, RENDER_MODES, "Color mode");
        hudTheme = new SliderSetting("Color", 0, HUD_THEMES, "Theme");
        hudColor = new ColorSetting("Primary color", 255, 255, 255, "Color");
        hudColor2 = new ColorSetting("Secondary color", 85, 85, 255, "Color 2");
        font = new SliderSetting("Font", Arrays.asList(HUD_FONT_OPTIONS).indexOf(DEFAULT_HUD_FONT), HUD_FONT_OPTIONS);
        fontSize = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.01);
        animation = new SliderSetting("Animation", 2, ANIMATION_MODES);
        alignRight = new ButtonSetting("Align right", false);
        textShadow = new ButtonSetting("Text shadow", true);
        lowercase = new ButtonSetting("Lowercase", false);
        showInfo = new ButtonSetting("Show module info", true);
        modernSettings = new ModernHudSettings();
        modernRenderer = new ModernArrayListRenderer(modernSettings);
        for (Map.Entry<String, Setting> entry : modernSettings.settings.entrySet()) {
            Setting setting = entry.getValue();
            registerSetting(setting, "setting.modern." + entry.getKey());
            if (setting == modernSettings.colorsGroup) {
                registerGroupedSetting(colorMode, modernSettings.colorsGroup);
                registerGroupedSetting(hudTheme, modernSettings.colorsGroup);
                registerGroupedSetting(hudColor, modernSettings.colorsGroup);
                registerGroupedSetting(hudColor2, modernSettings.colorsGroup);
            } else if (setting == modernSettings.layoutGroup) {
                registerGroupedSetting(alignRight, modernSettings.layoutGroup);
            } else if (setting == modernSettings.animationGroup) {
                registerGroupedSetting(animation, modernSettings.animationGroup);
            } else if (setting == modernSettings.textGroup) {
                registerGroupedSetting(font, modernSettings.textGroup);
                registerGroupedSetting(fontSize, modernSettings.textGroup);
                registerGroupedSetting(lowercase, modernSettings.textGroup);
                registerGroupedSetting(textShadow, modernSettings.textGroup);
            } else if (setting == modernSettings.suffixGroup) {
                registerGroupedSetting(showInfo, modernSettings.suffixGroup);

            }
        }
        registerSetting(potionSettings);
        registerGroupedSetting(showPotionHud, potionSettings);
        for (Setting setting : potionComponent.getSettings()) {
            ModernHudSettings.setGroup(setting, potionSettings);
            registerEmbeddedSetting(setting);
        }
        compactRenderer = new CompactArrayListRenderer(this);
        guiUpdate();
    }

    void registerOverlaySetting(Setting setting, String key) {
        registerSetting(setting, "setting.overlay." + key);
    }

    private void registerGroupedSetting(Setting setting, GroupSetting group) {
        ModernHudSettings.setGroup(setting, group);
        registerSetting(setting);
    }

    private void registerEmbeddedSetting(Setting setting) {
        this.registerSetting(setting, getEmbeddedSettingConfigId(setting.getName()));
    }

    private String getEmbeddedSettingConfigId(String name) {
        switch (name) {
            case "Potion Lower case":
                return "setting.045";
            case "Potion Icons": return "setting.potion.icons";
            case "Potion Levels": return "setting.potion.levels";
            case "Potion Durations": return "setting.potion.durations";
            case "Potion Hide long effects": return "setting.potion.hideLongEffects";
            default:
                throw new IllegalArgumentException("Unknown embedded HUD setting: " + name);
        }
    }

    @Override
    public void guiUpdate() {
        boolean modern = isModern();
        for (Setting setting : new Setting[]{font, fontSize, animation, textShadow, lowercase, showInfo}) {
            if (setting != null) setting.setVisible(!isCompactHud(), this);
        }
        boolean customColor = hudTheme == null || (int) hudTheme.getInput() == 0;
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (colorMode != null) colorMode.setVisible(true, this);
        if (hudTheme != null) hudTheme.setVisible(mode != 2, this);
        if (hudColor != null) hudColor.setVisible(customColor && mode != 2, this);
        if (hudColor2 != null) hudColor2.setVisible(customColor && mode == 1, this);

        if (alignRight != null) alignRight.setVisible(!modern, this);

        for (Setting setting : potionComponent.getSettings()) {
            setting.setVisible(showPotionHud.isToggled(), this);
        }
        if (modernSettings != null) {
            modernSettings.updateVisibility(this, modern, mode, showInfo != null && showInfo.isToggled(), !isNoAnimation());
        }
        if (compactRenderer != null) {
            if (isCompactHud()) {
                for (Setting setting : getSettings()) {
                    if (setting != hudMode && setting != showArrayList && setting != potionSettings
                            && setting != showPotionHud && !potionComponent.getSettings().contains(setting)) {
                        setting.setVisible(false, this);
                    }
                }
            }
            compactRenderer.updateVisibility(isCompactHud());
        }
    }

    @Subscribe
    public void onModuleStateChange(idle.miau.event.ModuleStateChangeEvent event) {
        if (isCompactHud()) {
            compactRenderer.control(event.getModule(), event.isEnabled());
        }
    }

    @Subscribe
    public void onWorldUnload(idle.miau.mcp.event.world.WorldEvent.Unload event) {
        compactRenderer.reset();
    }

    public static boolean isCompactHud() {
        return hudMode != null && hudMode.getInput() == 2;
    }

    @Override
    public void onEnable() {
        animationStates.clear();
        modernRenderer.resetAnimation();
        compactRenderer.reset();
        compactActive = false;
        guiUpdate();
        ModuleManager.synchronizeOrganizedModules();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == modernSettings.blur) {
            modernSettings.blurPreset.setValue(0);
        }
        if (buttonSetting == showInfo) {
            ModuleManager.sort();
        }
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == modernSettings.blurPreset) {
            modernSettings.applyBlurPreset();
        } else if (setting == modernSettings.blurRadius || setting == modernSettings.blurPasses) {
            modernSettings.blurPreset.setValue(0);
        }
        Module.sort = true;
        guiUpdate();
    }

    @Subscribe
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }

        String currentFontName = getSelectedFontName();
        float currentFontScale = getSelectedFontScale();
        if (!currentFontName.equals(lastHudFontName) || Float.compare(currentFontScale, lastHudFontScale) != 0) {
            lastHudFontName = currentFontName;
            lastHudFontScale = currentFontScale;
            ModuleManager.sort();
        }

        if ((mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) || mc.gameSettings.showDebugInfo) {
            return;
        }
        if (compactActive != isCompactHud()) {
            compactActive = isCompactHud();
            compactRenderer.reset();
            if (compactActive) compactRenderer.control(this, true);
        }
        if (showPotionHud.isToggled()) potionComponent.renderFromHud();
        if (!showArrayList.isToggled()) return;

        if (isCompactHud()) {
            compactRenderer.render();
            return;
        }

        mc.fontRendererObj.withSmoothFont(() -> {
            renderArrayList();
            return 0;
        });
    }

    private void renderArrayList() {
        syncPositionToResolution();

        Module[] organizedSnapshot;
        synchronized (ModuleManager.organizedModules) {
            organizedSnapshot = ModuleManager.organizedModules.toArray(new Module[0]);
        }

        for (Module module : organizedSnapshot) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }

        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;

        MiauFontRenderer hudFont = getHudFontRenderer();
        int textTopOffset = hudFont.getTextTopOffset();
        int textBottomOffset = hudFont.getTextBottomOffset();
        int horizontalTextPadding = getHudHorizontalTextPadding();
        int textTopPadding = getHudTextTopPadding();
        int textBottomPadding = getHudTextBottomPadding();
        int outlineThickness = getHudOutlineThickness();
        float rowHeight = getHudRowHeight(hudFont, (textTopPadding + textBottomPadding) / 2.0f);
        List<AnimatedRow> animatedRows = getAnimatedRows(hudFont, System.currentTimeMillis());

        if (isModern()) {
            modernRenderer.render(animatedRows, hudFont, isScaleInAnimation(),
                    !isNoAnimation() && !isScaleInAnimation());
            return;
        }

        if (mc.currentScreen instanceof net.minecraft.client.gui.GuiChat) {
            ScaledResolution resolution = new ScaledResolution(mc);
            int mouseX = org.lwjgl.input.Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
            int mouseY = resolution.getScaledHeight() - org.lwjgl.input.Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;

            float dragWidth = 0;
            float animatedHeight = 0.0F;
            for (AnimatedRow row : animatedRows) {
                dragWidth = Math.max(dragWidth, row.width);
                animatedHeight += row.progress * rowHeight;
            }
            if (animatedRows.isEmpty()) {
                dragWidth = hudFont.getStringWidth("ArrayList");
                animatedHeight = rowHeight;
            }

            float width = dragWidth + horizontalTextPadding * 2.0F + outlineThickness * 2.0F;
            float height = animatedHeight + outlineThickness * 2.0F;
            float left = alignRight.isToggled()
                    ? posX - dragWidth - horizontalTextPadding - outlineThickness
                    : posX - horizontalTextPadding - outlineThickness;
            float right = left + width;
            float top = posY - outlineThickness;
            float bottom = top + height;

            boolean hovering = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
            double scaleFactor = resolution.getScaleFactor() / Math.pow(resolution.getScaleFactor(), 2.0D);
            if (hovering && !isDragging && org.lwjgl.input.Mouse.isButtonDown(0)) {
                isDragging = true;
                startedX = posX;
                startedY = posY;
                draggedX = org.lwjgl.input.Mouse.getX() * scaleFactor;
                draggedY = (mc.displayHeight - org.lwjgl.input.Mouse.getY()) * scaleFactor;
            }
            if (isDragging) {
                if (!org.lwjgl.input.Mouse.isButtonDown(0)) {
                    isDragging = false;
                } else {
                    double mx = org.lwjgl.input.Mouse.getX() * scaleFactor;
                    double my = (mc.displayHeight - org.lwjgl.input.Mouse.getY()) * scaleFactor;

                    setAbsolutePosition((float) (startedX + (mx - draggedX)), (float) (startedY + (my - draggedY)), resolution);
                }
            }
        }

        float yPos = posY;
        ScaledResolution resolution = new ScaledResolution(mc);
        boolean scaleIn = isScaleInAnimation();
        boolean moveIn = !isNoAnimation() && !scaleIn;
        MiauFontRenderer styledFont = HudStyle.themedFont();
        List<HudStyle.Panel> panels = new ArrayList<>();
        for (AnimatedRow row : animatedRows) {
            float progress = row.progress;
            float xPos = posX - (alignRight.isToggled() ? row.width : 0);
            if (moveIn) xPos += getMoveInOffset(alignRight.isToggled(), progress,
                    xPos, xPos + row.width, resolution.getScaledWidth());
            float textX = xPos;
            float textY = yPos + (rowHeight - textTopOffset - textBottomOffset) / 2 + textTopOffset;
            float pivotX = xPos + row.width / 2f;
            float pivotY = yPos + rowHeight / 2f;
            float left = xPos - horizontalTextPadding;
            float top = yPos;
            float width = row.width + horizontalTextPadding * 2;
            float height = rowHeight;
            if (scaleIn) {
                left = pivotX + (left - pivotX) * progress;
                top = pivotY + (top - pivotY) * progress;
                width *= progress;
                height *= progress;
            }
            panels.add(new HudStyle.Panel(left, top, width, height, progress, () -> {
                if (scaleIn) startScaleAnimation(pivotX, pivotY, progress);
                try {
                    styledFont.drawString(row.text, textX, textY, 0xFFFFFFFF, false);
                } finally {
                    if (scaleIn) GlStateManager.popMatrix();
                }
            }, null));
            yPos += progress * rowHeight;
        }
        HudStyle.arrayListPanels(panels);
    }

    private List<AnimatedRow> getAnimatedRows(MiauFontRenderer hudFont, long now) {
        List<AnimatedRow> rows = new ArrayList<AnimatedRow>();
        if (isNoAnimation()) {
            animationStates.clear();
            List<Module> organized;
            synchronized (ModuleManager.organizedModules) {
                organized = new ArrayList<Module>(ModuleManager.organizedModules);
            }
            for (Module module : organized) {
                if (module != null && module.isEnabled() && module != this
                        && !shouldSkipModule(module, false)) {
                    rows.add(createAnimatedRow(module, hudFont, 1.0F));
                }
            }
            sortRows(rows);
            return rows;
        }

        List<Module> candidates = new ArrayList<Module>(ModuleManager.modules);
        synchronized (ModuleManager.organizedModules) {
            for (Module module : ModuleManager.organizedModules) {
                if (!candidates.contains(module)) {
                    candidates.add(module);
                }
            }
        }
        for (Module module : new ArrayList<Module>(animationStates.keySet())) {
            if (!candidates.contains(module)) {
                candidates.add(module);
            }
        }

        for (Module module : candidates) {
            if (module == null || module == this || shouldSkipModule(module, false)) {
                animationStates.remove(module);
                continue;
            }

            AnimatedModuleState state = animationStates.get(module);
            if (state == null) {
                if (!module.isEnabled()) {
                    continue;
                }
                state = new AnimatedModuleState(now);
                animationStates.put(module, state);
            }

            state.animation.setDuration(isModern() ? (long) modernSettings.animationDuration.getInput()
                    : MiauHudAnimation.DURATION_MS, now);
            state.animation.setVisible(module.isEnabled(), now);
            if (!module.isEnabled() && state.animation.isFinishedHidden(now)) {
                animationStates.remove(module);
                continue;
            }

            rows.add(createAnimatedRow(module, hudFont, (float) state.animation.getOutput(now)));
        }

        sortRows(rows);
        return rows;
    }

    private static AnimatedRow createAnimatedRow(Module module, MiauFontRenderer font, float progress) {
        String text = getHudRenderText(module);
        String name = getHudText(module);
        String suffix = text.substring(name.length());
        int nameWidth = font.getStringWidth(name);
        float suffixOffset = nameWidth;
        float width = font.getStringWidth(text);
        if (isModern()) {
            if (modernSettings.suffixStyle.getInput() != 6) suffix = suffix.stripLeading();
            if (!suffix.isEmpty()) {
                suffixOffset += modernSettings.suffixGap.getInput() * getSelectedFontScale() / HudScale.LEGACY_VISUAL_BASELINE;
            }
            width = suffixOffset + font.getStringWidth(suffix);
        }
        return new AnimatedRow(module, text, width, progress, name, suffix, nameWidth, suffixOffset);
    }

    private static void sortRows(List<AnimatedRow> rows) {
        if (isModern() && modernSettings.sorting.getInput() == 1) {
            rows.sort(Comparator.comparing(row -> row.text.toLowerCase(Locale.ROOT)));
        } else {
            Comparator<AnimatedRow> comparator = Comparator.comparingDouble((AnimatedRow row) -> row.width).reversed();
            if (isModern()) {
                comparator = comparator.thenComparing(Comparator.comparingInt((AnimatedRow row) -> row.nameWidth).reversed());
            }
            rows.sort(comparator);
        }
        if (isModern() && modernSettings.reverseSort.isToggled()) {
            java.util.Collections.reverse(rows);
        }
    }

    public static boolean isModern() {
        return hudMode != null && hudMode.getInput() == 1 && modernSettings != null;
    }

    private static boolean isScaleInAnimation() {
        return animation == null || (int) animation.getInput() == 2;
    }

    private static boolean isNoAnimation() {
        return animation != null && (int) animation.getInput() == 0;
    }

    static float getMoveInOffset(
            boolean alignRight,
            float progress,
            double textLeft,
            double textRight,
            int screenWidth) {
        float hiddenDistance = alignRight
                ? (float) (screenWidth - textLeft)
                : (float) -textRight;
        return hiddenDistance * (1.0F - clampAnimationProgress(progress));
    }

    private static void startScaleAnimation(float centerX, float centerY, float progress) {
        float scale = clampAnimationProgress(progress);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);
    }

    static int applyAnimationAlpha(int color, float progress, boolean scaleIn) {
        if (!scaleIn) {
            return color;
        }
        int alpha = color >>> 24;
        return Utils.mergeAlpha(
                color,
                Math.round(alpha * clampAnimationProgress(progress)));
    }

    private static float clampAnimationProgress(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private static final class AnimatedModuleState {
        private final MiauHudAnimation animation;

        private AnimatedModuleState(long now) {
            this.animation = new MiauHudAnimation(false, now);
        }
    }

    static final class AnimatedRow {
        final Module module;
        final String text;
        final float width;
        final float progress;
        final String name;
        final String suffix;
        final int nameWidth;
        final float suffixOffset;

        AnimatedRow(Module module, String text, float width, float progress, String name, String suffix,
                    int nameWidth, float suffixOffset) {
            this.module = module;
            this.text = text;
            this.width = width;
            this.progress = progress;
            this.name = name;
            this.suffix = suffix;
            this.nameWidth = nameWidth;
            this.suffixOffset = suffixOffset;
        }
    }

    public static int getLongestModule() {
        MiauFontRenderer hudFont = getHudFontRenderer();
        int length = 0;

        Module[] organizedSnapshot;
        synchronized (ModuleManager.organizedModules) {
            organizedSnapshot = ModuleManager.organizedModules.toArray(new Module[0]);
        }
        for (Module module : organizedSnapshot) {
            if (module.isEnabled()) {
                length = Math.max(length, hudFont.getStringWidth(getHudRenderText(module)));
            }
        }

        return length;
    }

    private static boolean shouldSkipModule(Module module, boolean removeVelocity) {
        if (module.isHidden()) {
            return true;
        }
        if (isModern() && (!module.canBeDisabled() || module instanceof idle.miau.module.impl.client.ClickGui)) {
            return true;
        }
        if (isModern() && modernSettings.hidesCategory(module.moduleCategory())) {
            return true;
        }
        return module instanceof Velocity && removeVelocity;
    }

    public static MiauFontRenderer getHudFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    public static String getHudText(Module module) {
        String moduleName = module.getNameInHud();
        if (isModern()) {
            moduleName = StringUtils.stripControlCodes(moduleName);
            if (modernSettings.removeSpaces.isToggled()) {
                moduleName = moduleName.replace(" ", "");
            }
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    public static String getHudRenderText(Module module) {
        String moduleName = getHudText(module);
        String info = CustomSuffix.customizeHudInfo(module, module.getHudInfo());
        if (showInfo != null && showInfo.isToggled() && info != null && !info.isEmpty()) {
            moduleName += isModern() ? modernSettings.formatSuffix(StringUtils.stripControlCodes(info))
                    : " \u00a77" + info;
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    static ModernHudSettings getAppearanceSettings() {
        return modernSettings;
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return DEFAULT_HUD_FONT;
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontScale() {
        if (fontSize == null) {
            return HudScale.toRenderScale(HudScale.USER_BASELINE);
        }
        return HudScale.toRenderScale(fontSize.getInput());
    }

    public static float getRelativePosX() {
        syncPositionToResolution();
        return relativePosX;
    }

    public static float getRelativePosY() {
        syncPositionToResolution();
        return relativePosY;
    }

    public float getModernRelativePosX() {
        return modernRenderer.getRelativeX();
    }

    public float getModernRelativePosY() {
        return modernRenderer.getRelativeY();
    }

    public void setModernRelativePosition(float x, float y) {
        modernRenderer.setRelativePosition(x, y);
    }

    public double getPotionPositionX() {
        return potionComponent.getPositionX();
    }

    public double getPotionPositionY() {
        return potionComponent.getPositionY();
    }

    public void setPotionPosition(double x, double y) {
        potionComponent.setPosition(x, y);
    }

    public static void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public static void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public static void resetPosition() {
        resetPosition(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            relativePosX = posX / scaledWidth;
            relativePosY = posY / scaledHeight;
        }

        posX = relativePosX * scaledWidth;
        posY = relativePosY * scaledHeight;
    }

    private static void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;

        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / scaledWidth;
        relativePosY = absoluteY / scaledHeight;
    }

    private static void resetPosition(ScaledResolution resolution) {
        setAbsolutePosition(DEFAULT_POS_X, DEFAULT_POS_Y, resolution);
    }

    private static int getHudHorizontalTextPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextTopPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextBottomPadding() {
        return 0;
    }

    private static int getHudOutlineThickness() {
        return getScaledHudPixels(1.0f);
    }

    private static int getScaledHudPixels(float pixels) {
        return Math.round(pixels * getSelectedFontScale());
    }

    static float getHudRowHeight(MiauFontRenderer font, float paddingY) {
        float lineHeight = font.getLineHeightExact();
        if (FontManager.isMinecraftFont(getSelectedFontName())) {
            lineHeight += 2.0f * getSelectedFontScale() / (float) HudScale.LEGACY_VISUAL_BASELINE;
        }
        float contentHeight = font.getTextTopOffset() + font.getTextBottomOffset();
        return Math.max(lineHeight, contentHeight + paddingY * 2);
    }

    public static boolean isHudTextShadowEnabled() {
        return textShadow == null || textShadow.isToggled();
    }

    public static int getHudColor(double y) {
        return getHudColorAtPosition(0.0, y);
    }

    public static int getHudColorAtPosition(double x, double y) {
        return getHudColorAtPosition(x, y, System.currentTimeMillis());
    }

    static int getHudColorAtPosition(double x, double y, long now) {
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (modernSettings != null) {
            return getModernColor(x, y, now, mode);
        }
        if (mode == 2) {
            return MiauThemes.RAINBOW.accent(x, y).getRGB();
        }
        MiauThemes theme = getSelectedColorTheme();
        if (theme != null) {
            if (mode == 3) {
                return breatheColor(theme.first().getRGB(), getFadePhase(y), 0.55);
            }
            return mode == 1 ? theme.accent(0.0, y).getRGB() : theme.first().getRGB();
        }
        java.awt.Color c1 = hudColor == null ? new java.awt.Color(255, 255, 255) : new java.awt.Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue());
        if (mode == 1 || mode == 3) {
            java.awt.Color c2 = hudColor2 == null ? new java.awt.Color(85, 85, 255) : new java.awt.Color(hudColor2.getRed(), hudColor2.getGreen(), hudColor2.getBlue());
            return mode == 3 ? breatheColor(c1.getRGB(), getFadePhase(y), 0.55)
                    : getGradientFadeColor(c1, c2, y);
        }
        return c1.getRGB();
    }

    public static int getHudPrimaryColor(double y) {
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (mode == 2) {
            return getHudColorAtPosition(0.0D, y);
        }
        MiauThemes theme = getSelectedColorTheme();
        if (theme != null) {
            return theme.first().getRGB();
        }
        return hudColor == null
                ? Color.WHITE.getRGB()
                : hudColor.getColor();
    }

    public static int getHudSecondaryColor(double y) {
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (mode == 2) {
            return getHudColorAtPosition(0.0D, y + 50.0D);
        }
        MiauThemes theme = getSelectedColorTheme();
        if (theme != null) {
            return theme.second().getRGB();
        }
        return hudColor2 == null
                ? new Color(85, 85, 255).getRGB()
                : hudColor2.getColor();
    }

    private static int getModernColor(double x, double y, long now, int mode) {
        double speed = modernSettings.colorSpeed.getInput();
        double spread = modernSettings.colorSpread.getInput();
        double offset = Math.toRadians(modernSettings.colorOffset.getInput());
        int direction = (int) modernSettings.colorDirection.getInput();
        double sign = modernSettings.reverseColors.isToggled() ? -1 : 1;
        if (mode == 2) {
            double position = direction == 0 ? y : direction == 1 ? x : x + y;
            double cycle = now * speed / 3600.0 + position * spread * sign / 360.0
                    + modernSettings.colorOffset.getInput() / 360.0;
            return Color.HSBtoRGB((float) (cycle - Math.floor(cycle)),
                    (float) modernSettings.rainbowSaturation.getInput(),
                    (float) modernSettings.rainbowBrightness.getInput());
        }
        int first = getHudPrimaryColor(y);
        if (mode == 0) {
            return first;
        }
        double position = direction == 0 ? y * FADE_Y_SCALE
                : direction == 1 ? x * FADE_Y_SCALE : x * 0.005 + y * FADE_Y_SCALE;
        double phase = now * speed / FADE_TIME_SCALE + position * spread * sign + offset;
        if (mode == 3) {
            return breatheColor(first, phase, modernSettings.breatheMinimum.getInput());
        }
        float factor = (float) (Math.sin(phase) * 0.5 + 0.5);
        MiauThemes theme = getSelectedColorTheme();
        if (theme != null && theme.third().getRGB() != theme.first().getRGB()) {
            return factor <= 0.5F
                    ? ModernArrayListRenderer.mix(theme.second().getRGB(), first, factor * 2)
                    : ModernArrayListRenderer.mix(theme.third().getRGB(), theme.second().getRGB(), (factor - 0.5F) * 2);
        }
        return ModernArrayListRenderer.mix(first, getHudSecondaryColor(y), factor);
    }

    private static int breatheColor(int color, double phase, double minimum) {
        float brightness = (float) (minimum + (1 - minimum) * (Math.sin(phase) * 0.5 + 0.5));
        return ModernArrayListRenderer.mix(0xFF000000, color, brightness);
    }

    private static int getGradientFadeColor(java.awt.Color first, java.awt.Color second, double y) {
        double blend = Math.sin(getFadePhase(y)) * 0.5 + 0.5;
        int red = (int) (first.getRed() * (1.0 - blend) + second.getRed() * blend);
        int green = (int) (first.getGreen() * (1.0 - blend) + second.getGreen() * blend);
        int blue = (int) (first.getBlue() * (1.0 - blend) + second.getBlue() * blend);
        return new Color(red, green, blue).getRGB();
    }

    private static double getFadePhase(double y) {
        return System.currentTimeMillis() / FADE_TIME_SCALE + y * FADE_Y_SCALE;
    }

    private static MiauThemes getSelectedColorTheme() {
        int themeIndex = hudTheme == null ? 0 : (int) hudTheme.getInput();
        if (themeIndex <= 0 || HUD_COLOR_THEMES.length == 0) {
            return null;
        }
        return HUD_COLOR_THEMES[Math.min(themeIndex - 1, HUD_COLOR_THEMES.length - 1)];
    }

    private static MiauThemes[] buildColorThemes() {
        MiauThemes[] themes = MiauThemes.values();
        MiauThemes[] result = new MiauThemes[Math.max(0, themes.length - 1)];
        int index = 0;
        for (MiauThemes theme : themes) {
            if (theme != MiauThemes.RAINBOW) {
                result[index++] = theme;
            }
        }
        if (index == result.length) {
            return result;
        }
        MiauThemes[] trimmed = new MiauThemes[index];
        System.arraycopy(result, 0, trimmed, 0, index);
        return trimmed;
    }

    private static String[] buildThemeOptions() {
        String[] result = new String[HUD_COLOR_THEMES.length + 1];
        result[0] = "Custom";
        for (int i = 0; i < HUD_COLOR_THEMES.length; i++) {
            result[i + 1] = MiauThemes.NAMES[HUD_COLOR_THEMES[i].ordinal()];
        }
        return result;
    }

}
