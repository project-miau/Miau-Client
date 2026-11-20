package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.config.Config;
import idle.miau.utility.config.online.OnlineConfigEntry;
import idle.miau.utility.config.online.OnlineConfigService;
import idle.miau.utility.config.online.OnlineConfigSource;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.gui.Gui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

final class MiauConfigWindow {
    private static final int WINDOW_WIDTH = 360;
    private static final int WINDOW_HEIGHT = 250;
    private static final int HEADER_HEIGHT = 22;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 18;
    private static final int PADDING = 5;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final OnlineConfigService onlineConfigs = new OnlineConfigService();
    private final List<LocalEntry> localEntries = new ArrayList<LocalEntry>();
    private final List<OnlineConfigEntry> officialEntries = new ArrayList<OnlineConfigEntry>();
    private final List<OnlineConfigEntry> userEntries = new ArrayList<OnlineConfigEntry>();
    private Tab selectedTab = Tab.OFFICIAL;
    private String officialStatus = "";
    private String userStatus = "";
    private String actionStatus = "Click a config to load";
    private int x;
    private int y;
    private int width;
    private int height;
    private int officialScroll;
    private int localScroll;
    private int userScroll;
    private int officialRequest;
    private int userRequest;
    private boolean officialLoaded;
    private boolean userLoaded;
    private boolean officialLoading;
    private boolean userLoading;
    private boolean loadingConfig;
    private boolean opened;

    void open(int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        boolean firstOpen = !opened;
        opened = true;
        refreshLocal();
        refreshOnlineIfNeeded(OnlineConfigSource.OFFICIAL);
        if (firstOpen) {
            refreshOnlineIfNeeded(OnlineConfigSource.USER);
        }
    }

    void close() {
        opened = false;
    }

    void draw(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        layout(screenWidth, screenHeight);
        MiauFontRenderer font = MiauStyle.moduleFont();
        int accent = MiauStyle.accent(x + width * 0.5D, y + height * 0.5D);
        RenderUtils.drawRoundedRectangle(x - 1, y - 1, x + width + 1, y + height + 1, 6,
                MiauStyle.withAlpha(accent, 220));
        RenderUtils.drawRoundedRectangle(x, y, x + width, y + height, 6,
                MiauStyle.withAlpha(MiauStyle.DARK, 245));

        String title = "Config Manager";
        font.drawString(title, x + (width - font.getStringWidth(title)) / 2,
                y + Math.max(3, (HEADER_HEIGHT - font.getFontHeight()) / 2), MiauStyle.WHITE, true);
        drawTabs(mouseX, mouseY, font);
        drawEntries(mouseX, mouseY, font);

        String footer = MiauStyle.trim(actionStatus, font, width - PADDING * 2);
        font.drawString(footer, x + PADDING, y + height - FOOTER_HEIGHT + 4,
                loadingConfig ? accent : MiauStyle.MUTED, true);
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !MiauStyle.hovered(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        int tabY = y + HEADER_HEIGHT;
        int tabWidth = (width - PADDING * 2) / Tab.values().length;
        for (int index = 0; index < Tab.values().length; index++) {
            int tabX = x + PADDING + index * tabWidth;
            if (MiauStyle.hovered(tabX, tabY, tabWidth, TAB_HEIGHT, mouseX, mouseY)) {
                selectTab(Tab.values()[index]);
                return true;
            }
        }

        int listTop = getListTop();
        int listBottom = getListBottom();
        if (mouseY < listTop || mouseY > listBottom) {
            return true;
        }
        int index = (mouseY - listTop + getScroll()) / ROW_HEIGHT;
        if (selectedTab == Tab.LOCAL) {
            if (index >= 0 && index < localEntries.size()) {
                loadLocal(localEntries.get(index));
            }
            return true;
        }
        List<OnlineConfigEntry> entries = getOnlineEntries(selectedTab.source);
        if (index >= 0 && index < entries.size()) {
            loadOnline(entries.get(index), selectedTab.source);
        }
        return true;
    }

    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (wheel == 0 || !MiauStyle.hovered(x, getListTop(), width,
                getListBottom() - getListTop(), mouseX, mouseY)) {
            return false;
        }
        int next = getScroll() - Math.round(ClickGuiInput.scrollPixels(wheel, ROW_HEIGHT));
        setScroll(Math.max(0, Math.min(next, getMaxScroll())));
        return true;
    }

    private void drawTabs(int mouseX, int mouseY, MiauFontRenderer font) {
        int tabY = y + HEADER_HEIGHT;
        int tabWidth = (width - PADDING * 2) / Tab.values().length;
        for (int index = 0; index < Tab.values().length; index++) {
            Tab tab = Tab.values()[index];
            int tabX = x + PADDING + index * tabWidth;
            boolean selected = tab == selectedTab;
            boolean hovered = MiauStyle.hovered(tabX, tabY, tabWidth, TAB_HEIGHT, mouseX, mouseY);
            if (selected) {
                int accent = MiauStyle.accent(tabX + tabWidth * 0.5D, tabY + TAB_HEIGHT * 0.5D);
                RenderUtils.drawRoundedRectangle(tabX, tabY, tabX + tabWidth, tabY + TAB_HEIGHT, 3,
                        MiauStyle.withAlpha(accent, 205));
            }
            else if (hovered) {
                Gui.drawRect(tabX, tabY, tabX + tabWidth, tabY + TAB_HEIGHT, MiauStyle.HOVER);
            }
            int color = selected ? MiauStyle.WHITE : MiauStyle.MUTED;
            font.drawString(tab.title, tabX + (tabWidth - font.getStringWidth(tab.title)) / 2,
                    tabY + Math.max(2, (TAB_HEIGHT - font.getFontHeight()) / 2), color, true);
        }
    }

    private void drawEntries(int mouseX, int mouseY, MiauFontRenderer font) {
        int listTop = getListTop();
        int listBottom = getListBottom();
        int listWidth = width - PADDING * 2;
        RenderUtils.scissorPushGui(x + PADDING, listTop, listWidth, listBottom - listTop);
        int rowY = listTop - getScroll();
        if (selectedTab == Tab.LOCAL) {
            for (LocalEntry entry : localEntries) {
                drawLocalEntry(entry, x + PADDING, rowY, listWidth, mouseX, mouseY, font);
                rowY += ROW_HEIGHT;
            }
        }
        else {
            List<OnlineConfigEntry> entries = getOnlineEntries(selectedTab.source);
            String status = getOnlineStatus(selectedTab.source);
            if (!status.isEmpty()) {
                font.drawString(MiauStyle.trim(status, font, listWidth - 8), x + PADDING + 4,
                        listTop + 6, MiauStyle.MUTED, true);
            }
            else {
                for (OnlineConfigEntry entry : entries) {
                    drawOnlineEntry(entry, x + PADDING, rowY, listWidth, mouseX, mouseY, font);
                    rowY += ROW_HEIGHT;
                }
            }
        }
        RenderUtils.scissorPop();
    }

    private void drawLocalEntry(LocalEntry entry, int rowX, int rowY, int rowWidth,
                                int mouseX, int mouseY, MiauFontRenderer font) {
        boolean hovered = isRowHovered(rowX, rowY, rowWidth, mouseX, mouseY);
        int background = hovered ? 0xFF343434 : MiauStyle.FRAME;
        if (entry.current) {
            background = MiauStyle.withAlpha(MiauStyle.accent(rowX, rowY), hovered ? 150 : 105);
        }
        Gui.drawRect(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT - 2, background);
        int titleColor = entry.current ? MiauStyle.accent(rowX + rowWidth, rowY) : MiauStyle.WHITE;
        font.drawString(MiauStyle.trim(entry.name, font, rowWidth - 10), rowX + 5, rowY + 4,
                titleColor, true);
        String detail = entry.current ? "Current config" : "Last used: " + entry.modified;
        font.drawString(MiauStyle.trim(detail, font, rowWidth - 10), rowX + 5, rowY + 17,
                entry.current ? MiauStyle.WHITE : MiauStyle.MUTED, true);
    }

    private void drawOnlineEntry(OnlineConfigEntry entry, int rowX, int rowY, int rowWidth,
                                 int mouseX, int mouseY, MiauFontRenderer font) {
        boolean hovered = isRowHovered(rowX, rowY, rowWidth, mouseX, mouseY);
        Gui.drawRect(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT - 2,
                hovered ? 0xFF343434 : MiauStyle.FRAME);
        int accent = MiauStyle.accent(rowX + rowWidth, rowY);
        font.drawString(MiauStyle.trim(entry.getName(), font, rowWidth - 10), rowX + 5, rowY + 4,
                hovered ? accent : MiauStyle.WHITE, true);
        String detail = "by " + entry.getAuthor() + " | " + entry.getDownloads() + " loads";
        if (selectedTab == Tab.OFFICIAL && !entry.getType().isEmpty()) {
            detail += " | " + entry.getType();
        }
        font.drawString(MiauStyle.trim(detail, font, rowWidth - 10), rowX + 5, rowY + 17,
                MiauStyle.MUTED, true);
    }

    private boolean isRowHovered(int rowX, int rowY, int rowWidth, int mouseX, int mouseY) {
        return rowY >= getListTop() - ROW_HEIGHT && rowY < getListBottom()
                && MiauStyle.hovered(rowX, rowY, rowWidth, ROW_HEIGHT - 2, mouseX, mouseY)
                && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        if (tab == Tab.LOCAL) {
            refreshLocal();
        }
        else {
            refreshOnlineIfNeeded(tab.source);
        }
        setScroll(Math.min(getScroll(), getMaxScroll()));
    }

    private void refreshOnlineIfNeeded(OnlineConfigSource source) {
        if (shouldRefreshOnline(source, isLoaded(source), isLoading(source))) {
            refreshOnline(source);
        }
    }

    static boolean shouldRefreshOnline(OnlineConfigSource source, boolean loaded, boolean loading) {
        return !loading && (source == OnlineConfigSource.OFFICIAL || !loaded);
    }

    private void refreshLocal() {
        localEntries.clear();
        if (Miau.configManager == null) {
            return;
        }
        String current = Miau.currentConfig == null ? "" : Miau.currentConfig.getName();
        for (Config config : Miau.configManager.configs) {
            File file = new File(Miau.configManager.directory, config.getName() + ".json");
            String modified = file.isFile() ? DATE_FORMAT.format(new Date(file.lastModified())) : "unknown";
            localEntries.add(new LocalEntry(config.getName(), modified,
                    config.getName().equalsIgnoreCase(current)));
        }
        localEntries.sort(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        localScroll = Math.min(localScroll, getMaxScroll(Tab.LOCAL));
    }

    private void refreshOnline(OnlineConfigSource source) {
        setLoading(source, true);
        setLoaded(source, false);
        setOnlineStatus(source, "Loading " + source.getPath() + " configs...");
        getOnlineEntries(source).clear();
        final int request = nextRequest(source);
        onlineConfigs.list(
                source,
                result -> {
                    if (request != getRequest(source)) {
                        return;
                    }
                    List<OnlineConfigEntry> entries = getOnlineEntries(source);
                    entries.clear();
                    entries.addAll(result);
                    entries.sort(Comparator.comparing(OnlineConfigEntry::getName,
                            String.CASE_INSENSITIVE_ORDER));
                    setOnlineStatus(source, entries.isEmpty() ? "No configs found" : "");
                    setLoading(source, false);
                    setLoaded(source, true);
                    setScroll(Math.min(getScroll(), getMaxScroll()));
                },
                error -> {
                    if (request != getRequest(source)) {
                        return;
                    }
                    setOnlineStatus(source, "Failed: " + error);
                    setLoading(source, false);
                }
        );
    }

    private void loadLocal(LocalEntry entry) {
        if (loadingConfig || Miau.configManager == null) {
            return;
        }
        boolean loaded = Miau.configManager.loadConfig(entry.name);
        actionStatus = loaded ? "Loaded local config: " + entry.name : "Failed to load: " + entry.name;
        if (loaded) {
            Utils.sendMessage("&7Loaded local config: &b" + entry.name);
        }
        refreshLocal();
    }

    private void loadOnline(OnlineConfigEntry entry, OnlineConfigSource source) {
        if (loadingConfig) {
            return;
        }
        loadingConfig = true;
        actionStatus = "Loading " + entry.getName() + "...";
        onlineConfigs.load(
                source,
                entry,
                loaded -> {
                    actionStatus = loaded ? "Loaded " + entry.getName() : "Failed to apply " + entry.getName();
                    loadingConfig = false;
                    if (loaded) {
                        Utils.sendMessage("&7Loaded " + source.getPath() + " config: &b" + entry.getName());
                        refreshLocal();
                    }
                },
                error -> {
                    actionStatus = "Failed: " + error;
                    loadingConfig = false;
                }
        );
    }

    private void layout(int screenWidth, int screenHeight) {
        width = Math.min(WINDOW_WIDTH, Math.max(160, screenWidth - 20));
        height = Math.min(WINDOW_HEIGHT, Math.max(120, screenHeight - 36));
        x = (screenWidth - width) / 2;
        y = Math.max(28, (screenHeight - height) / 2);
    }

    private int getListTop() {
        return y + HEADER_HEIGHT + TAB_HEIGHT + 4;
    }

    private int getListBottom() {
        return y + height - FOOTER_HEIGHT;
    }

    private int getMaxScroll() {
        return getMaxScroll(selectedTab);
    }

    private int getMaxScroll(Tab tab) {
        int count = tab == Tab.LOCAL ? localEntries.size() : getOnlineEntries(tab.source).size();
        return Math.max(0, count * ROW_HEIGHT - (getListBottom() - getListTop()));
    }

    private int getScroll() {
        switch (selectedTab) {
            case LOCAL:
                return localScroll;
            case USER:
                return userScroll;
            default:
                return officialScroll;
        }
    }

    private void setScroll(int value) {
        int scroll = Math.max(0, value);
        switch (selectedTab) {
            case LOCAL:
                localScroll = scroll;
                break;
            case USER:
                userScroll = scroll;
                break;
            default:
                officialScroll = scroll;
        }
    }

    private List<OnlineConfigEntry> getOnlineEntries(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? userEntries : officialEntries;
    }

    private String getOnlineStatus(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? userStatus : officialStatus;
    }

    private void setOnlineStatus(OnlineConfigSource source, String status) {
        if (source == OnlineConfigSource.USER) {
            userStatus = status;
        }
        else {
            officialStatus = status;
        }
    }

    private boolean isLoaded(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? userLoaded : officialLoaded;
    }

    private void setLoaded(OnlineConfigSource source, boolean loaded) {
        if (source == OnlineConfigSource.USER) {
            userLoaded = loaded;
        }
        else {
            officialLoaded = loaded;
        }
    }

    private boolean isLoading(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? userLoading : officialLoading;
    }

    private void setLoading(OnlineConfigSource source, boolean loading) {
        if (source == OnlineConfigSource.USER) {
            userLoading = loading;
        }
        else {
            officialLoading = loading;
        }
    }

    private int nextRequest(OnlineConfigSource source) {
        if (source == OnlineConfigSource.USER) {
            return ++userRequest;
        }
        return ++officialRequest;
    }

    private int getRequest(OnlineConfigSource source) {
        return source == OnlineConfigSource.USER ? userRequest : officialRequest;
    }

    private enum Tab {
        OFFICIAL("Official", OnlineConfigSource.OFFICIAL),
        LOCAL("Local", null),
        USER("User", OnlineConfigSource.USER);

        private final String title;
        private final OnlineConfigSource source;

        Tab(String title, OnlineConfigSource source) {
            this.title = title;
            this.source = source;
        }
    }

    private static final class LocalEntry {
        private final String name;
        private final String modified;
        private final boolean current;

        private LocalEntry(String name, String modified, boolean current) {
            this.name = name;
            this.modified = modified;
            this.current = current;
        }
    }
}
