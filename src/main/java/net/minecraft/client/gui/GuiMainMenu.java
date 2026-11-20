package net.minecraft.client.gui;

import idle.miau.performance.MiauDrawCalls;
import java.io.IOException;

import dev.idle.igam.gui.GuiAccountManager;
import idle.miau.gui.GuiChangelogs;
import idle.miau.gui.MiauMenuButton;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.utility.changelog.ClientChangelogEntry;
import idle.miau.utility.changelog.ClientChangelogLine;
import idle.miau.utility.changelog.ClientChangelogOverview;
import idle.miau.utility.config.MainMenuPreferences;
import idle.miau.utility.font.FontManager;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.MainMenuBackgroundShader;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.demo.DemoWorldServer;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.WorldInfo;
import org.lwjgl.opengl.GL11;

public class GuiMainMenu extends GuiScreen implements GuiYesNoCallback {
    private static final String TITLE_TEXT = "miau";
    private static final ResourceLocation TITLE_LOGO =
            new ResourceLocation("miau/images/title_cat.png");
    private static final ResourceLocation[] FRIENDLY_CAT_SOUNDS = {
            new ResourceLocation("mob.cat.meow"),
            new ResourceLocation("mob.cat.purreow")
    };
    private static final int TITLE_LOGO_TEXTURE_WIDTH = 128;
    private static final int TITLE_LOGO_TEXTURE_HEIGHT = 114;
    private static final int TITLE_HIT_PADDING = 2;
    private static final long TITLE_TOGGLE_ANIMATION_DURATION_NANOS = 260_000_000L;
    private static final float TITLE_TOGGLE_START_SCALE = 0.35F;
    private static final int ACTION_HEIGHT = 14;
    private static final int CONTENT_MARGIN_MIN = 8;
    private static final int CONTENT_MARGIN_MAX = 48;
    private static final float CONTENT_MARGIN_RATIO = 0.022F;
    private static final int CONTENT_COLUMN_GAP = 12;
    private static final int ACCOUNT_MANAGER_BUTTON = 77701;
    private static final int DEFAULT_SHADER_PRIMARY = 0xFF40BFFF;
    private static final int DEFAULT_SHADER_SECONDARY = 0xFFFF8099;
    private static final long BOOT_TRANSITION_DURATION_NANOS = 550_000_000L;
    private static final float BOOT_TRANSITION_START_SCALE = 0.84F;
    private static final float BOOT_TRANSITION_EXPO_EXPONENT = 10.0F;

    private GuiButton buttonResetDemo;
    private long openedAt;
    private boolean bootTransitionPending;
    private long bootTransitionStartedAt = -1L;
    private int contentMargin;
    private int actionWidth;
    private int changelogWidth;
    private int changelogTop;
    private int changelogHeight;
    private int changelogAnimationOrder;
    private MiauFontRenderer titleFont;
    private MiauFontRenderer smallFont;
    private MiauFontRenderer buttonFont;
    private MainMenuPreferences preferences;
    private boolean titleLogoVisible;
    private long titleToggleAnimationStartedAt = -1L;
    private int nextCatSoundIndex;
    private ISound activeCatSound;

    public GuiMainMenu() {
        this(false);
    }

    public GuiMainMenu(boolean playBootTransition) {
        this.bootTransitionPending = playBootTransition;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.contentMargin = calculateContentMargin(this.width, this.height);
        this.actionWidth = Math.max(112, Math.round(this.width * 0.18F));
        initializeFonts();
        initializePreferences();
        ClientChangelogOverview.ensureLoaded();
        this.openedAt = System.nanoTime();

        int actionX = this.width - this.contentMargin - this.actionWidth;
        int actionY = this.contentMargin;
        int order = 0;

        if (this.mc.isDemo()) {
            this.buttonList.add(createActionButton(11, actionX, actionY,
                    I18n.format("menu.playdemo", new Object[0]), order++));
            this.buttonResetDemo = createActionButton(12, actionX, actionY += ACTION_HEIGHT,
                    I18n.format("menu.resetdemo", new Object[0]), order++);
            this.buttonList.add(this.buttonResetDemo);
            WorldInfo demoWorld = this.mc.getSaveLoader().getWorldInfo("Demo_World");
            this.buttonResetDemo.enabled = demoWorld != null;
        }
        else {
            this.buttonList.add(createActionButton(1, actionX, actionY,
                    I18n.format("menu.singleplayer", new Object[0]), order++));
            this.buttonList.add(createActionButton(2, actionX, actionY += ACTION_HEIGHT,
                    I18n.format("menu.multiplayer", new Object[0]), order++));
            this.buttonList.add(createActionButton(ACCOUNT_MANAGER_BUTTON, actionX,
                    actionY += ACTION_HEIGHT, "Account Manager", order++));
        }

        this.buttonList.add(createActionButton(0, actionX, actionY += ACTION_HEIGHT,
                "Settings", order++));

        int changelogY = this.contentMargin + this.titleFont.getFontHeight() + 8;
        this.changelogTop = changelogY;
        this.changelogHeight = 0;
        this.changelogWidth = Math.min(320, Math.max(
                80,
                actionX - this.contentMargin - CONTENT_COLUMN_GAP));
        this.changelogAnimationOrder = order;
    }

    private void initializeFonts() {
        this.titleFont = FontManager.getMainMenuRenderer(21.0F);
        this.smallFont = FontManager.getMainMenuRenderer(8.0F);
        this.buttonFont = FontManager.getMainMenuRenderer(11.0F);
    }

    private void initializePreferences() {
        if (this.preferences != null) {
            return;
        }
        this.preferences = MainMenuPreferences.forGameDirectory(this.mc.mcDataDir);
        this.titleLogoVisible = this.preferences.loadLogoMode()
                == MainMenuPreferences.LogoMode.IMAGE;
    }

    private MiauMenuButton createActionButton(int id, int x, int y,
                                              String label, int order) {
        return new MiauMenuButton(id, x, y,
                this.actionWidth, ACTION_HEIGHT, label, order,
                this.buttonFont, true);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                break;
            case 1:
                this.mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 4:
                this.mc.shutdown();
                break;
            case 5:
                this.mc.displayGuiScreen(new GuiLanguage(this, this.mc.gameSettings,
                        this.mc.getLanguageManager()));
                break;
            case 11:
                this.mc.launchIntegratedServer("Demo_World", "Demo_World",
                        DemoWorldServer.demoWorldSettings);
                break;
            case 12:
                confirmDemoReset();
                break;
            case ACCOUNT_MANAGER_BUTTON:
                this.mc.displayGuiScreen(new GuiAccountManager(this));
                break;
            default:
                break;
        }
    }

    private void confirmDemoReset() {
        ISaveFormat saveLoader = this.mc.getSaveLoader();
        WorldInfo demoWorld = saveLoader.getWorldInfo("Demo_World");
        if (demoWorld != null) {
            this.mc.displayGuiScreen(GuiSelectWorld.makeDeleteWorldYesNo(
                    this, demoWorld.getWorldName(), 12));
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (result && id == 12) {
            ISaveFormat saveLoader = this.mc.getSaveLoader();
            saveLoader.flushCache();
            saveLoader.deleteWorldDirectory("Demo_World");
        }
        this.mc.displayGuiScreen(this);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();

        if (this.bootTransitionPending && this.bootTransitionStartedAt < 0L) {
            this.bootTransitionStartedAt = now;
        }

        float bootProgress = getBootTransitionProgress(now);
        boolean bootTransitionRunning = bootProgress < 1.0F;
        int menuMouseX = bootTransitionRunning ? Integer.MIN_VALUE : mouseX;
        int menuMouseY = bootTransitionRunning ? Integer.MIN_VALUE : mouseY;

        if (bootTransitionRunning) {
            Gui.drawRect(0, 0, this.width, this.height, 0xFF000000);
            float scale = calculateBootScale(bootProgress);
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.width / 2.0F, this.height / 2.0F, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.translate(-this.width / 2.0F, -this.height / 2.0F, 0.0F);
        }

        drawBackground(now);
        drawInformation(now);
        super.drawScreen(menuMouseX, menuMouseY, partialTicks);
        drawFooter();

        if (bootTransitionRunning) {
            GlStateManager.popMatrix();
        }

        if (this.bootTransitionPending && bootProgress >= 1.0F) {
            this.bootTransitionPending = false;
            this.bootTransitionStartedAt = -1L;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float getBootTransitionProgress(long now) {
        if (!this.bootTransitionPending) {
            return 1.0F;
        }
        if (this.bootTransitionStartedAt < 0L) {
            return 0.0F;
        }
        return clamp((now - this.bootTransitionStartedAt) / (float) BOOT_TRANSITION_DURATION_NANOS);
    }

    static float calculateBootScale(float progress) {
        float easedProgress = easeOutExpo(progress);
        return BOOT_TRANSITION_START_SCALE
                + (1.0F - BOOT_TRANSITION_START_SCALE) * easedProgress;
    }

    static float easeOutExpo(float progress) {
        float clampedProgress = clamp(progress);
        if (clampedProgress <= 0.0F || clampedProgress >= 1.0F) {
            return clampedProgress;
        }
        float endpoint = 1.0F - (float) Math.pow(2.0D, -BOOT_TRANSITION_EXPO_EXPONENT);
        return (1.0F - (float) Math.pow(
                2.0D, -BOOT_TRANSITION_EXPO_EXPONENT * clampedProgress)) / endpoint;
    }

    private void drawBackground(long now) {
        float elapsedSeconds = Math.max(0L, now - this.openedAt) / 1_000_000_000.0F;
        int primaryColor = getConfiguredHudColor(0.0D, DEFAULT_SHADER_PRIMARY);
        int secondaryColor = getConfiguredHudColor(48.0D, DEFAULT_SHADER_SECONDARY);
        if (MainMenuBackgroundShader.render(elapsedSeconds, primaryColor, secondaryColor)) {
            return;
        }

        drawFallbackBackground(now);
    }

    private static int getConfiguredHudColor(double offset, int fallbackColor) {
        if (ModuleManager.arraylist == null) {
            return fallbackColor;
        }
        try {
            return ArrayListModule.getHudColor(offset);
        }
        catch (RuntimeException | LinkageError ignored) {
            return fallbackColor;
        }
    }

    private void drawFallbackBackground(long now) {
        this.drawGradientRect(0, 0, this.width, this.height, 0xFF14233A, 0xFF080F1D);
        double seconds = now / 1_000_000_000.0D;
        float radius = Math.max(this.width, this.height) * 0.42F;
        drawSoftGlow(
                this.width * 0.16F + (float) Math.sin(seconds * 0.17D) * this.width * 0.04F,
                this.height * 0.20F + (float) Math.cos(seconds * 0.13D) * this.height * 0.04F,
                radius,
                79,
                183,
                224,
                9);
        drawSoftGlow(
                this.width * 0.84F + (float) Math.cos(seconds * 0.15D) * this.width * 0.04F,
                this.height * 0.78F + (float) Math.sin(seconds * 0.11D) * this.height * 0.04F,
                radius,
                137,
                102,
                220,
                8);
    }

    private void drawSoftGlow(float centerX, float centerY, float radius,
                              int red, int green, int blue, int maximumAlpha) {
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(red / 255.0F, green / 255.0F, blue / 255.0F, maximumAlpha / 255.0F);
        GL11.glVertex2f(centerX, centerY);
        GL11.glColor4f(red / 255.0F, green / 255.0F, blue / 255.0F, 0.0F);
        for (int segment = 0; segment <= 64; segment++) {
            double angle = segment * Math.PI * 2.0D / 64.0D;
            GL11.glVertex2f(
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
        MiauDrawCalls.glEnd();
        GlStateManager.shadeModel(7424);

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawInformation(long now) {
        float entrance = getEntranceProgress();
        if (entrance <= 0.01F) {
            return;
        }

        float eased = 1.0F - (float) Math.pow(1.0F - entrance, 3.0D);
        int titleAlpha = Math.max(4, Math.round(255.0F * eased));
        int bodyAlpha = Math.max(4, Math.round(205.0F * eased));

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.contentMargin, this.contentMargin, 0.0F);
        float titleScale = calculateTitleScale(entrance);
        GlStateManager.scale(titleScale, titleScale, 1.0F);

        float toggleScale = getTitleToggleScale(now);
        if (toggleScale < 1.0F) {
            float titleCenterX = getTitleWidth() / 2.0F;
            float titleCenterY = this.titleFont.getFontHeight() / 2.0F;
            GlStateManager.translate(titleCenterX, titleCenterY, 0.0F);
            GlStateManager.scale(toggleScale, toggleScale, 1.0F);
            GlStateManager.translate(-titleCenterX, -titleCenterY, 0.0F);
        }
        drawTitle(titleAlpha);
        GlStateManager.popMatrix();

        float elapsedSeconds = Math.max(0L, now - this.openedAt) / 1_000_000_000.0F;
        float changelogEntrance = MiauMenuButton.calculateEntranceProgress(
                elapsedSeconds,
                this.changelogAnimationOrder);
        drawChangelogPreview(bodyAlpha, changelogEntrance);
    }

    private void drawTitle(int alpha) {
        if (!this.titleLogoVisible) {
            this.titleFont.drawString(TITLE_TEXT, 0.0F, 0.0F,
                    alpha << 24 | 0x00FFFFFF);
            return;
        }

        int logoHeight = this.titleFont.getFontHeight();
        int logoWidth = calculateTitleLogoWidth(logoHeight);
        this.mc.getTextureManager().bindTexture(TITLE_LOGO);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        Gui.drawScaledCustomSizeModalRect(
                0,
                0,
                0.0F,
                0.0F,
                TITLE_LOGO_TEXTURE_WIDTH,
                TITLE_LOGO_TEXTURE_HEIGHT,
                logoWidth,
                logoHeight,
                TITLE_LOGO_TEXTURE_WIDTH,
                TITLE_LOGO_TEXTURE_HEIGHT);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawChangelogPreview(int alpha, float entrance) {
        ClientChangelogOverview.Snapshot snapshot = ClientChangelogOverview.get();
        int lineHeight = this.smallFont.getLineHeight() + 1;
        ClientChangelogEntry release = snapshot.getPreviewRelease();
        int lineCount = release == null
                ? 1
                : 1 + snapshot.getPreviewLines().size();
        this.changelogHeight = lineHeight * lineCount;

        if (entrance <= 0.0F) {
            return;
        }

        float centerX = this.contentMargin + this.changelogWidth / 2.0F;
        float centerY = this.changelogTop + this.changelogHeight / 2.0F;
        float scale = 0.97F + entrance * 0.03F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(
                centerX,
                centerY + (1.0F - entrance) * 4.0F,
                0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);

        float y = this.changelogTop;

        if (release == null) {
            String message = snapshot.isChecking()
                    ? "Fetching changelogs..."
                    : snapshot.isUnavailable()
                    ? "Unable to load changelogs."
                    : "No changelogs are available yet.";
            drawPreviewLine(message, y, alpha << 24 | 0x00FFFFFF);
            GlStateManager.popMatrix();
            return;
        }

        drawPreviewLine(formatReleaseName(release.getDisplayName()), y,
                alpha << 24 | 0x00FFFFFF);
        y += lineHeight;
        for (ClientChangelogLine line : snapshot.getPreviewLines()) {
            int color = alpha << 24 | (line.getType().getColor() & 0x00FFFFFF);
            drawPreviewLine("- " + line.getText(), y, color);
            y += lineHeight;
        }
        GlStateManager.popMatrix();
    }

    private void drawPreviewLine(String text, float y, int color) {
        this.smallFont.drawString(
                ellipsize(text, this.smallFont, this.changelogWidth),
                this.contentMargin,
                y,
                color,
                false);
    }

    private void drawFooter() {
        int footerY = this.height - this.contentMargin - this.smallFont.getFontHeight();
        String updateStatus = ClientChangelogOverview.get().getUpdateMessage();
        String credits = "Credits: Myau client, vnhao294, khoanguyen67, aftercares, vector3030";
        if (this.smallFont.getStringWidth(updateStatus)
                + this.smallFont.getStringWidth(credits)
                + this.contentMargin * 2 + CONTENT_COLUMN_GAP > this.width) {
            credits = "Credits: Myau Client";
        }

        int availableStatusWidth = this.width
                - this.contentMargin * 2
                - this.smallFont.getStringWidth(credits)
                - CONTENT_COLUMN_GAP;
        updateStatus = ellipsize(
                updateStatus,
                this.smallFont,
                Math.max(1, availableStatusWidth));
        this.smallFont.drawString(
                updateStatus, this.contentMargin, footerY, 0xD0FFFFFF, false);
        this.smallFont.drawString(credits,
                this.width - this.contentMargin - this.smallFont.getStringWidth(credits),
                footerY,
                0xD0FFFFFF,
                false);
    }

    private float getEntranceProgress() {
        return clamp((System.nanoTime() - this.openedAt) / 450_000_000.0F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static int calculateContentMargin(int width, int height) {
        int availableWidth = Math.max(1, width);
        int availableHeight = Math.max(1, height);
        int referenceWidth = Math.min(
                availableWidth,
                Math.round(availableHeight * (16.0F / 9.0F)));
        return Math.max(
                CONTENT_MARGIN_MIN,
                Math.min(
                        CONTENT_MARGIN_MAX,
                        Math.round(referenceWidth * CONTENT_MARGIN_RATIO)));
    }

    static int calculateTitleLogoWidth(int logoHeight) {
        return Math.max(1, Math.round(
                Math.max(1, logoHeight)
                        * (TITLE_LOGO_TEXTURE_WIDTH / (float) TITLE_LOGO_TEXTURE_HEIGHT)));
    }

    static float calculateTitleScale(float entranceProgress) {
        float entrance = clamp(entranceProgress);
        float eased = 1.0F - (float) Math.pow(1.0F - entrance, 3.0D);
        return 1.0F + (1.0F - eased) * 0.12F;
    }

    static float calculateTitleToggleScale(float progress) {
        float clampedProgress = clamp(progress);
        float easedProgress = 1.0F
                - (float) Math.pow(1.0F - clampedProgress, 3.0D);
        return TITLE_TOGGLE_START_SCALE
                + (1.0F - TITLE_TOGGLE_START_SCALE) * easedProgress;
    }

    private float getTitleToggleScale(long now) {
        if (this.titleToggleAnimationStartedAt < 0L) {
            return 1.0F;
        }

        float progress = clamp(
                (now - this.titleToggleAnimationStartedAt)
                        / (float) TITLE_TOGGLE_ANIMATION_DURATION_NANOS);
        if (progress >= 1.0F) {
            this.titleToggleAnimationStartedAt = -1L;
            return 1.0F;
        }
        return calculateTitleToggleScale(progress);
    }

    static boolean isPointInsideTitle(
            int pointX,
            int pointY,
            int titleX,
            int titleY,
            int titleWidth,
            int titleHeight,
            float scale,
            int padding) {
        int safePadding = Math.max(0, padding);
        float safeScale = Math.max(0.0F, scale);
        return pointX >= titleX - safePadding
                && pointX < titleX + Math.max(1, titleWidth) * safeScale + safePadding
                && pointY >= titleY - safePadding
                && pointY < titleY + Math.max(1, titleHeight) * safeScale + safePadding;
    }

    private boolean isTitleClicked(int mouseX, int mouseY) {
        float entrance = getEntranceProgress();
        if (entrance <= 0.01F) {
            return false;
        }
        int titleWidth = getTitleWidth();
        return isPointInsideTitle(
                mouseX,
                mouseY,
                this.contentMargin,
                this.contentMargin,
                titleWidth,
                this.titleFont.getFontHeight(),
                calculateTitleScale(entrance),
                TITLE_HIT_PADDING);
    }

    private int getTitleWidth() {
        return this.titleLogoVisible
                ? calculateTitleLogoWidth(this.titleFont.getFontHeight())
                : this.titleFont.getStringWidth(TITLE_TEXT);
    }

    private void toggleTitle() {
        this.titleLogoVisible = !this.titleLogoVisible;
        this.titleToggleAnimationStartedAt = System.nanoTime();
        if (this.preferences != null) {
            this.preferences.saveLogoMode(this.titleLogoVisible
                    ? MainMenuPreferences.LogoMode.IMAGE
                    : MainMenuPreferences.LogoMode.TEXT);
        }
        playNextFriendlyCatSound();
    }

    private void playNextFriendlyCatSound() {
        if (this.activeCatSound != null) {
            this.mc.getSoundHandler().stopSound(this.activeCatSound);
        }

        ResourceLocation sound = FRIENDLY_CAT_SOUNDS[this.nextCatSoundIndex];
        this.nextCatSoundIndex = (this.nextCatSoundIndex + 1) % FRIENDLY_CAT_SOUNDS.length;
        this.activeCatSound = PositionedSoundRecord.create(sound);
        this.mc.getSoundHandler().playSound(this.activeCatSound);
    }

    static String ellipsize(String text, MiauFontRenderer font, int maximumWidth) {
        if (text == null || font == null || maximumWidth <= 0) {
            return "";
        }
        if (font.getStringWidth(text) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int textWidth = maximumWidth - font.getStringWidth(suffix);
        if (textWidth <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            result.append(text.charAt(index));
            if (font.getStringWidth(result.toString()) > textWidth) {
                result.setLength(result.length() - 1);
                break;
            }
        }
        return result.append(suffix).toString();
    }

    private static String formatReleaseName(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            return "Latest release";
        }
        return name.charAt(0) == 'v' || name.charAt(0) == 'V'
                ? name : "v" + name;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (getBootTransitionProgress(System.nanoTime()) >= 1.0F) {
            if (mouseButton == 0 && isTitleClicked(mouseX, mouseY)) {
                toggleTitle();
                return;
            }
            if (mouseButton == 0
                    && mouseX >= this.contentMargin
                    && mouseX < this.contentMargin + this.changelogWidth
                    && mouseY >= this.changelogTop
                    && mouseY < this.changelogTop + this.changelogHeight) {
                this.mc.displayGuiScreen(new GuiChangelogs(this));
                return;
            }
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
