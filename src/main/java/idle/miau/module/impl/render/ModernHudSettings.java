package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.TextSetting;
import net.minecraft.util.StringUtils;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ModernHudSettings {
    private static final double[] BLUR_RADII = {0, 1.5, 2.5, 4, 6};
    private static final int[] BLUR_PASSES = {0, 1, 2, 3, 4};
    final Map<String, Setting> settings = new LinkedHashMap<>();
    private GroupSetting activeGroup;

    final GroupSetting colorsGroup = group("colorsGroup", "Client colors");
    final SliderSetting colorDirection = option("colorDirection", "Color direction", 2, "Vertical", "Horizontal", "Diagonal");
    final ButtonSetting reverseColors = button("reverseColors", "Reverse color direction", false);
    final SliderSetting colorSpread = slider("colorSpread", "Color spread", 1, 0, 4, 0.1);
    final SliderSetting colorSpeed = slider("colorSpeed", "Color speed", 1, 0, 5, 0.1);
    final SliderSetting colorOffset = slider("colorOffset", "Color offset", 0, 0, 360, 1);
    final SliderSetting rainbowSaturation = slider("rainbowSaturation", "Rainbow saturation", 0.6, 0, 1, 0.01);
    final SliderSetting rainbowBrightness = slider("rainbowBrightness", "Rainbow brightness", 1, 0.1, 1, 0.01);
    final SliderSetting breatheMinimum = slider("breatheMinimum", "Breathe brightness", 0.55, 0.1, 1, 0.01);

    final GroupSetting layoutGroup = group("layoutGroup", "Layout");
    final SliderSetting alignment = option("alignment", "Alignment", 0, "Right", "Left");
    final SliderSetting growth = option("growth", "List direction", 0, "Down", "Up");
    final ButtonSetting lockPosition = button("lockPosition", "Lock position", false);
    final SliderSetting widthMode = option("widthMode", "Row width", 0, "Content", "Uniform");
    final SliderSetting minimumWidth = slider("minimumWidth", "Minimum row width", 0, 0, 300, 1);
    final SliderSetting textAlignment = option("textAlignment", "Text alignment", 0, "Follow edge", "Left", "Center", "Right");
    final SliderSetting horizontalPadding = slider("horizontalPadding", "Horizontal padding", 3, 0, 20, 0.5);
    final SliderSetting verticalPadding = slider("verticalPadding", "Vertical padding", 1, 0, 12, 0.5);
    final SliderSetting rowGap = slider("rowGap", "Row spacing", 0, 0, 20, 0.5);
    final SliderSetting sorting = option("sorting", "Sort by", 0, "Width", "Alphabetical");
    final ButtonSetting reverseSort = button("reverseSort", "Reverse sorting", false);
    final MultiSelectSetting hiddenCategories = add("hiddenCategories", new MultiSelectSetting("Hidden categories",
            Arrays.stream(Module.category.values()).map(ModernHudSettings::categoryName).toArray(String[]::new)));

    final GroupSetting animationGroup = group("animationGroup", "Animation settings");
    final SliderSetting animationDuration = slider("animationDuration", "Animation duration", 250, 50, 1000, 10);

    final GroupSetting textGroup = group("textGroup", "Text settings");
    final ButtonSetting removeSpaces = button("removeSpaces", "Remove name spaces", false);
    final SliderSetting textColorMode = option("textColorMode", "Text color", 0, "Theme", "White", "Custom");
    final ColorSetting textColor = color("textColor", "Custom text color", 255, 255, 255);
    final ButtonSetting gradientText = button("gradientText", "Gradient across text", false);
    final SliderSetting textOpacity = slider("textOpacity", "Text opacity", 255, 0, 255, 1);
    final SliderSetting textOffsetX = slider("textOffsetX", "Text offset X", 0, -10, 10, 0.5);
    final SliderSetting textOffsetY = slider("textOffsetY", "Text offset Y", 0, -10, 10, 0.5);

    final GroupSetting suffixGroup = group("suffixGroup", "Suffix settings");
    final SliderSetting suffixStyle = option("suffixStyle", "Suffix style", 0,
            "Space", "Dash", "Brackets", "Parentheses", "Angle brackets", "Pipe", "Custom");
    final TextSetting suffixFormat = add("suffixFormat", new TextSetting("Suffix format", " {info}", " {info}", 64));
    final SliderSetting suffixGap = slider("suffixGap", "Suffix spacing", 3, 0, 20, 0.5);
    final SliderSetting suffixColorMode = option("suffixColorMode", "Suffix color", 0, "Gray", "Theme", "White", "Custom");
    final ColorSetting suffixColor = color("suffixColor", "Custom suffix color", 170, 170, 170);
    final SliderSetting suffixOpacity = slider("suffixOpacity", "Suffix opacity", 255, 0, 255, 1);

    final GroupSetting backgroundGroup = group("backgroundGroup", "Background settings");
    final SliderSetting background = option("background", "Background", 1, "None", "Dark", "Tinted", "Gradient");
    final SliderSetting backgroundAlpha = slider("backgroundAlpha", "Background opacity", 110, 0, 255, 1);
    final SliderSetting rounding = slider("rounding", "Corner radius", 0, 0, 12, 0.5);
    final SliderSetting backgroundGradient = option("backgroundGradient", "Gradient colors", 0, "Theme", "Custom");
    final ColorSetting backgroundColor = color("backgroundColor", "Background color", 12, 12, 16);
    final ColorSetting backgroundColor2 = color("backgroundColor2", "Second background color", 30, 30, 45);
    final SliderSetting backgroundTint = slider("backgroundTint", "Background tint", 20, 0, 100, 1);
    final SliderSetting gradientDirection = option("gradientDirection", "Gradient direction", 0,
            "Left to right", "Right to left", "Top to bottom", "Bottom to top", "Diagonal", "Reverse diagonal");
    final ButtonSetting blur = button("blur", "Background blur", true);
    final SliderSetting blurPreset = option("blurPreset", "Blur preset", 0, "Custom", "Soft", "Balanced", "Strong", "Ultra");
    final SliderSetting blurRadius = slider("blurRadius", "Blur radius", 2.5, 0.5, 8, 0.5);
    final SliderSetting blurPasses = slider("blurPasses", "Blur passes", 2, 1, 4, 1);

    final GroupSetting outlineGroup = group("outlineGroup", "Outline settings");
    final SliderSetting outline = add("outline", new SliderSetting("Outline", 3,
            new String[]{"None", "Sidebar", "Steps", "Full", "Top", "Bottom"}, "Modern outline", "Outline settings.Modern outline"));
    final SliderSetting sidebar = option("sidebar", "Sidebar position", 1, "Outer", "Inner", "Both");
    final SliderSetting outlineWidth = slider("outlineWidth", "Outline width", 1, 0.5, 3, 0.5);
    final SliderSetting outlineColorMode = option("outlineColorMode", "Outline color", 0, "Theme", "White", "Custom");
    final ColorSetting outlineColor = color("outlineColor", "Custom outline color", 255, 255, 255);
    final SliderSetting outlineOpacity = slider("outlineOpacity", "Outline opacity", 255, 0, 255, 1);

    final GroupSetting shaderGroup = group("shaderGroup", "Shader settings");
    final SliderSetting effect = option("effect", "Shader effect", 1, "None", "Shadow", "Glow");
    final SliderSetting effectTarget = option("effectTarget", "Shader target", 0, "Background", "Text", "Outline", "All");
    final SliderSetting effectColorMode = option("effectColorMode", "Shader color", 0, "Auto", "Theme", "Custom");
    final ColorSetting effectColor = color("effectColor", "Custom shader color", 0, 0, 0);
    final SliderSetting effectOpacity = slider("effectOpacity", "Shader opacity", 100, 0, 255, 1);
    final SliderSetting effectOffsetX = slider("effectOffsetX", "Shader offset X", 0, -15, 15, 0.5);
    final SliderSetting effectOffsetY = slider("effectOffsetY", "Shader offset Y", 0, -15, 15, 0.5);
    final SliderSetting effectRadius = slider("effectRadius", "Shader radius", 2.5, 0.5, 8, 0.5);
    final SliderSetting effectPasses = slider("effectPasses", "Shader passes", 3, 1, 4, 1);

    void updateVisibility(Module hud, boolean modern, int colorMode, boolean showInfo, boolean animated) {
        Map<Setting, Boolean> visibility = new IdentityHashMap<>();
        for (Setting setting : settings.values()) {
            visibility.put(setting, modern);
        }
        visibility.put(colorsGroup, true);
        visibility.put(layoutGroup, true);
        visibility.put(animationGroup, true);
        visibility.put(textGroup, true);
        visibility.put(suffixGroup, true);
        visibility.put(backgroundGroup, true);
        visibility.put(outlineGroup, true);
        for (GroupSetting group : new GroupSetting[]{colorsGroup, textGroup, backgroundGroup, outlineGroup, shaderGroup}) {
            visibility.put(group, true);
            for (Setting setting : settings.values()) {
                if (setting.getConfigKey().startsWith(group.getName() + ".")) visibility.put(setting, true);
            }
        }
        for (Setting setting : new Setting[]{textColorMode, gradientText, textOpacity, textOffsetX, textOffsetY,
                background, blur, blurPreset, outline, effect}) visibility.put(setting, true);
        boolean hasBackground = background.getInput() != 0;
        boolean hasEffect = effect.getInput() != 0;
        visibility.put(animationDuration, modern && animated);
        visibility.put(backgroundColor, hasBackground);
        visibility.put(backgroundAlpha, hasBackground);
        boolean gradientBackground = background.getInput() == 3;
        visibility.put(backgroundGradient, gradientBackground);
        visibility.put(backgroundColor2, gradientBackground && backgroundGradient.getInput() == 1);
        visibility.put(gradientDirection, gradientBackground);
        visibility.put(backgroundTint, hasBackground && (background.getInput() == 2
                || background.getInput() == 3 && backgroundGradient.getInput() == 0));
        visibility.put(rounding, (outline.getInput() != 0 || hasBackground || blurEnabled()
                || hasEffect && (effectTarget.getInput() == 0 || effectTarget.getInput() == 3)));
        visibility.put(blurRadius, blurEnabled());
        visibility.put(blurPasses, blurEnabled());
        visibility.put(effectTarget, hasEffect);
        visibility.put(effectOpacity, hasEffect);
        visibility.put(effectRadius, hasEffect);
        visibility.put(effectPasses, hasEffect);
        visibility.put(effectColorMode, hasEffect);
        visibility.put(effectColor, hasEffect && effectColorMode.getInput() == 2);
        visibility.put(effectOffsetX, hasEffect);
        visibility.put(effectOffsetY, hasEffect);
        visibility.put(sidebar, outline.getInput() == 1);
        visibility.put(outlineWidth, outline.getInput() != 0);
        visibility.put(outlineColorMode, outline.getInput() != 0);
        visibility.put(outlineColor, outline.getInput() != 0 && outlineColorMode.getInput() == 2);
        visibility.put(outlineOpacity, outline.getInput() != 0);
        visibility.put(textColor, textColorMode.getInput() == 2);
        visibility.put(gradientText, textColorMode.getInput() == 0);
        visibility.put(suffixColorMode, modern && showInfo);
        visibility.put(suffixColor, modern && showInfo && suffixColorMode.getInput() == 3);
        visibility.put(suffixStyle, modern && showInfo);
        visibility.put(suffixGap, modern && showInfo);
        visibility.put(suffixOpacity, modern && showInfo);
        visibility.put(suffixFormat, modern && showInfo && suffixStyle.getInput() == 6);
        visibility.put(colorSpeed, colorMode != 0);
        visibility.put(colorSpread, colorMode != 0);
        visibility.put(colorOffset, colorMode != 0);
        visibility.put(colorDirection, colorMode != 0);
        visibility.put(reverseColors, colorMode != 0);
        visibility.put(rainbowSaturation, colorMode == 2);
        visibility.put(rainbowBrightness, colorMode == 2);
        visibility.put(breatheMinimum, colorMode == 3);
        for (Setting setting : settings.values()) {
            setting.setVisible(visibility.get(setting), hud);
        }
    }

    void applyBlurPreset() {
        int preset = (int) blurPreset.getInput();
        if (preset <= 0 || preset >= BLUR_RADII.length) {
            return;
        }
        blur.enable();
        blurRadius.setValue(BLUR_RADII[preset]);
        blurPasses.setValue(BLUR_PASSES[preset]);
    }

    boolean blurEnabled() {
        return effect.getInput() != 0 && blur.isToggled();
    }

    boolean shaderEnabled() {
        return effect.getInput() != 0 && effectOpacity.getInput() > 0;
    }

    String formatSuffix(String info) {
        if (info == null || info.isEmpty()) {
            return "";
        }
        switch ((int) suffixStyle.getInput()) {
            case 1:
                return " - " + info;
            case 2:
                return " [" + info + "]";
            case 3:
                return " (" + info + ")";
            case 4:
                return " <" + info + ">";
            case 5:
                return " | " + info;
            case 6:
                return StringUtils.stripControlCodes(suffixFormat.getText())
                        .replace("{info}", info);
            default:
                return " " + info;
        }
    }

    boolean hidesCategory(Module.category category) {
        return category != null && hiddenCategories.isSelected(categoryName(category));
    }

    private static String categoryName(Module.category category) {
        String name = category.name().replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private GroupSetting group(String id, String name) {
        activeGroup = new GroupSetting(name);
        settings.put(id, activeGroup);
        return activeGroup;
    }

    private SliderSetting option(String id, String name, int value, String... options) {
        return add(id, new SliderSetting(name, value, options));
    }

    private SliderSetting slider(String id, String name, double value, double min, double max, double step) {
        return add(id, new SliderSetting(name, value, min, max, step));
    }

    private ButtonSetting button(String id, String name, boolean value) {
        return add(id, new ButtonSetting(name, value));
    }

    private ColorSetting color(String id, String name, int red, int green, int blue) {
        return add(id, new ColorSetting(name, red, green, blue));
    }

    private <T extends Setting> T add(String id, T setting) {
        setGroup(setting, activeGroup);
        settings.put(id, setting);
        return setting;
    }

    static void setGroup(Setting setting, GroupSetting group) {
        if (setting instanceof SliderSetting slider) slider.groupSetting = group;
        else if (setting instanceof ButtonSetting button) button.group = group;
        else if (setting instanceof ColorSetting color) color.groupSetting = group;
        else if (setting instanceof TextSetting text) text.group = group;
        else if (setting instanceof MultiSelectSetting selection) selection.groupSetting = group;
    }
}
