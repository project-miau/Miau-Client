package idle.miau.module.impl.client;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.SliderSetting;

public final class Notifications extends Module {
    private static final String CHAT = "Chat";
    private static final String OVERLAY = "Slinky";
    private static final String[] ANIMATION_OPTIONS = {"None", "Move in", "Scale in"};
    private static final String[] SCALE_OPTIONS = {"75%", "100%", "150%", "200%"};
    private static final float[] SCALE_FACTORS = {0.75F, 1.0F, 1.5F, 2.0F};

    public static ButtonSetting hideInGame;
    public static ButtonSetting hideHoldNotifications;
    public static ButtonSetting showCombat;
    public static ButtonSetting showVisual;
    public static ButtonSetting showUtility;
    public static ColorSetting accent;
    public static SliderSetting scale;
    public static SliderSetting animation;

    private final MultiSelectSetting mode;
    private final GroupSetting hideIf;
    private final GroupSetting categories;

    public Notifications() {
        super("Notifications", category.render, 0);
        setDescription("Shows alerts when modules change state.");
        this.registerSetting(mode = new MultiSelectSetting(
                "Mode", new String[]{CHAT, OVERLAY}, CHAT, OVERLAY));

        hideIf = new GroupSetting("Hide if");
        this.registerSetting(hideIf);
        this.registerSetting(hideInGame = new ButtonSetting(hideIf, "In-game", false));
        this.registerSetting(hideHoldNotifications = new ButtonSetting(
                hideIf, "Hold bind is used", true));

        categories = new GroupSetting("Visible mod categories");
        this.registerSetting(categories);
        this.registerSetting(showCombat = new ButtonSetting(categories, "Combat", true));
        this.registerSetting(showVisual = new ButtonSetting(categories, "Visual", true));
        this.registerSetting(showUtility = new ButtonSetting(categories, "Utility", true));

        this.registerSetting(accent = new ColorSetting("Accent", 0xBA, 0x8F, 0x79));
        this.registerSetting(scale = new SliderSetting("Scale", 1, SCALE_OPTIONS));
        this.registerSetting(animation = new SliderSetting("Animation", 2, ANIMATION_OPTIONS));
        this.setHidden(true);
        guiUpdate();
    }

    @Override
    public void onEnable() {
        if (Miau.notificationService != null) {
            Miau.notificationService.onNotificationsEnabled();
        }
    }

    @Override
    public void onDisable() {
        if (Miau.notificationService != null) {
            Miau.notificationService.clear();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting != mode || Miau.notificationService == null) {
            return;
        }
        if (isOverlayEnabled()) {
            Miau.notificationService.onNotificationsEnabled();
        } else {
            Miau.notificationService.clear();
        }
    }

    @Override
    public void guiUpdate() {
        boolean visible = mode.isSelected(OVERLAY);
        hideIf.setVisible(visible, this);
        categories.setVisible(visible, this);
        accent.setVisible(visible, this);
        scale.setVisible(visible, this);
        animation.setVisible(visible, this);
    }

    public static boolean isChatEnabled() {
        return isOutputEnabled(CHAT);
    }

    public static boolean isOverlayEnabled() {
        return isOutputEnabled(OVERLAY);
    }

    private static boolean isOutputEnabled(String output) {
        Notifications module = ModuleManager.notifications;
        return module != null && module.isEnabled() && module.mode.isSelected(output);
    }

    public static float scaleFactor() {
        int index = scale == null ? 1 : (int) scale.getInput();
        index = Math.max(0, Math.min(SCALE_FACTORS.length - 1, index));
        return SCALE_FACTORS[index];
    }

    public static int accentRgb() {
        return accent == null ? 0xBA8F79 : accent.getRGB();
    }

    public static int animationMode() {
        int index = animation == null ? 2 : (int) animation.getInput();
        return Math.max(0, Math.min(ANIMATION_OPTIONS.length - 1, index));
    }
}
