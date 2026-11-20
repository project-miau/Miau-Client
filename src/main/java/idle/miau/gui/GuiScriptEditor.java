package idle.miau.gui;

import idle.miau.Miau;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiScriptEditor extends GuiScreen {
    private static final int CARD_MARGIN_X = 20;
    private static final int CARD_MARGIN_Y = 14;
    private static final int TOP_BAR_HEIGHT = 28;
    private static final int BOTTOM_BAR_HEIGHT = 18;
    private static final int GUTTER_WIDTH = 28;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int FONT_PADDING = 3;

    private static final int CARD_BG_COLOR = 0xF0141416;
    private static final int EDITOR_BG_COLOR = 0xEE111113;
    private static final int TOPBAR_BG_COLOR = 0xFF19191C;
    private static final int BOTTOMBAR_BG_COLOR = 0xFF131315;
    private static final int TEXT_MUTED = 0xFF888888;
    private static final int TEXT_WHITE = 0xFFEEEEEE;
    private static final int SELECTION_COLOR = 0x553875D7;
    private static final int LINE_HIGHLIGHT_COLOR = 0x10FFFFFF;

    private static final int SYNTAX_KEYWORD = 0xFF569CD6;
    private static final int SYNTAX_STRING = 0xFFCE9178;
    private static final int SYNTAX_COMMENT = 0xFF6A9955;
    private static final int SYNTAX_ANNOTATION = 0xFFDCDCAA;
    private static final int SYNTAX_NUMBER = 0xFFB5CEA8;
    private static final int SYNTAX_DEFAULT = 0xFFD4D4D4;

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null"
    ));

    private final GuiScreen parentScreen;
    private final String initialScriptTarget;
    private File scriptsDirectory;
    private final List<File> scriptFiles = new ArrayList<>();
    private File currentFile;

    private final List<String> lines = new ArrayList<>();
    private int cursorRow = 0;
    private int cursorCol = 0;

    private int selAnchorRow = -1;
    private int selAnchorCol = -1;

    private float scrollY = 0.0f;
    private float scrollX = 0.0f;

    private boolean isDraggingScrollbar = false;
    private float dragOffsetThumbY = 0.0f;

    private boolean isScriptMenuOpen = false;

    private boolean modified = false;
    private String statusText = "Ready";
    private boolean statusIsError = false;
    private long statusTimestamp = 0L;

    private boolean isCreatingScript = false;
    private String newScriptNameInput = "";

    private final LinkedList<List<String>> undoStack = new LinkedList<>();
    private final LinkedList<List<String>> redoStack = new LinkedList<>();
    private static final int MAX_UNDO = 40;

    private boolean isDraggingMouse = false;

    public GuiScriptEditor(GuiScreen parent) {
        this(parent, null);
    }

    public GuiScriptEditor(String initialScriptTarget) {
        this(null, initialScriptTarget);
    }

    public GuiScriptEditor(GuiScreen parent, String initialScriptTarget) {
        this.parentScreen = parent;
        this.initialScriptTarget = initialScriptTarget;
    }

    @Override
    public void initGui() {
        try {
            Keyboard.enableRepeatEvents(true);
        } catch (Throwable ignored) {}
        resolveScriptsDirectory();
        refreshFileList();

        if (currentFile == null) {
            if (initialScriptTarget != null && !initialScriptTarget.trim().isEmpty()) {
                String targetName = initialScriptTarget.trim();
                if (!targetName.endsWith(".java")) {
                    targetName += ".java";
                }
                for (File file : scriptFiles) {
                    if (file.getName().equalsIgnoreCase(targetName)) {
                        loadFile(file);
                        break;
                    }
                }
                if (currentFile == null && scriptsDirectory != null) {
                    File targetFile = new File(scriptsDirectory, targetName);
                    if (!targetFile.exists()) {
                        String className = targetName.substring(0, targetName.length() - 5);
                        try {
                            String template = buildScriptTemplate(className);
                            Files.write(targetFile.toPath(), Collections.singletonList(template), StandardCharsets.UTF_8);
                            refreshFileList();
                        } catch (IOException ignored) {}
                    }
                    if (targetFile.exists()) {
                        loadFile(targetFile);
                    }
                }
            }
            if (currentFile == null && !scriptFiles.isEmpty()) {
                loadFile(scriptFiles.get(0));
            } else if (currentFile == null) {
                lines.clear();
                lines.add("// No scripts found in miau/scripts");
                lines.add("// Click 'New Script' above to create one!");
                resetCursor();
            }
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            Keyboard.enableRepeatEvents(false);
        } catch (Throwable ignored) {}
    }

    private void resolveScriptsDirectory() {
        if (Miau.scriptManager != null && Miau.scriptManager.directory != null) {
            scriptsDirectory = Miau.scriptManager.directory;
        } else {
            File base = idle.miau.utility.ClientStorage.getDirectory(Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().mcDataDir : null);
            scriptsDirectory = new File(base, "scripts");
        }
        if (scriptsDirectory != null && !scriptsDirectory.exists()) {
            scriptsDirectory.mkdirs();
        }
    }

    private void refreshFileList() {
        scriptFiles.clear();
        if (scriptsDirectory != null && scriptsDirectory.isDirectory()) {
            File[] files = scriptsDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".java"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                Collections.addAll(scriptFiles, files);
            }
        }
    }

    private void loadFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            currentFile = file;
            lines.clear();
            List<String> loadedLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String fullContent = String.join("\n", loadedLines);
            if (fullContent.contains("extends ScriptModule") || fullContent.contains("net.minecraftforge.")) {
                String scriptBase = file.getName().replace(".java", "");
                String cleaned = keystrokesmod.script.ScriptSourceTransformer.unwrapLegacyTemplate(fullContent, scriptBase).trim();
                if (cleaned.isEmpty() || cleaned.equals("// Unwrapped class end")) {
                    lines.addAll(Arrays.asList(buildScriptTemplate(scriptBase).split("\n")));
                } else {
                    lines.addAll(Arrays.asList(cleaned.split("\r?\n", -1)));
                }
                modified = true;
                setStatus("Migrated " + file.getName() + " to Miau script format (Ctrl+S to save)", false);
            } else {
                if (loadedLines.isEmpty()) {
                    lines.add("");
                } else {
                    lines.addAll(loadedLines);
                }
                setStatus("Loaded " + file.getName(), false);
            }
            modified = false;
            undoStack.clear();
            redoStack.clear();
            pushUndoSnapshot();
            resetCursor();
        } catch (IOException e) {
            setStatus("Failed to read " + file.getName(), true);
            e.printStackTrace();
        }
    }

    private void saveAndReload() {
        if (currentFile == null) {
            setStatus("No file open to save", true);
            return;
        }
        try {
            Files.write(currentFile.toPath(), lines, StandardCharsets.UTF_8);
            modified = false;

            keystrokesmod.script.Manager manager = (Miau.moduleManager != null) ? Miau.moduleManager.scriptManager : null;
            if (Miau.scriptManager != null) {
                Miau.scriptManager.loadScripts();
                if (manager != null) {
                    manager.loadScriptsNow();
                }
            } else if (manager != null) {
                manager.loadScriptsNow();
            }

            if (Miau.scriptManager != null && Miau.scriptManager.compiler == null) {
                setStatus("Saved to disk. (Note: Launch client with JDK 17+ to compile scripts)", true);
                refreshFileList();
                return;
            }

            String scriptBaseName = currentFile.getName().replace(".java", "");
            keystrokesmod.script.Script foundScript = null;
            if (Miau.scriptManager != null) {
                foundScript = Miau.scriptManager.getScriptByFile(currentFile);
                if (foundScript == null) {
                    foundScript = Miau.scriptManager.getScript(scriptBaseName);
                }
            }

            if (foundScript != null && foundScript.error) {
                String err = foundScript.lastError != null ? foundScript.lastError : "Compile error! Check chat/console";
                setStatus("Error: " + err, true);
            } else if (foundScript != null) {
                setStatus("Saved & compiled " + currentFile.getName() + " successfully!", false);
            } else {
                setStatus("Saved " + currentFile.getName(), false);
            }
            refreshFileList();
        } catch (Exception e) {
            setStatus("Failed to save: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private void createNewScript(String name) {
        if (name == null || name.trim().isEmpty()) {
            setStatus("Script name cannot be empty", true);
            return;
        }
        String cleanName = name.trim();
        if (cleanName.endsWith(".java")) {
            cleanName = cleanName.substring(0, cleanName.length() - 5);
        }
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleanName.isEmpty()) {
            setStatus("Invalid script name", true);
            return;
        }

        File newFile = new File(scriptsDirectory, cleanName + ".java");
        if (newFile.exists()) {
            setStatus("File already exists: " + newFile.getName(), true);
            return;
        }

        try {
            String template = buildScriptTemplate(cleanName);
            Files.write(newFile.toPath(), Collections.singletonList(template), StandardCharsets.UTF_8);
            refreshFileList();
            loadFile(newFile);
            saveAndReload();
            isCreatingScript = false;
            newScriptNameInput = "";
            setStatus("Created " + newFile.getName(), false);
        } catch (IOException e) {
            setStatus("Failed to create script: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    private String buildScriptTemplate(String className) {
        return "// Miau Script: " + className + "\n\n" +
                "void onLoad() {\n" +
                "    modules.registerDescription(\"Custom script: " + className + "\");\n" +
                "    // modules.registerButton(\"Toggle Feature\", true);\n" +
                "    // modules.registerSlider(\"Settings\", \"Value\", \"\", 5.0, 1.0, 10.0, 0.5);\n" +
                "}\n\n" +
                "void onEnable() {\n" +
                "    client.print(\"&a" + className + " enabled!\");\n" +
                "}\n\n" +
                "void onDisable() {\n" +
                "    client.print(\"&c" + className + " disabled!\");\n" +
                "}\n\n" +
                "void onPreUpdate() {\n" +
                "    // Runs each client tick\n" +
                "}\n";
    }

    private void resetCursor() {
        cursorRow = 0;
        cursorCol = 0;
        selAnchorRow = -1;
        selAnchorCol = -1;
        scrollY = 0.0f;
        scrollX = 0.0f;
    }

    private void setStatus(String message, boolean error) {
        statusText = message;
        statusIsError = error;
        statusTimestamp = System.currentTimeMillis();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int getCardX() {
        return (width < 420) ? 0 : CARD_MARGIN_X;
    }

    private int getCardY() {
        return (height < 320) ? 0 : CARD_MARGIN_Y;
    }

    private int getCardW() {
        return (width < 420) ? width : Math.max(260, width - CARD_MARGIN_X * 2);
    }

    private int getCardH() {
        return (height < 320) ? height : Math.max(160, height - CARD_MARGIN_Y * 2);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static boolean hovered(float x, float y, float width, float height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (fontRendererObj == null) {
            fontRendererObj = Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().fontRendererObj : null;
        }
        if (fontRendererObj == null) {
            return;
        }

        drawDefaultBackground();

        int cardX = getCardX();
        int cardY = getCardY();
        int cardW = getCardW();
        int cardH = getCardH();

        int accentColor = ArrayListModule.getHudColorAtPosition(cardX + 20, cardY + 10);

        RenderUtils.drawRoundedRectangle(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 6.0f, withAlpha(accentColor, 150));
        RenderUtils.drawRoundedRectangle(cardX, cardY, cardX + cardW, cardY + cardH, 6.0f, CARD_BG_COLOR);

        drawTopBar(mouseX, mouseY);

        drawCodeEditor(mouseX, mouseY);

        drawBottomBar();

        if (isScriptMenuOpen) {
            drawScriptMenu(mouseX, mouseY);
        }

        if (isCreatingScript) {
            drawNewScriptModal(mouseX, mouseY);
        }

        if (isDraggingScrollbar) {
            if (!Mouse.isButtonDown(0)) {
                isDraggingScrollbar = false;
            } else {
                updateScrollbarDrag(mouseY);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTopBar(int mouseX, int mouseY) {
        int cardX = getCardX();
        int cardY = getCardY();
        int cardW = getCardW();

        int accentColor = ArrayListModule.getHudColorAtPosition(cardX + 20, cardY + 10);

        RenderUtils.drawRoundedRectangle(cardX, cardY, cardX + cardW, cardY + TOP_BAR_HEIGHT, 5.0f, TOPBAR_BG_COLOR);

        fontRendererObj.drawStringWithShadow("MIAU SCRIPT", cardX + 12, cardY + 9, accentColor);

        int pillX = cardX + 86;
        int pillY = cardY + 4;
        String fileName = (currentFile != null ? currentFile.getName() : "No Script") + (modified ? " *" : "");
        int nameW = fontRendererObj.getStringWidth(fileName);
        int pillW = Math.max(120, nameW + 24);
        int pillH = 20;

        boolean isPillHovered = hovered(pillX, pillY, pillW, pillH, mouseX, mouseY);
        RenderUtils.drawRoundedRectangle(pillX, pillY, pillX + pillW, pillY + pillH, 4.0f,
                isScriptMenuOpen ? 0xFF38383E : (isPillHovered ? 0xFF303035 : 0xFF222226));

        int nameColor = modified ? 0xFFFFAA00 : TEXT_WHITE;
        fontRendererObj.drawStringWithShadow(fileName, pillX + 7, pillY + 6, nameColor);
        fontRendererObj.drawStringWithShadow("▾", pillX + pillW - 12, pillY + 6, TEXT_MUTED);

        int newX = pillX + pillW + 6;
        int newW = 46;
        boolean newHovered = hovered(newX, pillY, newW, pillH, mouseX, mouseY);
        RenderUtils.drawRoundedRectangle(newX, pillY, newX + newW, pillY + pillH, 4.0f, newHovered ? 0xFF35353B : 0xFF222226);
        fontRendererObj.drawStringWithShadow("+ New", newX + (newW - fontRendererObj.getStringWidth("+ New")) / 2, pillY + 6, newHovered ? 0xFFFFFFFF : TEXT_MUTED);

        int curRight = cardX + cardW - 8;

        curRight -= 24;
        drawPillButton("✕", curRight, pillY, 22, pillH, mouseX, mouseY, false, 0, 0xFFFF5555);

        curRight -= 52;
        drawPillButton("Folder", curRight, pillY, 48, pillH, mouseX, mouseY, false, 0, TEXT_WHITE);

        if (modified) {
            curRight -= 54;
            drawPillButton("Revert", curRight, pillY, 50, pillH, mouseX, mouseY, false, 0, 0xFFFFAA00);
        }

        curRight -= 102;
        int saveBg = modified ? 0xFF2E7D32 : withAlpha(accentColor, 210);
        drawPillButton("Save (Ctrl+S)", curRight, pillY, 98, pillH, mouseX, mouseY, true, saveBg, 0xFFFFFFFF);
    }

    private void drawPillButton(String label, int bx, int by, int bw, int bh, int mouseX, int mouseY, boolean highlight, int customBg, int textColor) {
        boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        int bg = highlight ? (hovered ? 0xFF388E3C : customBg) : (hovered ? 0xFF35353B : 0xFF222226);
        RenderUtils.drawRoundedRectangle(bx, by, bx + bw, by + bh, 4.0f, bg);
        int textX = bx + (bw - fontRendererObj.getStringWidth(label)) / 2;
        int textY = by + (bh - fontRendererObj.FONT_HEIGHT) / 2;
        fontRendererObj.drawStringWithShadow(label, textX, textY, hovered ? 0xFFFFFFFF : textColor);
    }

    private void drawScriptMenu(int mouseX, int mouseY) {
        int cardX = getCardX();
        int cardY = getCardY();
        int pillX = cardX + 86;
        int pillY = cardY + 4;
        String fileName = (currentFile != null ? currentFile.getName() : "No Script") + (modified ? " *" : "");
        int nameW = fontRendererObj.getStringWidth(fileName);
        int pillW = Math.max(120, nameW + 24);
        int menuX = pillX;
        int menuY = pillY + 20 + 2;
        int menuW = Math.max(160, pillW);
        int itemH = 18;
        int visibleCount = Math.min(8, Math.max(1, scriptFiles.size()));
        int menuH = visibleCount * itemH + 6;

        int accent = ArrayListModule.getHudColorAtPosition(menuX, menuY);
        RenderUtils.drawRoundedRectangle(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + menuH + 1, 4.0f, withAlpha(accent, 180));
        RenderUtils.drawRoundedRectangle(menuX, menuY, menuX + menuW, menuY + menuH, 4.0f, 0xFA1A1A1E);

        if (scriptFiles.isEmpty()) {
            fontRendererObj.drawStringWithShadow("No scripts in miau/scripts", menuX + 8, menuY + 7, TEXT_MUTED);
            return;
        }

        int itemY = menuY + 3;
        for (int i = 0; i < visibleCount; i++) {
            File file = scriptFiles.get(i);
            boolean isCurrent = (currentFile != null && currentFile.equals(file));
            boolean hovered = mouseX >= menuX && mouseX <= menuX + menuW && mouseY >= itemY && mouseY < itemY + itemH;

            if (isCurrent) {
                Gui.drawRect(menuX + 1, itemY, menuX + 3, itemY + itemH, accent);
            }
            if (hovered) {
                Gui.drawRect(menuX + 1, itemY, menuX + menuW - 1, itemY + itemH, 0x22FFFFFF);
            }

            String name = file.getName();
            int color = isCurrent ? accent : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            fontRendererObj.drawStringWithShadow(name, menuX + 8, itemY + 5, color);

            itemY += itemH;
        }
    }

    private void drawCodeEditor(int mouseX, int mouseY) {
        int cardX = getCardX();
        int cardY = getCardY();
        int cardW = getCardW();
        int cardH = getCardH();

        int editorX = cardX + 6;
        int editorY = cardY + TOP_BAR_HEIGHT + 2;
        int editorW = cardW - 12;
        int editorH = cardH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 4;

        RenderUtils.drawRoundedRectangle(editorX, editorY, editorX + editorW, editorY + editorH, 4.0f, EDITOR_BG_COLOR);

        int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
        int textStartX = editorX + GUTTER_WIDTH + 6;
        int firstVisibleRow = Math.max(0, (int) (scrollY / lineHeight));
        int visibleRowCount = (editorH / lineHeight) + 2;
        int lastVisibleRow = Math.min(lines.size() - 1, firstVisibleRow + visibleRowCount);

        RenderUtils.scissorPushGui(editorX, editorY, editorW - SCROLLBAR_WIDTH - 4, editorH);

        for (int r = firstVisibleRow; r <= lastVisibleRow; r++) {
            int lineY = editorY + (int) ((r * lineHeight) - scrollY);

            String numStr = String.valueOf(r + 1);
            int numW = fontRendererObj.getStringWidth(numStr);
            int numColor = (r == cursorRow) ? TEXT_WHITE : 0xFF505055;
            fontRendererObj.drawString(numStr, editorX + GUTTER_WIDTH - numW - 4, lineY + 1, numColor, false);

            if (r == cursorRow) {
                Gui.drawRect(editorX + GUTTER_WIDTH, lineY, editorX + editorW - SCROLLBAR_WIDTH - 4, lineY + lineHeight, LINE_HIGHLIGHT_COLOR);
            }

            String lineText = (r < lines.size()) ? lines.get(r) : "";

            drawSelectionOnLine(r, lineText, textStartX - (int) scrollX, lineY, lineHeight);

            drawSyntaxLine(lineText, textStartX - (int) scrollX, lineY + 1);

            if (r == cursorRow && (System.currentTimeMillis() / 500L) % 2 == 0) {
                int safeCol = Math.min(cursorCol, lineText.length());
                String beforeCursor = lineText.substring(0, safeCol);
                int caretX = textStartX - (int) scrollX + fontRendererObj.getStringWidth(beforeCursor);
                Gui.drawRect(caretX, lineY, caretX + 1, lineY + lineHeight - 1, 0xFFFFFFFF);
            }
        }

        RenderUtils.scissorPop();

        drawScrollbar(mouseX, mouseY, editorX, editorY, editorW, editorH);
    }

    private void drawScrollbar(int mouseX, int mouseY, int editorX, int editorY, int editorW, int editorH) {
        int trackX = editorX + editorW - SCROLLBAR_WIDTH - 2;
        int trackY = editorY + 3;
        int trackW = SCROLLBAR_WIDTH;
        int trackH = editorH - 6;

        int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
        float contentHeight = Math.max(1, lines.size()) * lineHeight;
        float maxScroll = Math.max(0.0f, contentHeight - editorH + lineHeight);

        RenderUtils.drawRoundedRectangle(trackX, trackY, trackX + trackW, trackY + trackH, 3.0f, 0x18FFFFFF);

        if (maxScroll > 0.0f) {
            float thumbH = Math.max(22.0f, (float) editorH / contentHeight * trackH);
            float thumbY = trackY + (scrollY / maxScroll) * (trackH - thumbH);

            boolean isHovered = mouseX >= trackX - 3 && mouseX <= trackX + trackW + 3 && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            int accent = ArrayListModule.getHudColorAtPosition(trackX, thumbY);
            int thumbColor = isDraggingScrollbar ? withAlpha(accent, 240)
                    : (isHovered ? 0x90FFFFFF : 0x48FFFFFF);

            RenderUtils.drawRoundedRectangle(trackX, thumbY, trackX + trackW, thumbY + thumbH, 3.0f, thumbColor);
        }
    }

    private void updateScrollbarDrag(int mouseY) {
        int cardH = getCardH();
        int editorH = cardH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 4;
        int trackY = getCardY() + TOP_BAR_HEIGHT + 2 + 3;
        int trackH = editorH - 6;
        int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
        float contentHeight = Math.max(1, lines.size()) * lineHeight;
        float maxScroll = Math.max(0.0f, contentHeight - editorH + lineHeight);

        if (maxScroll <= 0.0f) {
            scrollY = 0.0f;
            return;
        }
        float thumbH = Math.max(22.0f, (float) editorH / contentHeight * trackH);
        float availableTrack = trackH - thumbH;
        if (availableTrack <= 0.0f) {
            scrollY = 0.0f;
            return;
        }
        float newThumbY = mouseY - trackY - dragOffsetThumbY;
        float ratio = newThumbY / availableTrack;
        scrollY = Math.max(0.0f, Math.min(maxScroll, ratio * maxScroll));
    }

    private void drawSelectionOnLine(int r, String lineText, int textX, int lineY, int lineHeight) {
        if (!hasSelection()) {
            return;
        }
        int minRow = Math.min(selAnchorRow, cursorRow);
        int maxRow = Math.max(selAnchorRow, cursorRow);
        if (r < minRow || r > maxRow) {
            return;
        }

        int startCol;
        int endCol;

        if (minRow == maxRow) {
            startCol = Math.min(selAnchorCol, cursorCol);
            endCol = Math.max(selAnchorCol, cursorCol);
        } else if (r == minRow) {
            startCol = (selAnchorRow == minRow) ? selAnchorCol : cursorCol;
            endCol = lineText.length();
        } else if (r == maxRow) {
            startCol = 0;
            endCol = (selAnchorRow == maxRow) ? selAnchorCol : cursorCol;
        } else {
            startCol = 0;
            endCol = lineText.length();
        }

        startCol = Math.max(0, Math.min(startCol, lineText.length()));
        endCol = Math.max(0, Math.min(endCol, lineText.length()));

        int x1 = textX + fontRendererObj.getStringWidth(lineText.substring(0, startCol));
        int x2 = textX + fontRendererObj.getStringWidth(lineText.substring(0, endCol));
        if (x1 == x2 && startCol == lineText.length()) {
            x2 += 4;
        }
        Gui.drawRect(x1, lineY, x2, lineY + lineHeight, SELECTION_COLOR);
    }

    private void drawSyntaxLine(String text, int startX, int y) {
        if (text.isEmpty()) {
            return;
        }
        int curX = startX;

        int commentIndex = text.indexOf("//");
        String codePart = (commentIndex >= 0) ? text.substring(0, commentIndex) : text;
        String commentPart = (commentIndex >= 0) ? text.substring(commentIndex) : null;

        int len = codePart.length();
        int i = 0;
        while (i < len) {
            char c = codePart.charAt(i);

            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i;
                i++;
                while (i < len && codePart.charAt(i) != quote) {
                    if (codePart.charAt(i) == '\\' && i + 1 < len) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                if (i < len) {
                    i++;
                }
                String strTok = codePart.substring(start, i);
                fontRendererObj.drawString(strTok, curX, y, SYNTAX_STRING, false);
                curX += fontRendererObj.getStringWidth(strTok);
                continue;
            }

            if (c == '@') {
                int start = i;
                i++;
                while (i < len && Character.isJavaIdentifierPart(codePart.charAt(i))) {
                    i++;
                }
                String annotTok = codePart.substring(start, i);
                fontRendererObj.drawString(annotTok, curX, y, SYNTAX_ANNOTATION, false);
                curX += fontRendererObj.getStringWidth(annotTok);
                continue;
            }

            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(codePart.charAt(i))) {
                    i++;
                }
                String word = codePart.substring(start, i);
                int color = JAVA_KEYWORDS.contains(word) ? SYNTAX_KEYWORD : SYNTAX_DEFAULT;
                fontRendererObj.drawString(word, curX, y, color, false);
                curX += fontRendererObj.getStringWidth(word);
                continue;
            }

            if (Character.isDigit(c)) {
                int start = i;
                while (i < len && (Character.isDigit(codePart.charAt(i)) || codePart.charAt(i) == '.' ||
                        codePart.charAt(i) == 'f' || codePart.charAt(i) == 'F' ||
                        codePart.charAt(i) == 'd' || codePart.charAt(i) == 'D' ||
                        codePart.charAt(i) == 'L' || codePart.charAt(i) == 'l')) {
                    i++;
                }
                String num = codePart.substring(start, i);
                fontRendererObj.drawString(num, curX, y, SYNTAX_NUMBER, false);
                curX += fontRendererObj.getStringWidth(num);
                continue;
            }

            String single = String.valueOf(c);
            fontRendererObj.drawString(single, curX, y, SYNTAX_DEFAULT, false);
            curX += fontRendererObj.getStringWidth(single);
            i++;
        }

        if (commentPart != null) {
            fontRendererObj.drawString(commentPart, curX, y, SYNTAX_COMMENT, false);
        }
    }

    private void drawBottomBar() {
        int cardX = getCardX();
        int cardY = getCardY();
        int cardW = getCardW();
        int cardH = getCardH();

        int barY = cardY + cardH - BOTTOM_BAR_HEIGHT;
        RenderUtils.drawRoundedRectangle(cardX, barY, cardX + cardW, cardY + cardH, 4.0f, BOTTOMBAR_BG_COLOR);

        int statusColor = statusIsError ? 0xFFFF5555 : 0xFF55FF55;
        fontRendererObj.drawStringWithShadow(statusText, cardX + 10, barY + 5, statusColor);

        String info = "Ln " + (cursorRow + 1) + ", Col " + (cursorCol + 1) + "  |  " + lines.size() + " lines  |  UTF-8";
        int infoW = fontRendererObj.getStringWidth(info);
        fontRendererObj.drawStringWithShadow(info, cardX + cardW - infoW - 10, barY + 5, TEXT_MUTED);
    }

    private void drawNewScriptModal(int mouseX, int mouseY) {
        Gui.drawRect(0, 0, width, height, 0x90000000);

        int mw = 260;
        int mh = 105;
        int mx = (width - mw) / 2;
        int my = (height - mh) / 2;

        int accent = ArrayListModule.getHudColorAtPosition(mx, my);
        RenderUtils.drawRoundedRectangle(mx - 1, my - 1, mx + mw + 1, my + mh + 1, 5.0f, withAlpha(accent, 180));
        RenderUtils.drawRoundedRectangle(mx, my, mx + mw, my + mh, 5.0f, 0xF51E1E22);

        fontRendererObj.drawStringWithShadow("Create New Script", mx + 16, my + 12, TEXT_WHITE);
        fontRendererObj.drawStringWithShadow("Enter script name (.java):", mx + 16, my + 28, TEXT_MUTED);

        int fieldY = my + 44;
        RenderUtils.drawRoundedRectangle(mx + 16, fieldY, mx + mw - 16, fieldY + 20, 3.0f, 0xFF141416);
        String displayInput = newScriptNameInput + (((System.currentTimeMillis() / 400L) % 2 == 0) ? "_" : "");
        fontRendererObj.drawStringWithShadow(displayInput, mx + 22, fieldY + 6, 0xFFFFFFFF);

        drawPillButton("Create", mx + mw - 140, my + 72, 60, 20, mouseX, mouseY, true, withAlpha(accent, 220), 0xFFFFFFFF);
        drawPillButton("Cancel", mx + mw - 72, my + 72, 56, 20, mouseX, mouseY, false, 0, TEXT_MUTED);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            return;
        }

        if (isCreatingScript) {
            int mw = 260;
            int mh = 105;
            int mx = (width - mw) / 2;
            int my = (height - mh) / 2;

            if (mouseX >= mx + mw - 140 && mouseX <= mx + mw - 80 && mouseY >= my + 72 && mouseY <= my + 92) {
                createNewScript(newScriptNameInput);
                return;
            }
            if (mouseX >= mx + mw - 72 && mouseX <= mx + mw - 16 && mouseY >= my + 72 && mouseY <= my + 92) {
                isCreatingScript = false;
                newScriptNameInput = "";
                return;
            }
            return;
        }

        int cardX = getCardX();
        int cardY = getCardY();
        int cardW = getCardW();
        int cardH = getCardH();

        int pillX = cardX + 86;
        int pillY = cardY + 4;
        String fileName = (currentFile != null ? currentFile.getName() : "No Script") + (modified ? " *" : "");
        int nameW = fontRendererObj.getStringWidth(fileName);
        int pillW = Math.max(120, nameW + 24);
        int pillH = 20;

        if (isScriptMenuOpen) {
            int menuX = pillX;
            int menuY = pillY + pillH + 2;
            int menuW = Math.max(160, pillW);
            int itemH = 18;
            int visibleCount = Math.min(8, Math.max(1, scriptFiles.size()));
            int menuH = visibleCount * itemH + 6;

            if (mouseX >= menuX && mouseX <= menuX + menuW && mouseY >= menuY && mouseY <= menuY + menuH) {
                int clickedIndex = (mouseY - menuY - 3) / itemH;
                if (clickedIndex >= 0 && clickedIndex < scriptFiles.size()) {
                    loadFile(scriptFiles.get(clickedIndex));
                }
                isScriptMenuOpen = false;
                return;
            }
            isScriptMenuOpen = false;
            if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH) {
                return;
            }
        }

        if (mouseY >= cardY && mouseY <= cardY + TOP_BAR_HEIGHT) {
            if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH) {
                isScriptMenuOpen = !isScriptMenuOpen;
                return;
            }

            int newX = pillX + pillW + 6;
            int newW = 46;
            if (mouseX >= newX && mouseX <= newX + newW && mouseY >= pillY && mouseY <= pillY + pillH) {
                isCreatingScript = true;
                newScriptNameInput = "";
                return;
            }

            int curRight = cardX + cardW - 8;

            curRight -= 24;
            if (mouseX >= curRight && mouseX <= curRight + 22 && mouseY >= pillY && mouseY <= pillY + pillH) {
                mc.displayGuiScreen(parentScreen);
                return;
            }

            curRight -= 52;
            if (mouseX >= curRight && mouseX <= curRight + 48 && mouseY >= pillY && mouseY <= pillY + pillH) {
                openScriptsFolder();
                return;
            }

            if (modified) {
                curRight -= 54;
                if (mouseX >= curRight && mouseX <= curRight + 50 && mouseY >= pillY && mouseY <= pillY + pillH) {
                    if (currentFile != null) {
                        loadFile(currentFile);
                    }
                    return;
                }
            }

            curRight -= 102;
            if (mouseX >= curRight && mouseX <= curRight + 98 && mouseY >= pillY && mouseY <= pillY + pillH) {
                saveAndReload();
                return;
            }
            return;
        }

        int editorX = cardX + 6;
        int editorY = cardY + TOP_BAR_HEIGHT + 2;
        int editorW = cardW - 12;
        int editorH = cardH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 4;

        int trackX = editorX + editorW - SCROLLBAR_WIDTH - 2;
        int trackY = editorY + 2;
        int trackW = SCROLLBAR_WIDTH;
        int trackH = editorH - 4;

        int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
        float contentHeight = Math.max(1, lines.size()) * lineHeight;
        float maxScroll = Math.max(0.0f, contentHeight - editorH + lineHeight);

        if (maxScroll > 0.0f && mouseX >= trackX - 4 && mouseX <= trackX + trackW + 4 && mouseY >= trackY && mouseY <= trackY + trackH) {
            float thumbH = Math.max(22.0f, (float) editorH / contentHeight * trackH);
            float thumbY = trackY + (scrollY / maxScroll) * (trackH - thumbH);
            isDraggingScrollbar = true;
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                dragOffsetThumbY = mouseY - thumbY;
            } else {
                dragOffsetThumbY = thumbH * 0.5f;
                float newThumbY = mouseY - trackY - dragOffsetThumbY;
                float ratio = (trackH > thumbH) ? newThumbY / (trackH - thumbH) : 0.0f;
                scrollY = Math.max(0.0f, Math.min(maxScroll, ratio * maxScroll));
            }
            return;
        }

        if (mouseX >= editorX && mouseX < trackX - 3 && mouseY >= editorY && mouseY < editorY + editorH) {
            int targetRow = (int) ((mouseY - editorY + scrollY) / lineHeight);
            targetRow = Math.max(0, Math.min(targetRow, lines.size() - 1));

            int textStartX = editorX + GUTTER_WIDTH + 6 - (int) scrollX;
            int clickCol = getColumnFromMouseX(targetRow, mouseX - textStartX);

            cursorRow = targetRow;
            cursorCol = clickCol;

            if (isShiftKeyDown()) {
                if (selAnchorRow == -1) {
                    selAnchorRow = targetRow;
                    selAnchorCol = clickCol;
                }
            } else {
                selAnchorRow = cursorRow;
                selAnchorCol = cursorCol;
            }
            isDraggingMouse = true;
            clampCursor();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton != 0) {
            return;
        }
        if (isDraggingScrollbar) {
            updateScrollbarDrag(mouseY);
            return;
        }
        if (isDraggingMouse) {
            int cardX = getCardX();
            int cardY = getCardY();
            int editorX = cardX + 6;
            int editorY = cardY + TOP_BAR_HEIGHT + 2;
            int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
            int targetRow = (int) ((mouseY - editorY + scrollY) / lineHeight);
            targetRow = Math.max(0, Math.min(targetRow, lines.size() - 1));

            int textStartX = editorX + GUTTER_WIDTH + 6 - (int) scrollX;
            int clickCol = getColumnFromMouseX(targetRow, mouseX - textStartX);

            cursorRow = targetRow;
            cursorCol = clickCol;
            clampCursor();
            ensureCursorVisible();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            isDraggingMouse = false;
            isDraggingScrollbar = false;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private int getColumnFromMouseX(int row, int relX) {
        if (row < 0 || row >= lines.size()) {
            return 0;
        }
        String line = lines.get(row);
        if (relX <= 0) {
            return 0;
        }
        int totalW = 0;
        for (int i = 0; i < line.length(); i++) {
            int charW = fontRendererObj.getCharWidth(line.charAt(i));
            if (relX < totalW + charW / 2) {
                return i;
            }
            totalW += charW;
        }
        return line.length();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (isShiftKeyDown()) {
                scrollX = Math.max(0, scrollX + (wheel > 0 ? -36 : 36));
            } else {
                int lineHeight = fontRendererObj != null ? fontRendererObj.FONT_HEIGHT + FONT_PADDING : 12;
                int linesToScroll = 4;
                float d = (wheel > 0) ? -(lineHeight * linesToScroll) : (lineHeight * linesToScroll);
                int cardH = getCardH();
                int editorH = cardH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 4;
                float maxScroll = Math.max(0.0f, lines.size() * lineHeight - editorH + lineHeight);
                scrollY = Math.max(0.0f, Math.min(scrollY + d, maxScroll));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (isCreatingScript) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                isCreatingScript = false;
                newScriptNameInput = "";
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                createNewScript(newScriptNameInput);
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!newScriptNameInput.isEmpty()) {
                    newScriptNameInput = newScriptNameInput.substring(0, newScriptNameInput.length() - 1);
                }
                return;
            }
            if (ChatAllowedCharacters.isAllowedCharacter(typedChar) || typedChar == '_' || typedChar == '-') {
                newScriptNameInput += typedChar;
            }
            return;
        }

        if (isScriptMenuOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                isScriptMenuOpen = false;
                return;
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
            return;
        }

        boolean ctrl = isCtrlKeyDown();
        boolean shift = isShiftKeyDown();

        if (ctrl && keyCode == Keyboard.KEY_S) {
            saveAndReload();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_A) {
            selAnchorRow = 0;
            selAnchorCol = 0;
            cursorRow = Math.max(0, lines.size() - 1);
            cursorCol = lines.isEmpty() ? 0 : lines.get(cursorRow).length();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_C) {
            copySelection();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_X) {
            copySelection();
            deleteSelection();
            pushUndoSnapshot();
            ensureCursorVisible();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_V) {
            pasteFromClipboard();
            pushUndoSnapshot();
            ensureCursorVisible();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_Z) {
            undo();
            return;
        }

        if (ctrl && keyCode == Keyboard.KEY_Y) {
            redo();
            return;
        }

        if (keyCode == Keyboard.KEY_LEFT) {
            handleArrowLeft(shift, ctrl);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            handleArrowRight(shift, ctrl);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            handleArrowUp(shift);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            handleArrowDown(shift);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            handleHome(shift);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            handleEnd(shift);
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            cursorRow = Math.max(0, cursorRow - 20);
            if (!shift) {
                clearSelection();
            }
            clampCursor();
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            cursorRow = Math.min(lines.size() - 1, cursorRow + 20);
            if (!shift) {
                clearSelection();
            }
            clampCursor();
            ensureCursorVisible();
            return;
        }

        if (keyCode == Keyboard.KEY_BACK) {
            handleBackspace();
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            handleDelete();
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            handleEnter();
            ensureCursorVisible();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            handleTab(shift);
            ensureCursorVisible();
            return;
        }

        if (typedChar >= 32 && typedChar != 127) {
            insertCharacter(typedChar);
            ensureCursorVisible();
        }
    }

    private void handleArrowLeft(boolean shift, boolean ctrl) {
        if (!shift && hasSelection()) {
            int minR = Math.min(selAnchorRow, cursorRow);
            int minC = (selAnchorRow == cursorRow) ? Math.min(selAnchorCol, cursorCol) : (selAnchorRow < cursorRow ? selAnchorCol : cursorCol);
            cursorRow = minR;
            cursorCol = minC;
            clearSelection();
            return;
        }
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        if (cursorCol > 0) {
            cursorCol--;
        } else if (cursorRow > 0) {
            cursorRow--;
            cursorCol = lines.get(cursorRow).length();
        }
        if (!shift) {
            clearSelection();
        }
    }

    private void handleArrowRight(boolean shift, boolean ctrl) {
        if (!shift && hasSelection()) {
            int maxR = Math.max(selAnchorRow, cursorRow);
            int maxC = (selAnchorRow == cursorRow) ? Math.max(selAnchorCol, cursorCol) : (selAnchorRow > cursorRow ? selAnchorCol : cursorCol);
            cursorRow = maxR;
            cursorCol = maxC;
            clearSelection();
            return;
        }
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        String currentLine = lines.get(cursorRow);
        if (cursorCol < currentLine.length()) {
            cursorCol++;
        } else if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorCol = 0;
        }
        if (!shift) {
            clearSelection();
        }
    }

    private void handleArrowUp(boolean shift) {
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        if (cursorRow > 0) {
            cursorRow--;
            clampCursor();
        }
        if (!shift) {
            clearSelection();
        }
    }

    private void handleArrowDown(boolean shift) {
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        if (cursorRow < lines.size() - 1) {
            cursorRow++;
            clampCursor();
        }
        if (!shift) {
            clearSelection();
        }
    }

    private void handleHome(boolean shift) {
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        String line = lines.get(cursorRow);
        int firstNonSpace = 0;
        while (firstNonSpace < line.length() && Character.isWhitespace(line.charAt(firstNonSpace))) {
            firstNonSpace++;
        }
        cursorCol = (cursorCol == firstNonSpace) ? 0 : firstNonSpace;
        if (!shift) {
            clearSelection();
        }
    }

    private void handleEnd(boolean shift) {
        if (shift && selAnchorRow == -1) {
            selAnchorRow = cursorRow;
            selAnchorCol = cursorCol;
        }
        cursorCol = lines.get(cursorRow).length();
        if (!shift) {
            clearSelection();
        }
    }

    private void handleBackspace() {
        if (hasSelection()) {
            deleteSelection();
            pushUndoSnapshot();
            return;
        }
        if (cursorCol > 0) {
            String line = lines.get(cursorRow);
            if (cursorCol >= 4 && line.substring(cursorCol - 4, cursorCol).equals("    ")) {
                lines.set(cursorRow, line.substring(0, cursorCol - 4) + line.substring(cursorCol));
                cursorCol -= 4;
            } else {
                lines.set(cursorRow, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
                cursorCol--;
            }
            modified = true;
            pushUndoSnapshot();
        } else if (cursorRow > 0) {
            String prevLine = lines.get(cursorRow - 1);
            String currentLine = lines.get(cursorRow);
            cursorRow--;
            cursorCol = prevLine.length();
            lines.set(cursorRow, prevLine + currentLine);
            lines.remove(cursorRow + 1);
            modified = true;
            pushUndoSnapshot();
        }
    }

    private void handleDelete() {
        if (hasSelection()) {
            deleteSelection();
            pushUndoSnapshot();
            return;
        }
        String line = lines.get(cursorRow);
        if (cursorCol < line.length()) {
            lines.set(cursorRow, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
            modified = true;
            pushUndoSnapshot();
        } else if (cursorRow < lines.size() - 1) {
            lines.set(cursorRow, line + lines.get(cursorRow + 1));
            lines.remove(cursorRow + 1);
            modified = true;
            pushUndoSnapshot();
        }
    }

    private void handleEnter() {
        if (hasSelection()) {
            deleteSelection();
        }
        String currentLine = lines.get(cursorRow);
        String prefix = currentLine.substring(0, cursorCol);
        String suffix = currentLine.substring(cursorCol);

        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < currentLine.length(); i++) {
            char c = currentLine.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        if (prefix.trim().endsWith("{")) {
            indent.append("    ");
        }

        lines.set(cursorRow, prefix);
        cursorRow++;
        cursorCol = indent.length();
        lines.add(cursorRow, indent + suffix);
        modified = true;
        pushUndoSnapshot();
    }

    private void handleTab(boolean shift) {
        if (hasSelection()) {
            int minR = Math.min(selAnchorRow, cursorRow);
            int maxR = Math.max(selAnchorRow, cursorRow);
            for (int r = minR; r <= maxR; r++) {
                String line = lines.get(r);
                if (!shift) {
                    lines.set(r, "    " + line);
                } else {
                    if (line.startsWith("    ")) {
                        lines.set(r, line.substring(4));
                    } else if (line.startsWith(" ")) {
                        lines.set(r, line.replaceFirst("^ +", ""));
                    }
                }
            }
            cursorCol = lines.get(cursorRow).length();
            modified = true;
            pushUndoSnapshot();
            return;
        }
        if (!shift) {
            insertText("    ");
        }
    }

    private void insertCharacter(char c) {
        if (hasSelection()) {
            deleteSelection();
        }
        String line = lines.get(cursorRow);
        String updated = line.substring(0, cursorCol) + c + line.substring(cursorCol);
        lines.set(cursorRow, updated);
        cursorCol++;
        modified = true;
        pushUndoSnapshot();
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (hasSelection()) {
            deleteSelection();
        }
        String[] split = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String currentLine = lines.get(cursorRow);
        String prefix = currentLine.substring(0, cursorCol);
        String suffix = currentLine.substring(cursorCol);

        if (split.length == 1) {
            lines.set(cursorRow, prefix + split[0] + suffix);
            cursorCol += split[0].length();
        } else {
            lines.set(cursorRow, prefix + split[0]);
            for (int i = 1; i < split.length - 1; i++) {
                cursorRow++;
                lines.add(cursorRow, split[i]);
            }
            cursorRow++;
            lines.add(cursorRow, split[split.length - 1] + suffix);
            cursorCol = split[split.length - 1].length();
        }
        modified = true;
        pushUndoSnapshot();
    }

    private boolean hasSelection() {
        return selAnchorRow != -1 && (selAnchorRow != cursorRow || selAnchorCol != cursorCol);
    }

    private void clearSelection() {
        selAnchorRow = -1;
        selAnchorCol = -1;
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        int r1 = selAnchorRow;
        int c1 = selAnchorCol;
        int r2 = cursorRow;
        int c2 = cursorCol;

        if (r1 > r2 || (r1 == r2 && c1 > c2)) {
            int tr = r1; r1 = r2; r2 = tr;
            int tc = c1; c1 = c2; c2 = tc;
        }

        String firstLine = lines.get(r1);
        String lastLine = lines.get(r2);

        String merged = firstLine.substring(0, Math.min(c1, firstLine.length())) +
                lastLine.substring(Math.min(c2, lastLine.length()));

        lines.set(r1, merged);
        for (int i = r2; i > r1; i--) {
            lines.remove(i);
        }

        cursorRow = r1;
        cursorCol = Math.min(c1, merged.length());
        clearSelection();
        modified = true;
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        int r1 = selAnchorRow;
        int c1 = selAnchorCol;
        int r2 = cursorRow;
        int c2 = cursorCol;

        if (r1 > r2 || (r1 == r2 && c1 > c2)) {
            int tr = r1; r1 = r2; r2 = tr;
            int tc = c1; c1 = c2; c2 = tc;
        }

        StringBuilder sb = new StringBuilder();
        if (r1 == r2) {
            String line = lines.get(r1);
            sb.append(line, Math.min(c1, line.length()), Math.min(c2, line.length()));
        } else {
            String first = lines.get(r1);
            sb.append(first.substring(Math.min(c1, first.length()))).append("\n");
            for (int r = r1 + 1; r < r2; r++) {
                sb.append(lines.get(r)).append("\n");
            }
            String last = lines.get(r2);
            sb.append(last, 0, Math.min(c2, last.length()));
        }

        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
    }

    private void pasteFromClipboard() {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                insertText(text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pushUndoSnapshot() {
        undoStack.addLast(new ArrayList<>(lines));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeFirst();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.size() > 1) {
            redoStack.addLast(undoStack.removeLast());
            List<String> prev = undoStack.getLast();
            lines.clear();
            lines.addAll(prev);
            clampCursor();
            modified = true;
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            List<String> next = redoStack.removeLast();
            undoStack.addLast(next);
            lines.clear();
            lines.addAll(next);
            clampCursor();
            modified = true;
        }
    }

    private void clampCursor() {
        cursorRow = Math.max(0, Math.min(cursorRow, lines.size() - 1));
        if (!lines.isEmpty()) {
            cursorCol = Math.max(0, Math.min(cursorCol, lines.get(cursorRow).length()));
        } else {
            cursorCol = 0;
        }
    }

    private void ensureCursorVisible() {
        clampCursor();
        int cardH = getCardH();
        int cardW = getCardW();
        int editorH = cardH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 4;
        int lineHeight = fontRendererObj.FONT_HEIGHT + FONT_PADDING;
        int curY = cursorRow * lineHeight;

        if (curY < scrollY) {
            scrollY = curY;
        } else if (curY + lineHeight > scrollY + editorH) {
            scrollY = curY + lineHeight - editorH;
        }

        if (cursorRow < lines.size()) {
            String line = lines.get(cursorRow);
            int cursorPixelX = fontRendererObj.getStringWidth(line.substring(0, cursorCol));
            int textViewportW = cardW - GUTTER_WIDTH - SCROLLBAR_WIDTH - 24;
            if (cursorPixelX < scrollX) {
                scrollX = cursorPixelX;
            } else if (cursorPixelX > scrollX + textViewportW) {
                scrollX = cursorPixelX - textViewportW;
            }
        }
    }

    private void openScriptsFolder() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(scriptsDirectory);
            }
        } catch (Exception e) {
            setStatus("Unable to open folder", true);
        }
    }

    public static class ChatAllowedCharacters {
        public static boolean isAllowedCharacter(char character) {
            return character >= ' ' && character != 127;
        }
    }
}
