package idle.miau.module.impl.client;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.GuiScreen;

public class ClickGui extends Module {
    private static final String[] GUI_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final String[] GUI_MODE_OPTIONS = {"Raven", "Slinky", "Miau"};

    public static SliderSetting mode;
    public static SliderSetting guiScale;
    public static SliderSetting font;
    public static SliderSetting backgroundBlur;
    public static SliderSetting scrollSpeed;
    public static ButtonSetting removePlayerModel;
    public static ButtonSetting darkBackground;
    public static ButtonSetting removeWatermark;
    public static ButtonSetting rainBowOutlines;
    public static ColorSetting panelAccent;
    public static ButtonSetting panelCompact;
    public static ButtonSetting panelWide;
    public static ButtonSetting miauBlur;
    public static SliderSetting miauBlurRadius;
    public static ButtonSetting miauMovement;
    private boolean switchingGui;

    public ClickGui() {
        super("ClickGUI", category.render, 54);
        this.registerSetting(mode = new SliderSetting("Mode", 2, GUI_MODE_OPTIONS));
        this.registerSetting(guiScale = new SliderSetting("Gui scale", "x", 1.0, 0.5, 2.0, 0.01));
        this.registerSetting(font = new SliderSetting("Font", 0, GUI_FONT_OPTIONS));
        this.registerSetting(backgroundBlur = new SliderSetting("Background blur", "%", 0, 0, 100, 1));
        this.registerSetting(scrollSpeed = new SliderSetting("Scroll speed", 20, 2, 90, 1));
        this.registerSetting(darkBackground = new ButtonSetting("Dark background", true));
        this.registerSetting(rainBowOutlines = new ButtonSetting("Rainbow outlines", true));
        this.registerSetting(removePlayerModel = new ButtonSetting("Remove player model", false));
        this.registerSetting(removeWatermark = new ButtonSetting("Remove watermark", false));
        this.registerSetting(panelAccent = new ColorSetting("Slinky Accent", 255, 89, 0));
        this.registerSetting(panelCompact = new ButtonSetting("Slinky Compact", false));
        this.registerSetting(panelWide = new ButtonSetting("Slinky Wide", false));
        this.registerSetting(miauBlur = new ButtonSetting("Blur", true, "Miau blur"));
        this.registerSetting(miauBlurRadius = new SliderSetting("Blur radius", "", 7, 1, 20, 1,
                "Miau blur radius"));
        this.registerSetting(miauMovement = new ButtonSetting("Allow movement", true,
                "Miau allow movement"));
    }

    @Override
    public void onEnable() {
        if (Utils.nullCheck()) {
            openSelectedGui();
        }
        this.disable();
    }

    @Override
    public void guiUpdate() {
        boolean classic = (int) mode.getInput() == 0;
        boolean panel = isPanelMode();
        boolean miau = isMiauMode();
        guiScale.setVisible(true, this);
        font.setVisible(classic, this);
        backgroundBlur.setVisible(classic, this);
        scrollSpeed.setVisible(!panel && !miau, this);
        darkBackground.setVisible(classic, this);
        rainBowOutlines.setVisible(classic, this);
        removePlayerModel.setVisible(classic, this);
        removeWatermark.setVisible(classic, this);
        panelAccent.setVisible(panel, this);
        panelCompact.setVisible(panel, this);
        panelWide.setVisible(panel, this);
        miauBlur.setVisible(miau, this);
        miauBlurRadius.setVisible(miau && miauBlur.isToggled(), this);
        miauMovement.setVisible(miau, this);
        if (mc != null && !switchingGui
                && (mc.currentScreen == Miau.clickGui || mc.currentScreen == Miau.panelClickGui || mc.currentScreen == Miau.miauClickGui)
                && mc.currentScreen != selectedGui()) {
            switchingGui = true;
            try {
                openSelectedGui();
            }
            finally {
                switchingGui = false;
            }
        }
    }

    public static boolean isPanelMode() {
        return mode != null && (int) mode.getInput() == 1;
    }

    public static boolean isMiauMode() {
        return mode != null && (int) mode.getInput() == 2;
    }

    private GuiScreen selectedGui() {
        if (isMiauMode()) {
            return Miau.miauClickGui;
        }
        if (isPanelMode()) {
            return Miau.panelClickGui;
        }
        return Miau.clickGui;
    }

    private void openSelectedGui() {
        GuiScreen selected = selectedGui();
        if (selected == null || mc.currentScreen == selected) {
            return;
        }
        mc.displayGuiScreen(selected);
        if (selected == Miau.clickGui) {
            Miau.clickGui.initMain();
        }
    }

    public static String getSelectedFontName() {
        if (font == null || font.getOptions() == null || font.getOptions().length == 0) {
            return GUI_FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static MiauFontRenderer getClickGuiHeaderFontRenderer() {
        return FontManager.getClickGuiHeaderRenderer(getSelectedFontName());
    }

    public static MiauFontRenderer getClickGuiSettingFontRenderer() {
        return FontManager.getClickGuiSettingRenderer(getSelectedFontName());
    }

    public static float getClickGuiScale() {
        if (guiScale == null) {
            return 1.0F;
        }
        return (float) Math.max(0.5D, Math.min(2.0D, guiScale.getInput()));
    }
}
