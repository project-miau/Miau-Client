package idle.miau.clickgui.modern;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.clickgui.GuiCategories;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.client.ClickGui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MiauClickGui extends GuiScreen {
    private final MiauRenderer renderer = new MiauRenderer();
    private final List<Column> columns = new ArrayList<>();
    private final Map<Module, MiauModuleRow> rows = new IdentityHashMap<>();
    private final MiauConfigPanel configPanel = new MiauConfigPanel(this);
    private final MiauScriptPanel scriptPanel = new MiauScriptPanel(this);
    private final MiauAnimation pageOpen = new MiauAnimation(180, MiauAnimation.Curve.SINE);
    private final MiauAnimation tabIndicator = new MiauAnimation(220, MiauAnimation.Curve.EXPO);
    private final Map<Tab, TabTransition> tabTransitions = new EnumMap<>(Tab.class);
    private Tab selectedTab = Tab.CLICK_GUI;
    private MiauSetting focused;
    private MiauModuleRow binding;
    private float renderScale = 1;
    private float logicalHeight;
    private boolean closing;
    private boolean showingBinds;

    public MiauClickGui() {
        for (Tab tab : Tab.values()) tabTransitions.put(tab, new TabTransition());
        resetTabs();
        String[] icons = {"\ue9e0", "\ue566", "\ue7fd", "\ue8f4", "\ue30f", "\ue5d3"};
        for (int index = 0; index < GuiCategories.modules().size(); index++) {
            columns.add(new Column(GuiCategories.title(GuiCategories.modules().get(index)), icons[index], index));
        }
    }

    @Override
    public void initGui() {
        renderScale = scale(width, ClickGui.getClickGuiScale());
        clearFocus();
        binding = null;
        closing = false;
        resetTabs();
        refreshModules();
        columns.forEach(column -> {
            column.open.reset(0);
            column.open.to(1);
        });
        pageOpen.reset(0);
        pageOpen.to(1);
        openTab();
        Keyboard.enableRepeatEvents(true);
    }

    public void refreshScriptModules() {
        refreshModules();
        scriptPanel.refresh();
    }

    public void refreshConfigModules() {
        refreshModules();
        configPanel.refreshLocal();
    }

    private void refreshModules() {
        if (Miau.moduleManager == null) return;
        List<Module> modules = new ArrayList<>(Miau.moduleManager.getModules());
        if (Miau.configManager != null) Miau.configManager.configs.forEach(config -> modules.add(config.getModule()));
        rows.entrySet().removeIf(entry -> {
            if (modules.contains(entry.getKey())) return false;
            entry.getValue().settings.cancel();
            if (binding == entry.getValue()) binding = null;
            return true;
        });
        columns.forEach(column -> column.modules.clear());
        for (Module.category category : GuiCategories.modules()) {
            Column column = columns.get(categoryIndex(category));
            for (Module module : GuiCategories.moduleEntries(category)) {
                column.modules.add(row(module));
            }
        }
    }

    static int categoryIndex(Module.category category) {
        return GuiCategories.modules().indexOf(category);
    }

    static float scale(int width, float configured) {
        return Math.max(.1f, Math.min(configured, (width - 20) / MiauLayout.WIDTH));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        renderScale = scale(width, ClickGui.getClickGuiScale());
        float logicalWidth = width / renderScale;
        logicalHeight = height / renderScale;
        float pointerX = closing ? -1 : mouseX / renderScale;
        float pointerY = closing ? -1 : mouseY / renderScale;
        showingBinds = Keyboard.isKeyDown(Keyboard.KEY_TAB);
        renderer.begin(logicalWidth, logicalHeight);
        try {
            for (Tab tab : Tab.values()) {
                if (tab != selectedTab) drawTab(tab, logicalWidth, -1, -1);
            }
            drawTab(selectedTab, logicalWidth, pointerX, pointerY);
            drawTabs(logicalWidth, pointerX, pointerY);
        } finally {
            renderer.end();
        }
        Column last = columns.get(columns.size() - 1);
        boolean finished = selectedTab == Tab.CLICK_GUI ? last.open.finished() && last.open.value() == 0
                : pageOpen.finished() && pageOpen.value() == 0;
        if (closing && finished) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        }
    }

    private void drawTab(Tab tab, float logicalWidth, float mouseX, float mouseY) {
        TabTransition transition = tabTransitions.get(tab);
        float alpha = transition.alpha.value();
        if (alpha <= 0.001f) return;
        float offset = transition.offset.value();
        renderer.layer(offset, 0, alpha, () -> {
            if (tab == Tab.CLICK_GUI) {
                float startX = MiauLayout.left(logicalWidth);
                for (int index = 0; index < columns.size(); index++) {
                    Column column = columns.get(index);
                    column.x = startX + index * (MiauLayout.COLUMN_WIDTH + MiauLayout.COLUMN_GAP);
                    column.draw(mouseX < 0 ? -1 : mouseX - offset, mouseY);
                }
            } else {
                renderer.alpha(pageOpen.to(closing ? 0 : 1));
                panel(tab).draw(renderer, mouseX < 0 ? -1 : mouseX - offset, mouseY, logicalWidth, logicalHeight);
                renderer.alpha(1);
            }
        });
    }

    private void resetTabs() {
        for (Tab tab : Tab.values()) {
            TabTransition transition = tabTransitions.get(tab);
            transition.alpha.reset(tab == selectedTab ? 1 : 0);
            transition.offset.reset(0);
        }
        tabIndicator.reset(selectedTab.ordinal());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (closing) return;
        if (binding != null) {
            assignBind(1000 + button);
            return;
        }
        if (focused instanceof MiauKey) {
            focused.key('\0', 1000 + button);
            return;
        }
        clearFocus();
        float pointerX = mouseX / renderScale;
        float pointerY = mouseY / renderScale;
        if (button == 0) {
            Tab tab = tabAt(width / renderScale, pointerX, pointerY);
            if (tab != null) {
                selectTab(tab);
                return;
            }
        }
        TabTransition transition = tabTransitions.get(selectedTab);
        if (transition.alpha.value() <= 0.1f) return;
        pointerX -= transition.offset.value();
        if (selectedTab != Tab.CLICK_GUI) {
            activePanel().click(pointerX, pointerY, button);
            return;
        }
        for (Column column : columns) {
            if (column.contains(pointerX, pointerY)) {
                column.click(pointerX, pointerY, button);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) rows.values().forEach(row -> row.settings.release());
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || closing) return;
        if (binding != null) {
            assignBind(wheel > 0 ? 1069 : 1070);
            return;
        }
        float mouseX = ClickGuiInput.eventMouseX(Mouse.getEventX(), mc.displayWidth, width) / renderScale;
        float mouseY = ClickGuiInput.eventMouseY(Mouse.getEventY(), mc.displayHeight, height) / renderScale;
        scroll(mouseX, mouseY, ClickGuiInput.scrollSteps(wheel));
    }

    void scroll(float mouseX, float mouseY, float steps) {
        TabTransition transition = tabTransitions.get(selectedTab);
        if (transition.alpha.value() <= 0.1f) return;
        mouseX -= transition.offset.value();
        if (selectedTab != Tab.CLICK_GUI) {
            activePanel().scroll(mouseX, mouseY, steps);
            return;
        }
        for (Column column : columns) {
            if (column.contains(mouseX, mouseY)) {
                column.scrollTarget = Math.max(-column.maxOffset(), Math.min(0, column.scrollTarget + steps * 50));
                column.scroll.to(column.scrollTarget);
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char character, int code) {
        if (closing) return;
        if (binding != null) {
            assignBind(code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_DELETE ? 0 : code);
            return;
        }
        if (focused != null && focused.key(character, code)) return;
        Module clickGui = Miau.moduleManager == null ? null : ModuleManager.getModule(ClickGui.class);
        if (code == Keyboard.KEY_ESCAPE || clickGui != null && code == clickGui.getKeycode()) {
            close();
        }
    }

    void close() {
        clearFocus();
        closing = true;
        rows.values().forEach(row -> row.settings.cancel());
        columns.forEach(column -> column.open.to(0));
        pageOpen.to(0);
    }

    @Override
    public void updateScreen() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings == null) return;
        boolean movement = !closing && !isCapturingInput() && ClickGui.miauMovement != null && ClickGui.miauMovement.isToggled();
        for (KeyBinding key : movementKeys()) {
            int code = key.getKeyCode();
            boolean pressed = movement && (code > 0 && code < 256 ? Keyboard.isKeyDown(code)
                    : code < 0 && code + 100 >= 0 && Mouse.isButtonDown(code + 100));
            KeyBinding.setKeyBindState(code, pressed);
        }
    }

    private KeyBinding[] movementKeys() {
        return new KeyBinding[]{mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSprint};
    }

    @Override
    public void onGuiClosed() {
        clearFocus();
        binding = null;
        rows.values().forEach(row -> row.settings.cancel());
        Keyboard.enableRepeatEvents(false);
        KeyBinding.unPressAllKeys();
        configPanel.cancel();
        scriptPanel.cancel();
        renderer.releaseBackground();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    public double getRenderScale() { return renderScale; }

    public boolean isCapturingInput() { return focused != null || binding != null; }

    public boolean allowsMovement() {
        return !closing && !isCapturingInput() && ClickGui.miauMovement != null && ClickGui.miauMovement.isToggled();
    }

    void focus(MiauSetting setting) {
        clearFocus();
        focused = setting;
    }

    boolean focused(MiauSetting setting) { return focused == setting; }

    void unfocus(MiauSetting setting) {
        if (focused == setting) focused = null;
    }

    private void clearFocus() {
        MiauSetting previous = focused;
        focused = null;
        if (previous != null) previous.cancel();
    }

    private void assignBind(int code) {
        binding.module.setBind(code);
        binding = null;
        Miau.markCurrentConfigUnsaved();
    }

    static String keyName(int code) {
        if (code == 0) return "None";
        if (code == 1069) return "Wheel Up";
        if (code == 1070) return "Wheel Down";
        if (code >= 1000) return "Mouse " + (code - 999);
        String name = Keyboard.getKeyName(code);
        return name == null ? "None" : name;
    }

    MiauModuleRow row(Module module) {
        return rows.computeIfAbsent(module, value -> new MiauModuleRow(value, this, renderer));
    }

    void bind(MiauModuleRow row) {
        clearFocus();
        binding = row;
    }

    boolean isBinding(MiauModuleRow row) { return binding == row; }

    boolean showingBinds() { return showingBinds; }

    Tab selectedTab() { return selectedTab; }

    void selectTab(Tab tab) {
        if (tab == selectedTab) return;
        clearFocus();
        binding = null;
        rows.values().forEach(row -> row.settings.cancel());
        configPanel.cancel();
        scriptPanel.cancel();
        int direction = Integer.compare(tab.ordinal(), selectedTab.ordinal());
        for (Tab page : Tab.values()) {
            TabTransition transition = tabTransitions.get(page);
            if (page == tab) {
                if (transition.alpha.value() <= 0.001f) transition.offset.reset(direction * 18);
                transition.alpha.to(1);
                transition.offset.to(0);
            } else {
                transition.alpha.to(0);
                transition.offset.to(-direction * 18);
            }
        }
        selectedTab = tab;
        tabIndicator.to(tab.ordinal());
        openTab();
    }

    private void openTab() {
        if (selectedTab == Tab.CONFIG) configPanel.open();
        if (selectedTab == Tab.SCRIPT) {
            if (Miau.scriptManager != null && Miau.scriptManager.scripts.isEmpty()) {
                Miau.scriptManager.loadScripts();
            }
            scriptPanel.open();
        }
    }

    private MiauPanel activePanel() {
        return panel(selectedTab);
    }

    private MiauPanel panel(Tab tab) {
        return tab == Tab.CONFIG ? configPanel : scriptPanel;
    }

    private void drawTabs(float logicalWidth, float mouseX, float mouseY) {
        float x = MiauLayout.tabLeft(logicalWidth);
        float alpha = closing ? selectedTab == Tab.CLICK_GUI ? columns.get(0).open.value() : pageOpen.value() : 1;
        renderer.alpha(alpha);
        renderer.glass(x, MiauLayout.TAB_TOP, MiauLayout.TAB_WIDTH * 3, MiauLayout.TAB_HEIGHT, 5, 5);
        renderer.rect(x, MiauLayout.TAB_TOP, MiauLayout.TAB_WIDTH * 3, MiauLayout.TAB_HEIGHT, 5, 0xd90f0f0f);
        float indicator = x + tabIndicator.value() * MiauLayout.TAB_WIDTH;
        renderer.gradient(indicator + 2, MiauLayout.TAB_TOP + 2, MiauLayout.TAB_WIDTH - 4, MiauLayout.TAB_HEIGHT - 4, 4, 4,
                MiauRenderer.opacity(MiauRenderer.primary(), .4f),
                MiauRenderer.opacity(MiauRenderer.secondary(), .4f), false);
        for (Tab tab : Tab.values()) {
            float left = x + tab.ordinal() * MiauLayout.TAB_WIDTH;
            boolean selected = selectedTab == tab;
            boolean hovered = MiauSetting.inside(mouseX, mouseY, left, MiauLayout.TAB_TOP,
                    MiauLayout.TAB_WIDTH, MiauLayout.TAB_HEIGHT);
            if (!selected && hovered) renderer.rect(left + 2, MiauLayout.TAB_TOP + 2, MiauLayout.TAB_WIDTH - 4,
                    MiauLayout.TAB_HEIGHT - 4, 4, 0x20ffffff);
            String font = selected ? MiauRenderer.BOLD : MiauRenderer.MEDIUM;
            int color = selected ? -1 : 0xffaaaaaa;
            renderer.text(MiauRenderer.OUTLINED, tab.icon, left + 12, MiauLayout.TAB_TOP + 16, 10, color);
            renderer.text(font, tab.title, left + 28, MiauLayout.TAB_TOP + 15, 9, color);
        }
        renderer.alpha(1);
    }

    static Tab tabAt(float logicalWidth, float mouseX, float mouseY) {
        float left = MiauLayout.tabLeft(logicalWidth);
        if (!MiauSetting.inside(mouseX, mouseY, left, MiauLayout.TAB_TOP,
                MiauLayout.TAB_WIDTH * 3, MiauLayout.TAB_HEIGHT)) return null;
        return Tab.values()[(int) ((mouseX - left) / MiauLayout.TAB_WIDTH)];
    }

    enum Tab {
        CLICK_GUI("ClickGUI", "\ue8f0"), CONFIG("Config", "\ue8b8"), SCRIPT("Script", "\ue86f");

        final String title;
        final String icon;

        Tab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final class TabTransition {
        final MiauAnimation alpha = new MiauAnimation(200, MiauAnimation.Curve.SINE);
        final MiauAnimation offset = new MiauAnimation(220, MiauAnimation.Curve.EXPO);
    }

    private final class Column {
        final String name;
        final String icon;
        final List<MiauModuleRow> modules = new ArrayList<>();
        final MiauAnimation open;
        final MiauAnimation scroll = new MiauAnimation(250, MiauAnimation.Curve.EXPO);
        float x;
        float totalHeight;
        float clipHeight;
        float scrollTarget;

        Column(String name, String icon, int index) {
            this.name = name;
            this.icon = icon;
            open = new MiauAnimation(140 + index * 45L, MiauAnimation.Curve.SINE);
        }

        void draw(float mouseX, float mouseY) {
            totalHeight = 0;
            for (MiauModuleRow row : modules) {
                row.width = MiauLayout.COLUMN_WIDTH;
                totalHeight += row.height() + MiauLayout.ROW_GAP;
            }
            if (!modules.isEmpty()) totalHeight -= MiauLayout.ROW_GAP;
            float progress = open.to(closing ? 0 : 1);
            float available = Math.max(0, logicalHeight - MiauLayout.LIST_TOP - 16);
            clipHeight = Math.min(available, totalHeight) * progress;
            float bounded = Math.max(-maxOffset(), Math.min(0, scrollTarget));
            if (scrollTarget != bounded) {
                scrollTarget = bounded;
                scroll.to(bounded);
            }
            float offset = Math.max(-maxOffset(), Math.min(0, scroll.value()));
            renderer.alpha(progress);
            renderer.glass(x, MiauLayout.COLUMN_TOP, MiauLayout.COLUMN_WIDTH, MiauLayout.HEADER_HEIGHT, 5, 5);
            renderer.rect(x, MiauLayout.COLUMN_TOP, MiauLayout.COLUMN_WIDTH, MiauLayout.HEADER_HEIGHT, 5, 0xd90f0f0f);
            renderer.text(MiauRenderer.BOLD, name, x + 5, MiauLayout.COLUMN_TOP + 13, 9, -1);
            renderer.text(MiauRenderer.OUTLINED, icon, x + MiauLayout.COLUMN_WIDTH - 15.5f,
                    MiauLayout.COLUMN_TOP + 15, 10, -1);
            renderer.clip(x, MiauLayout.LIST_TOP, MiauLayout.COLUMN_WIDTH, clipHeight, () -> {
                float nextY = MiauLayout.LIST_TOP + offset;
                for (MiauModuleRow row : modules) {
                    row.x = x;
                    row.y = nextY;
                    row.draw(mouseX, mouseY);
                    nextY += row.renderedHeight + MiauLayout.ROW_GAP;
                }
            });
            renderer.alpha(1);
        }

        float maxOffset() {
            return Math.max(0, totalHeight - Math.max(0, logicalHeight - MiauLayout.LIST_TOP - 16));
        }

        boolean contains(float mouseX, float mouseY) {
            return MiauSetting.inside(mouseX, mouseY, x, MiauLayout.LIST_TOP, MiauLayout.COLUMN_WIDTH, clipHeight);
        }

        void click(float mouseX, float mouseY, int button) {
            for (MiauModuleRow row : modules) {
                if (MiauSetting.inside(mouseX, mouseY, row.x, row.y, row.width, row.renderedHeight)) {
                    row.click(mouseX, mouseY, button);
                    return;
                }
            }
        }
    }

}
