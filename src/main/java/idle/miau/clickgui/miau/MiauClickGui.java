package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.module.Module;
import idle.miau.module.impl.client.ClickGui;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MiauClickGui extends GuiScreen {
    private static final int FRAME_WIDTH = 118;
    private static final int FRAME_TOP = 34;
    private static final int ROW_GAP = 155;
    private static final int COL_GAP = 6;
    private static final float AUTO_LAYOUT_GAP = 5.0F;
    private static final int TAB_TOP = 5;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_PADDING = 9;
    private static final int TAB_ICON_SIZE = 9;
    private static final int TAB_ICON_GAP = 4;

    private static final Module.category[] ORDERED_CATEGORIES = new Module.category[] {
            Module.category.combat,
            Module.category.movement,
            Module.category.player,
            Module.category.render,
            Module.category.world,
            Module.category.minigames,
            Module.category.bedwar_utils,
            Module.category.other
    };

    private final List<MiauCategoryFrame> frames = new ArrayList<MiauCategoryFrame>();
    private final MiauConfigWindow configWindow = new MiauConfigWindow();
    private final MiauScriptWindow scriptWindow = new MiauScriptWindow();
    private Tab selectedTab = Tab.CLICK_GUI;
    private long openingStarted;
    private boolean laidOut;

    public MiauClickGui() {
        for (Module.category category : ORDERED_CATEGORIES) {
            frames.add(new MiauCategoryFrame(category, FRAME_WIDTH));
        }
    }

    @Override
    public void initGui() {
        int screenWidth = logicalWidth();
        int screenHeight = logicalHeight();
        if (!laidOut) {
            layoutFrames(screenWidth, screenHeight);
            laidOut = true;
        }
        else {
            keepFramesOnScreen(screenWidth, screenHeight);
        }
        for (MiauCategoryFrame frame : frames) {
            frame.syncModules();
            frame.stopDragging();
        }
        if (selectedTab == Tab.CONFIGS) {
            configWindow.open(screenWidth, screenHeight);
        }
        else if (selectedTab == Tab.SCRIPTS) {
            openScriptsTab(screenWidth, screenHeight);
        }
        openingStarted = System.currentTimeMillis();
        super.initGui();
    }

    public void refreshScriptModules() {
        for (MiauCategoryFrame frame : frames) {
            if (frame.getCategory() == Module.category.scripts) {
                frame.syncModules();
            }
        }
        if (scriptWindow != null) {
            scriptWindow.syncModules();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float scale = getRenderScale();
        int logicalMouseX = logicalCoordinate(mouseX, scale);
        int logicalMouseY = logicalCoordinate(mouseY, scale);
        int screenWidth = logicalSize(width, scale);
        int screenHeight = logicalSize(height, scale);
        float progress = Math.min(1.0F, (System.currentTimeMillis() - openingStarted) / 125.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0F);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        try {
            if (selectedTab == Tab.CLICK_GUI) {
                keepFramesOnScreen(screenWidth, screenHeight);
                for (MiauCategoryFrame frame : frames) {
                    frame.update(logicalMouseX, logicalMouseY, screenHeight);
                }
                updateAutoLayout();
                for (MiauCategoryFrame frame : getRenderOrder()) {
                    frame.draw(logicalMouseX, logicalMouseY, eased);
                }
            }
            else if (selectedTab == Tab.CONFIGS) {
                configWindow.draw(logicalMouseX, logicalMouseY, screenWidth, screenHeight);
            }
            else {
                scriptWindow.draw(logicalMouseX, logicalMouseY, screenWidth, screenHeight);
            }
            super.drawScreen(logicalMouseX, logicalMouseY, partialTicks);
            drawTabs(logicalMouseX, logicalMouseY, screenWidth);
        }
        finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        float scale = getRenderScale();
        mouseX = logicalCoordinate(mouseX, scale);
        mouseY = logicalCoordinate(mouseY, scale);
        if (mouseButton == 0) {
            Tab clickedTab = getTabAt(mouseX, mouseY, logicalSize(width, scale));
            if (clickedTab != null) {
                boolean sameTab = clickedTab == selectedTab;
                selectTab(clickedTab);
                if (clickedTab == Tab.SCRIPTS && sameTab) {
                    openScriptsTab(logicalSize(width, scale), logicalSize(height, scale));
                }
                return;
            }
        }
        if (selectedTab == Tab.CLICK_GUI) {
            List<MiauCategoryFrame> inputOrder = getRenderOrder();
            Collections.reverse(inputOrder);
            for (MiauCategoryFrame frame : inputOrder) {
                if (frame.mouseClicked(mouseX, mouseY, mouseButton)) {
                    frame.bringToFront();
                    return;
                }
            }
            for (MiauCategoryFrame frame : frames) {
                frame.clearTextFocus();
            }
        }
        else if (selectedTab == Tab.CONFIGS) {
            if (configWindow.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
        else if (scriptWindow.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        float scale = getRenderScale();
        mouseX = logicalCoordinate(mouseX, scale);
        mouseY = logicalCoordinate(mouseY, scale);
        boolean frameMoved = false;
        if (selectedTab == Tab.CLICK_GUI) {
            for (MiauCategoryFrame frame : frames) {
                frameMoved |= frame.mouseReleased(mouseX, mouseY, state);
            }
        }
        if (frameMoved) {
            Miau.markCurrentConfigUnsaved();
        }
        if (selectedTab == Tab.SCRIPTS) {
            scriptWindow.mouseReleased(mouseX, mouseY, state);
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (selectedTab == Tab.CLICK_GUI) {
            for (MiauCategoryFrame frame : getInputOrder()) {
                if (frame.keyTyped(typedChar, keyCode)) {
                    return;
                }
            }
        } else if (selectedTab == Tab.SCRIPTS) {
            if (scriptWindow.keyTyped(typedChar, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = ClickGuiInput.eventMouseX(Mouse.getEventX(), mc.displayWidth, width);
        int mouseY = ClickGuiInput.eventMouseY(Mouse.getEventY(), mc.displayHeight, height);
        float scale = getRenderScale();
        mouseX = logicalCoordinate(mouseX, scale);
        mouseY = logicalCoordinate(mouseY, scale);
        if (selectedTab == Tab.CONFIGS) {
            configWindow.mouseWheel(mouseX, mouseY, wheel);
            return;
        }
        if (selectedTab == Tab.SCRIPTS) {
            scriptWindow.mouseWheel(mouseX, mouseY, wheel);
            return;
        }
        for (MiauCategoryFrame frame : getInputOrder()) {
            if (frame.mouseWheel(mouseX, mouseY, wheel)) {
                return;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        configWindow.close();
        scriptWindow.onGuiClosed();
        scriptWindow.close();
        for (MiauCategoryFrame frame : frames) {
            frame.onGuiClosed();
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public List<MiauCategoryFrame> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    public boolean hasLayout() {
        return laidOut;
    }

    public void applyFrameState(Module.category category, int x, int y, boolean opened) {
        for (MiauCategoryFrame frame : frames) {
            if (frame.getCategory() == category) {
                frame.applyState(x, y, opened);
                laidOut = true;
                return;
            }
        }
    }

    private void layoutFrames(int screenWidth, int screenHeight) {
        int totalWideW = 8 * FRAME_WIDTH + 7 * COL_GAP;
        if (screenWidth >= totalWideW + 20) {
            int startX = Math.max(10, (screenWidth - totalWideW) / 2);
            for (int i = 0; i < frames.size(); i++) {
                MiauCategoryFrame frame = frames.get(i);
                frame.setPosition(startX + i * (FRAME_WIDTH + COL_GAP), FRAME_TOP);
                frame.clampToScreen(screenWidth, screenHeight);
            }
            return;
        }

        int cols = screenWidth >= 4 * FRAME_WIDTH + 3 * COL_GAP + 16 ? 4 : 2;
        int totalW = cols * FRAME_WIDTH + (cols - 1) * COL_GAP;
        int startX = Math.max(8, (screenWidth - totalW) / 2);
        for (int i = 0; i < frames.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (FRAME_WIDTH + COL_GAP);
            int y = FRAME_TOP + row * ROW_GAP;
            MiauCategoryFrame frame = frames.get(i);
            frame.setPosition(x, y);
            frame.clampToScreen(screenWidth, screenHeight);
        }
    }

    static int defaultFrameX(Module.category category) {
        int index = getCategoryIndex(category);
        int col = index % 4;
        return 16 + col * (FRAME_WIDTH + COL_GAP);
    }

    static int defaultFrameY(Module.category category) {
        int index = getCategoryIndex(category);
        int row = index / 4;
        return FRAME_TOP + row * ROW_GAP;
    }

    private static int getCategoryIndex(Module.category category) {
        for (int i = 0; i < ORDERED_CATEGORIES.length; i++) {
            if (ORDERED_CATEGORIES[i] == category) {
                return i;
            }
        }
        return 0;
    }

    private void updateAutoLayout() {
        int size = frames.size();
        float[] x = new float[size];
        float[] y = new float[size];
        float[] widths = new float[size];
        float[] heights = new float[size];
        boolean[] dragging = new boolean[size];

        for (int index = 0; index < size; index++) {
            MiauCategoryFrame frame = frames.get(index);
            x[index] = frame.getX();
            y[index] = frame.getY();
            widths[index] = frame.getWidth();
            heights[index] = frame.getAnimatedHeight();
            dragging[index] = frame.isDragging();
        }

        float[] resolvedY = MiauCategoryLayout.resolve(x, y, widths, heights, dragging, AUTO_LAYOUT_GAP);
        for (int index = 0; index < size; index++) {
            frames.get(index).setLayoutOffsetY(resolvedY[index] - y[index]);
        }
    }

    private void keepFramesOnScreen(int screenWidth, int screenHeight) {
        for (MiauCategoryFrame frame : frames) {
            frame.clampToScreen(screenWidth, screenHeight);
        }
    }

    private void drawTabs(int mouseX, int mouseY, int screenWidth) {
        MiauFontRenderer font = MiauStyle.moduleFont();
        int contentWidth = getTabContentWidth(font);
        int barX = (screenWidth - contentWidth - 4) / 2;
        int cursorX = barX + 2;
        RenderUtils.drawRoundedRectangle(
                barX,
                TAB_TOP,
                barX + contentWidth + 4,
                TAB_TOP + TAB_HEIGHT,
                5,
                MiauStyle.withAlpha(MiauStyle.DARK, 235));

        for (Tab tab : Tab.values()) {
            int tabWidth = getTabWidth(tab, font);
            boolean hovered = MiauStyle.hovered(cursorX, TAB_TOP + 2, tabWidth, TAB_HEIGHT - 4, mouseX, mouseY);
            if (tab == selectedTab) {
                int color = MiauStyle.withAlpha(MiauStyle.accent(cursorX + tabWidth * 0.5D, TAB_TOP + TAB_HEIGHT * 0.5D), 225);
                RenderUtils.drawRoundedRectangle(cursorX, TAB_TOP + 2, cursorX + tabWidth, TAB_TOP + TAB_HEIGHT - 2, 4, color);
            }
            else if (hovered) {
                RenderUtils.drawRoundedRectangle(cursorX, TAB_TOP + 2, cursorX + tabWidth, TAB_TOP + TAB_HEIGHT - 2, 4, MiauStyle.HOVER);
            }

            int color = tab == selectedTab ? MiauStyle.WHITE : MiauStyle.MUTED;
            int iconX = cursorX + TAB_PADDING;
            int iconY = TAB_TOP + (TAB_HEIGHT - TAB_ICON_SIZE) / 2;
            if (!MiauStyle.drawTexture(tab.icon, iconX, iconY, TAB_ICON_SIZE, TAB_ICON_SIZE, color)) {
                Gui.drawRect(iconX, iconY, iconX + TAB_ICON_SIZE, iconY + TAB_ICON_SIZE, color);
            }
            int textX = iconX + TAB_ICON_SIZE + TAB_ICON_GAP;
            int textY = MiauStyle.centeredTextY(TAB_TOP, TAB_HEIGHT, font);
            font.drawString(tab.title, textX, textY, color, true);
            cursorX += tabWidth;
        }
    }

    private Tab getTabAt(int mouseX, int mouseY, int screenWidth) {
        MiauFontRenderer font = MiauStyle.moduleFont();
        int cursorX = (screenWidth - getTabContentWidth(font) - 4) / 2 + 2;
        for (Tab tab : Tab.values()) {
            int tabWidth = getTabWidth(tab, font);
            if (MiauStyle.hovered(cursorX, TAB_TOP + 2, tabWidth, TAB_HEIGHT - 4, mouseX, mouseY)) {
                return tab;
            }
            cursorX += tabWidth;
        }
        return null;
    }

    private int getTabContentWidth(MiauFontRenderer font) {
        int total = 0;
        for (Tab tab : Tab.values()) {
            total += getTabWidth(tab, font);
        }
        return total;
    }

    private int getTabWidth(Tab tab, MiauFontRenderer font) {
        return TAB_PADDING * 2 + TAB_ICON_SIZE + TAB_ICON_GAP + font.getStringWidth(tab.title);
    }

    private void selectTab(Tab tab) {
        if (tab == selectedTab) {
            return;
        }
        if (selectedTab == Tab.SCRIPTS) {
            scriptWindow.clearTextFocus();
        }
        for (MiauCategoryFrame frame : frames) {
            frame.clearTextFocus();
            frame.stopDragging();
        }
        selectedTab = tab;
        if (selectedTab == Tab.CONFIGS) {
            configWindow.open(logicalWidth(), logicalHeight());
        }
        else if (selectedTab == Tab.SCRIPTS) {
            openScriptsTab(logicalWidth(), logicalHeight());
        }
    }

    private void openScriptsTab(int screenWidth, int screenHeight) {
        if (Miau.scriptManager != null && Miau.scriptManager.scripts.isEmpty()) {
            Miau.scriptManager.loadScripts();
        }
        scriptWindow.open(screenWidth, screenHeight);
    }

    static int logicalCoordinate(int coordinate, float scale) {
        return (int) Math.floor(coordinate / normalizedScale(scale));
    }

    static int logicalSize(int size, float scale) {
        return (int) Math.ceil(size / normalizedScale(scale));
    }

    private int logicalWidth() {
        return logicalSize(width, getRenderScale());
    }

    private int logicalHeight() {
        return logicalSize(height, getRenderScale());
    }

    public float getRenderScale() {
        return normalizedScale(ClickGui.getClickGuiScale());
    }

    private static float normalizedScale(float scale) {
        return Float.isFinite(scale) && scale > 0.0F ? scale : 1.0F;
    }

    private List<MiauCategoryFrame> getRenderOrder() {
        List<MiauCategoryFrame> order = new ArrayList<MiauCategoryFrame>(frames);
        order.sort(Comparator.comparingLong(MiauCategoryFrame::getInteractionOrder));
        return order;
    }

    private List<MiauCategoryFrame> getInputOrder() {
        List<MiauCategoryFrame> order = getRenderOrder();
        Collections.reverse(order);
        return order;
    }

    private enum Tab {
        CLICK_GUI("ClickGUI", "visuals"),
        CONFIGS("Configs", "script"),
        SCRIPTS("Scripts", "script");

        private final String title;
        private final String icon;

        Tab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }
}
