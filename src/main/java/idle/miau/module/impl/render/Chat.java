package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;

public class Chat extends Module {
    public static final String[] ANIMATION_MODES = {"None", "Slide", "Fade"};
    public static final String[] BOX_STYLES = {"Rounded Box", "Rounded Lines", "Transparent", "Vanilla"};
    public static SliderSetting animationMode;
    public static SliderSetting animSpeed;
    public static ButtonSetting openAnimation;
    public static ButtonSetting smoothScroll;
    public static SliderSetting chatBoxStyle;
    public static SliderSetting cornerRadius;
    public static SliderSetting backgroundOpacity;
    public static ButtonSetting roundedInputBox;
    public static SliderSetting inputBoxRadius;
    public static ButtonSetting shader;
    public static ButtonSetting blur;
    public static ButtonSetting bloom;

    public Chat() {
        super("Chat", category.render);
        setDescription("Customizes chat rounding, animations and neutral shaders.");
        canBeDisabled = false;
        setEnabled(true);
        GroupSetting appearance = new GroupSetting("Appearance");
        registerSetting(appearance);
        add(appearance, chatBoxStyle = new SliderSetting("Box Style", 3, BOX_STYLES));
        add(appearance, cornerRadius = new SliderSetting("Corner Radius", "px", 5, 1, 10, 0.5));
        add(appearance, backgroundOpacity = new SliderSetting("BG Opacity", 0.5, 0, 1, 0.05));
        add(appearance, roundedInputBox = new ButtonSetting("Rounded Input", false));
        add(appearance, inputBoxRadius = new SliderSetting("Input Radius", "px", 4, 1, 8, 0.5));
        GroupSetting animations = new GroupSetting("Animations");
        registerSetting(animations);
        add(animations, animationMode = new SliderSetting("Animation", 0, ANIMATION_MODES));
        add(animations, animSpeed = new SliderSetting("Animation speed", "ms", 250, 100, 600, 25, "Anim Speed"));
        add(animations, openAnimation = new ButtonSetting("Open animation", false));
        add(animations, smoothScroll = new ButtonSetting("Smooth scroll", false));
        GroupSetting shaders = new GroupSetting("Shaders");
        registerSetting(shaders);
        add(shaders, shader = new ButtonSetting("Shader", false));
        add(shaders, blur = new ButtonSetting("Blur", true));
        add(shaders, bloom = new ButtonSetting("Bloom", false));
        guiUpdate();
    }

    private void add(GroupSetting group, Setting setting) {
        ModernHudSettings.setGroup(setting, group);
        registerSetting(setting);
    }

    @Override
    public void guiUpdate() {
        cornerRadius.setVisible(getChatBoxStyle() <= 1, this);
        inputBoxRadius.setVisible(roundedInputBox.isToggled(), this);
        animSpeed.setVisible(animationMode.getInput() != 0 || openAnimation.isToggled(), this);
        blur.setVisible(shader.isToggled(), this);
        bloom.setVisible(shader.isToggled(), this);
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
        return ModuleManager.chat != null && ModuleManager.chat.isEnabled();
    }

    public static int getAnimationMode() {
        return isActive() && animationMode != null ? (int) animationMode.getInput() : 0;
    }

    public static long getAnimationSpeed() {
        return isActive() && animSpeed != null ? (long) animSpeed.getInput() : 250;
    }

    public static boolean isOpenAnimationActive() {
        return isActive() && openAnimation != null && openAnimation.isToggled();
    }

    public static boolean isSmoothScrollActive() {
        return isActive() && smoothScroll != null && smoothScroll.isToggled();
    }

    public static boolean isTextShadowActive() {
        return ArrayListModule.isHudTextShadowEnabled();
    }

    public static boolean isBlurActive() {
        return isShaderActive() && blur != null && blur.isToggled();
    }

    public static float getBlurStrength() {
        return HudStyle.blurRadius();
    }

    public static boolean isBloomActive() {
        return isShaderActive() && bloom != null && bloom.isToggled();
    }

    public static boolean isShaderActive() {
        return isActive() && shader != null && shader.isToggled();
    }

    public static float getBloomStrength() {
        return HudStyle.shaderRadius();
    }

    public static int getChatBoxStyle() {
        return chatBoxStyle == null ? 3 : (int) chatBoxStyle.getInput();
    }

    public static float getCornerRadius() {
        return cornerRadius == null ? 5 : (float) cornerRadius.getInput();
    }

    public static float getBackgroundOpacity() {
        return backgroundOpacity == null ? 0.5f : (float) backgroundOpacity.getInput();
    }

    public static boolean isRoundedInputBoxActive() {
        return isActive() && roundedInputBox != null && roundedInputBox.isToggled();
    }

    public static float getInputBoxRadius() {
        return inputBoxRadius == null ? 4 : (float) inputBoxRadius.getInput();
    }

    public static HudStyle.PanelStyle panelStyle() {
        int style = getChatBoxStyle();
        float radius = style <= 1 ? getCornerRadius() * HudStyle.scale() : 0;
        int background = style == 2 ? 0 : Math.round(getBackgroundOpacity() * 255) << 24;
        return localStyle(radius, background);
    }

    public static HudStyle.PanelStyle inputPanelStyle() {
        float radius = isRoundedInputBoxActive() ? getInputBoxRadius() * HudStyle.scale() : 0;
        return localStyle(radius, Math.round(getBackgroundOpacity() * 255) << 24);
    }

    private static HudStyle.PanelStyle localStyle(float radius, int background) {
        return HudStyle.PanelStyle.shared(radius, background, isBlurActive(), isBloomActive());
    }
}
