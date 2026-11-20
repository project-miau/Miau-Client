package idle.miau.gui;

import dev.idle.igam.utils.TextFormatting;
import idle.miau.Miau;
import idle.miau.utility.changelog.ClientChangelogClient;
import idle.miau.utility.changelog.ClientChangelogEntry;
import idle.miau.utility.changelog.ClientChangelogFormatter;
import idle.miau.utility.changelog.ClientChangelogLine;
import idle.miau.utility.changelog.ClientChangelogPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuiChangelogs extends GuiScreen {
    private static final int BACK_BUTTON = 0;
    private static final int REFRESH_BUTTON = 1;
    private static final int WEBSITE_BUTTON = 2;
    private static final int OLDER_BUTTON = 3;
    private static final int WEBSITE_CONFIRM = 4;
    private static final int CARD_GAP = 4;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final String WEBSITE_URL = "https://getmiau.today/changelogs/client";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy")
            .withZone(ZoneId.systemDefault());
    private static final Comparator<ClientChangelogEntry> ENTRY_ORDER =
            new Comparator<ClientChangelogEntry>() {
                @Override
                public int compare(ClientChangelogEntry left, ClientChangelogEntry right) {
                    int dateOrder = Long.compare(
                            right.getPublishedAtEpochMillis(),
                            left.getPublishedAtEpochMillis());
                    return dateOrder != 0
                            ? dateOrder
                            : right.getReleaseId().compareTo(left.getReleaseId());
                }
            };

    private final GuiScreen parent;
    private final ClientChangelogClient client;
    private final List<ClientChangelogEntry> changelogs =
            new ArrayList<ClientChangelogEntry>();
    private List<ReleaseLayout> layouts = Collections.emptyList();
    private GuiButton refreshButton;
    private GuiButton olderButton;
    private String nextCursor;
    private String errorMessage;
    private int requestGeneration;
    private int layoutWidth = -1;
    private int totalContentHeight;
    private int scroll;
    private int maxScroll;
    private int scrollbarDragOffset;
    private int controlsLeft;
    private int controlsWidth;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private boolean loadingInitial;
    private boolean loadingOlder;
    private boolean loadedOnce;
    private boolean closed;
    private boolean draggingScrollbar;

    public GuiChangelogs(GuiScreen parent) {
        this(parent, new ClientChangelogClient());
    }

    GuiChangelogs(GuiScreen parent, ClientChangelogClient client) {
        this.parent = parent;
        this.client = client;
    }

    @Override
    public void initGui() {
        closed = false;
        int margin = 8;
        int gap = 8;
        controlsWidth = Math.max(90, Math.min(150, width / 5));
        controlsLeft = margin;
        contentLeft = controlsLeft + controlsWidth + gap;
        contentRight = width - margin;
        contentTop = 45;
        contentBottom = height - margin;

        int buttonX = controlsLeft + 4;
        int buttonWidth = controlsWidth - 8;
        int buttonY = contentTop + 8;
        int buttonStep = 22;
        refreshButton = new GuiButton(
                REFRESH_BUTTON,
                buttonX,
                buttonY,
                buttonWidth,
                20,
                "Refresh");
        buttonList.add(refreshButton);
        buttonList.add(new GuiButton(
                WEBSITE_BUTTON,
                buttonX,
                buttonY += buttonStep,
                buttonWidth,
                20,
                "Website"));
        olderButton = new GuiButton(
                OLDER_BUTTON,
                buttonX,
                buttonY += buttonStep,
                buttonWidth,
                20,
                "Older");
        buttonList.add(olderButton);
        buttonList.add(new GuiButton(
                BACK_BUTTON,
                buttonX,
                buttonY + buttonStep,
                buttonWidth,
                20,
                "Back"));
        updateButtons();
        if (!loadedOnce && !loadingInitial) {
            loadPage(false);
        }
    }

    @Override
    public void onGuiClosed() {
        closed = true;
        draggingScrollbar = false;
        loadingInitial = false;
        loadingOlder = false;
        requestGeneration++;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BACK_BUTTON) {
            mc.displayGuiScreen(parent);
        }
        else if (button.id == REFRESH_BUTTON) {
            loadPage(false);
        }
        else if (button.id == WEBSITE_BUTTON) {
            GuiConfirmOpenLink confirm = new GuiConfirmOpenLink(
                    this, WEBSITE_URL, WEBSITE_CONFIRM, true);
            confirm.disableSecurityWarning();
            mc.displayGuiScreen(confirm);
        }
        else if (button.id == OLDER_BUTTON) {
            loadPage(true);
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        if (id == WEBSITE_CONFIRM && result) {
            openWebsite();
        }
        mc.displayGuiScreen(this);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            scrollBy(-20);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            scrollBy(20);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollBy(-viewportHeight());
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollBy(viewportHeight());
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            scroll = 0;
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            scroll = maxScroll;
            return;
        }
        if (keyCode == Keyboard.KEY_F5) {
            loadPage(false);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollBy(wheel > 0 ? -36 : 36);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && maxScroll > 0 && mouseX >= scrollbarX() - 2
                && mouseX <= scrollbarX() + 8
                && mouseY >= viewportTop() && mouseY <= viewportBottom()) {
            int thumbY = scrollbarThumbY();
            int thumbHeight = scrollbarThumbHeight();
            if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                draggingScrollbar = true;
                scrollbarDragOffset = mouseY - thumbY;
            }
            else {
                setScrollFromThumb(mouseY - thumbHeight / 2);
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(
            int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar && clickedMouseButton == 0) {
            setScrollFromThumb(mouseY - scrollbarDragOffset);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScrollbar = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(
                controlsLeft,
                contentTop,
                controlsLeft + controlsWidth,
                contentBottom,
                0x76000000);
        drawRect(contentLeft, contentTop, contentRight, contentBottom, 0x52000000);
        drawCenteredString(
                fontRendererObj,
                TextFormatting.translate("&cMiau Changelogs"),
                width / 2,
                14,
                VALUE_COLOR);
        drawCenteredString(fontRendererObj, statusText(), width / 2, 28, statusColor());

        int panelX = contentLeft + 6;
        int panelWidth = Math.max(40, contentRight - panelX - 14);
        ensureLayouts(panelWidth);
        updateScrollBounds();
        beginScissor(
                contentLeft,
                viewportTop(),
                Math.max(1, contentRight - contentLeft),
                viewportHeight());
        if (changelogs.isEmpty()) {
            drawEmptyState();
        }
        else {
            int y = viewportTop() - scroll;
            for (ReleaseLayout layout : layouts) {
                if (y + layout.height >= viewportTop() && y <= viewportBottom()) {
                    drawRelease(panelX, y, panelWidth, layout);
                }
                y += layout.height + CARD_GAP;
            }
        }
        endScissor();
        drawScrollbar();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void loadPage(boolean append) {
        if (loadingInitial || loadingOlder || append && nextCursor == null) {
            return;
        }
        final int generation = ++requestGeneration;
        final String cursor = append ? nextCursor : null;
        errorMessage = null;
        if (append) {
            loadingOlder = true;
        }
        else {
            loadingInitial = true;
            scroll = 0;
        }
        updateButtons();
        try {
            Miau.getCachedExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final ClientChangelogPage page = client.loadPage(cursor);
                        scheduleResult(new Runnable() {
                            @Override
                            public void run() {
                                applyPage(generation, append, page);
                            }
                        });
                    }
                    catch (final Exception exception) {
                        scheduleResult(new Runnable() {
                            @Override
                            public void run() {
                                applyError(generation, append, exception);
                            }
                        });
                    }
                }
            });
        }
        catch (RuntimeException exception) {
            applyError(generation, append, exception);
        }
    }

    private void scheduleResult(Runnable result) {
        Minecraft minecraft = mc;
        if (minecraft != null) {
            minecraft.addScheduledTask(result);
        }
    }

    private void applyPage(
            int generation, boolean append, ClientChangelogPage page) {
        if (closed || generation != requestGeneration) {
            return;
        }
        if (append) {
            Map<String, ClientChangelogEntry> merged =
                    new LinkedHashMap<String, ClientChangelogEntry>();
            for (ClientChangelogEntry changelog : changelogs) {
                merged.put(changelog.getReleaseId(), changelog);
            }
            for (ClientChangelogEntry changelog : page.getChangelogs()) {
                merged.put(changelog.getReleaseId(), changelog);
            }
            changelogs.clear();
            changelogs.addAll(merged.values());
        }
        else {
            changelogs.clear();
            changelogs.addAll(page.getChangelogs());
            loadedOnce = true;
        }
        Collections.sort(changelogs, ENTRY_ORDER);
        nextCursor = page.getNextCursor();
        loadingInitial = false;
        loadingOlder = false;
        errorMessage = null;
        invalidateLayout();
        updateButtons();
    }

    private void applyError(int generation, boolean append, Exception exception) {
        if (closed || generation != requestGeneration) {
            return;
        }
        loadingInitial = false;
        loadingOlder = false;
        if (!append && changelogs.isEmpty()) {
            loadedOnce = false;
            nextCursor = null;
        }
        errorMessage = safeMessage(exception);
        updateButtons();
    }

    private void updateButtons() {
        boolean loading = loadingInitial || loadingOlder;
        if (refreshButton != null) {
            refreshButton.enabled = !loading;
            refreshButton.displayString = loadingInitial ? "Loading..." : "Refresh";
        }
        if (olderButton != null) {
            olderButton.enabled = !loading && nextCursor != null;
            olderButton.displayString = loadingOlder ? "Loading..." : "Older";
        }
    }

    private void invalidateLayout() {
        layoutWidth = -1;
        layouts = Collections.emptyList();
    }

    private void ensureLayouts(int panelWidth) {
        int textWidth = Math.max(20, panelWidth - 18);
        if (layoutWidth == textWidth) {
            return;
        }
        List<ReleaseLayout> rebuilt = new ArrayList<ReleaseLayout>();
        int height = 0;
        for (ClientChangelogEntry changelog : changelogs) {
            List<RenderedLine> renderedLines = new ArrayList<RenderedLine>();
            List<ClientChangelogLine> parsed =
                    ClientChangelogFormatter.parse(changelog.getNotes());
            if (parsed.isEmpty()) {
                for (String text : fontRendererObj.listFormattedStringToWidth(
                        "- No changelog details were provided.", textWidth)) {
                    renderedLines.add(new RenderedLine(
                            text, ClientChangelogLine.Type.DEFAULT.getColor()));
                }
            }
            for (ClientChangelogLine line : parsed) {
                List<String> wrapped = fontRendererObj.listFormattedStringToWidth(
                        "- " + line.getText(), textWidth);
                for (String text : wrapped) {
                    renderedLines.add(new RenderedLine(text, line.getType().getColor()));
                }
            }
            ReleaseLayout layout = new ReleaseLayout(
                    changelog,
                    renderedLines,
                    Math.max(44, 31 + renderedLines.size() * 11));
            rebuilt.add(layout);
            height += layout.height + CARD_GAP;
        }
        layouts = Collections.unmodifiableList(rebuilt);
        totalContentHeight = Math.max(0, height - CARD_GAP);
        layoutWidth = textWidth;
    }

    private void drawRelease(int x, int y, int cardWidth, ReleaseLayout layout) {
        drawRect(
                x,
                y,
                x + cardWidth,
                y + layout.height,
                layout.changelog.isCurrent() ? 0x8A16445A : 0x72000000);
        String date = formatDate(layout.changelog);
        int dateWidth = fontRendererObj.getStringWidth(date);
        String current = layout.changelog.isCurrent() ? "Current" : "";
        int currentWidth = current.isEmpty()
                ? 0 : fontRendererObj.getStringWidth(current) + 7;
        int labelWidth = fontRendererObj.getStringWidth("Version: ");
        int nameWidth = Math.max(
                20,
                cardWidth - dateWidth - currentWidth - labelWidth - 28);
        String name = fontRendererObj.trimStringToWidth(
                layout.changelog.getDisplayName(), nameWidth);
        drawString(fontRendererObj, "Version: ", x + 8, y + 7, LABEL_COLOR);
        drawString(fontRendererObj, name, x + 8 + labelWidth, y + 7, VALUE_COLOR);
        if (!current.isEmpty()) {
            drawString(
                    fontRendererObj,
                    current,
                    x + 12 + labelWidth + fontRendererObj.getStringWidth(name),
                    y + 7,
                    0xFF55FF55);
        }
        drawString(
                fontRendererObj,
                date,
                x + cardWidth - dateWidth - 8,
                y + 7,
                LABEL_COLOR);
        drawRect(x + 7, y + 20, x + cardWidth - 7, y + 21, 0x66000000);
        int textY = y + 25;
        for (RenderedLine line : layout.lines) {
            drawString(fontRendererObj, line.text, x + 9, textY, line.color);
            textY += 11;
        }
    }

    private void drawEmptyState() {
        String message;
        int color;
        if (loadingInitial) {
            message = "Loading changelogs...";
            color = LABEL_COLOR;
        }
        else if (errorMessage != null) {
            message = errorMessage;
            color = ERROR_COLOR;
        }
        else {
            message = "No changelogs are available yet.";
            color = LABEL_COLOR;
        }
        List<String> wrapped = fontRendererObj.listFormattedStringToWidth(
                message, Math.max(40, contentRight - contentLeft - 24));
        int y = viewportTop()
                + Math.max(12, (viewportHeight() - wrapped.size() * 11) / 2);
        int centerX = contentLeft + (contentRight - contentLeft) / 2;
        for (String line : wrapped) {
            drawCenteredString(fontRendererObj, line, centerX, y, color);
            y += 11;
        }
    }

    private void drawScrollbar() {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = scrollbarX();
        drawRect(trackX, viewportTop(), trackX + 6, viewportBottom(), 0xFF000000);
        int thumbY = scrollbarThumbY();
        drawRect(
                trackX,
                thumbY,
                trackX + 6,
                thumbY + scrollbarThumbHeight(),
                draggingScrollbar ? 0xFFC0C0C0 : 0xFF808080);
        drawRect(
                trackX,
                thumbY,
                trackX + 5,
                thumbY + scrollbarThumbHeight() - 1,
                draggingScrollbar ? 0xFFFFFFFF : 0xFFC0C0C0);
    }

    private void beginScissor(int x, int y, int width, int height) {
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                x * scale,
                (this.height - y - height) * scale,
                width * scale,
                height * scale);
    }

    private void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void updateScrollBounds() {
        maxScroll = Math.max(0, totalContentHeight - viewportHeight());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    private void scrollBy(int amount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll + amount));
    }

    private void setScrollFromThumb(int thumbY) {
        int available = viewportHeight() - scrollbarThumbHeight();
        if (available <= 0) {
            scroll = 0;
            return;
        }
        int position = Math.max(0, Math.min(available, thumbY - viewportTop()));
        scroll = Math.round((float) position / available * maxScroll);
    }

    private int scrollbarThumbY() {
        int available = viewportHeight() - scrollbarThumbHeight();
        return viewportTop() + (maxScroll == 0 ? 0 : Math.round(
                (float) scroll / maxScroll * available));
    }

    private int scrollbarThumbHeight() {
        if (totalContentHeight <= 0) {
            return viewportHeight();
        }
        int maximum = Math.max(1, viewportHeight() - 8);
        int minimum = Math.min(32, maximum);
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        viewportHeight() * viewportHeight() / totalContentHeight));
    }

    private int viewportHeight() {
        return Math.max(1, viewportBottom() - viewportTop());
    }

    private int viewportTop() {
        return contentTop + 6;
    }

    private int viewportBottom() {
        return Math.max(viewportTop() + 1, contentBottom - 6);
    }

    private int scrollbarX() {
        return contentRight - 8;
    }

    private String statusText() {
        if (loadingOlder) {
            return "Loading older releases...";
        }
        if (loadingInitial) {
            return changelogs.isEmpty() ? "Fetching release history" : "Refreshing release history";
        }
        if (errorMessage != null && !changelogs.isEmpty()) {
            return errorMessage;
        }
        if (!changelogs.isEmpty()) {
            return changelogs.size() + (changelogs.size() == 1 ? " release" : " releases");
        }
        return "Release history from api.getmiau.today";
    }

    private int statusColor() {
        return errorMessage != null ? ERROR_COLOR : LABEL_COLOR;
    }

    private String formatDate(ClientChangelogEntry changelog) {
        try {
            return DATE_FORMAT.format(Instant.ofEpochMilli(
                    changelog.getPublishedAtEpochMillis()));
        }
        catch (Exception exception) {
            return changelog.getPublishedAt();
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Unable to load changelogs";
        }
        String normalized = message.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() > 160
                ? normalized.substring(0, 157) + "..." : normalized;
    }

    private void openWebsite() {
        try {
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Object desktop = desktopClass.getMethod("getDesktop").invoke(null);
            desktopClass.getMethod("browse", URI.class).invoke(
                    desktop, new URI(WEBSITE_URL));
        }
        catch (Exception exception) {
            errorMessage = "Unable to open the changelog website";
        }
    }

    private static final class ReleaseLayout {
        private final ClientChangelogEntry changelog;
        private final List<RenderedLine> lines;
        private final int height;

        private ReleaseLayout(
                ClientChangelogEntry changelog,
                List<RenderedLine> lines,
                int height) {
            this.changelog = changelog;
            this.lines = lines;
            this.height = height;
        }
    }

    private static final class RenderedLine {
        private final String text;
        private final int color;

        private RenderedLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

}
