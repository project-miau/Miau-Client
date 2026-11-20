package idle.miau.clickgui.miau;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.module.Module;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.config.Config;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.RoundedUtils;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiauCategoryFrame {
    private static final int HEADER_HEIGHT = 19;
    private static final float CORNER_RADIUS = 4.0F;
    private static final int CONTENT_BOTTOM_INSET = 3;
    private static final long OPEN_ANIMATION_MS = 220L;
    private static long interactionSequence;

    private final Module.category category;
    private final int width;
    private final List<MiauModuleRow> modules = new ArrayList<MiauModuleRow>();
    private final MiauFloatAnimation openAnimation = new MiauFloatAnimation(0.0F);
    private int x;
    private int y;
    private int renderY;
    private int dragX;
    private int dragY;
    private int viewportHeight;
    private int contentHeight;
    private float scroll;
    private float layoutOffsetY;
    private boolean opened;
    private boolean dragging;
    private long interactionOrder;

    MiauCategoryFrame(Module.category category, int width) {
        this.category = category;
        this.width = width;
        bringToFront();
    }

    void draw(int mouseX, int mouseY, float openingProgress) {
        renderY = Math.round((y + layoutOffsetY) * openingProgress);

        MiauFontRenderer titleFont = MiauStyle.categoryFont();
        String title = MiauStyle.displayName(category.name());
        int textY = MiauStyle.centeredTextY(renderY, HEADER_HEIGHT, titleFont);
        if (viewportHeight <= 0) {
            RoundedUtils.drawRound(x, renderY, width, HEADER_HEIGHT, CORNER_RADIUS, MiauStyle.DARK);
            titleFont.drawString(title, x + 6, textY, MiauStyle.WHITE, true);
            drawCategoryIcon();
            return;
        }

        RoundedUtils.drawRound(x, renderY, width,
                HEADER_HEIGHT + viewportHeight + CONTENT_BOTTOM_INSET, CORNER_RADIUS, MiauStyle.FRAME);
        RoundedUtils.drawRoundedRectRise(x, renderY, width, HEADER_HEIGHT, CORNER_RADIUS, MiauStyle.DARK,
                true, true, false, false);
        Gui.drawRect(x, renderY + HEADER_HEIGHT - 1, x + width, renderY + HEADER_HEIGHT, MiauStyle.SEPARATOR);
        titleFont.drawString(title, x + 6, textY, MiauStyle.WHITE, true);
        drawCategoryIcon();

        RenderUtils.scissorPushGui(x, renderY + HEADER_HEIGHT, width, viewportHeight);
        int rowY = renderY + HEADER_HEIGHT - Math.round(scroll);
        for (int index = 0; index < modules.size(); index++) {
            MiauModuleRow module = modules.get(index);
            module.setStripeIndex(index);
            module.setBounds(x, rowY, width);
            module.draw(mouseX, mouseY);
            rowY += module.getHeight();
        }
        RenderUtils.scissorPop();
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (MiauStyle.hovered(x, renderY, width, HEADER_HEIGHT, mouseX, mouseY)) {
            clearTextFocus();
            if (button == 1) {
                opened = !opened;
                clampScroll();
                Miau.markCurrentConfigUnsaved();
            }
            else if (button == 0) {
                y = Math.round(y + layoutOffsetY);
                layoutOffsetY = 0.0F;
                renderY = y;
                dragging = true;
                dragX = x - mouseX;
                dragY = y - mouseY;
            }
            return button == 0 || button == 1;
        }
        if (!opened || !isInsideViewport(mouseX, mouseY)) {
            return false;
        }
        for (MiauModuleRow module : modules) {
            if (module.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        clearTextFocus();
        return true;
    }

    boolean mouseReleased(int mouseX, int mouseY, int button) {
        boolean wasDragging = dragging;
        dragging = false;
        for (MiauModuleRow module : modules) {
            module.mouseReleased(mouseX, mouseY, button);
        }
        return wasDragging;
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!opened) {
            return false;
        }
        for (MiauModuleRow module : modules) {
            if (module.keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return false;
    }

    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (!opened || !isInsideViewport(mouseX, mouseY)) {
            return false;
        }
        for (MiauModuleRow module : modules) {
            if (module.mouseWheel(mouseX, mouseY, wheel)) {
                return true;
            }
        }
        scroll -= ClickGuiInput.scrollPixels(
                wheel, idle.miau.module.impl.client.ClickGui.scrollSpeed.getInput());
        clampScroll();
        return true;
    }

    void syncModules() {
        List<Module> current = getCurrentModules();
        current.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, MiauModuleRow> previous = new HashMap<String, MiauModuleRow>();
        for (MiauModuleRow row : modules) {
            previous.put(row.getModule().getConfigId() + "\u0000" + row.getModule().getName(), row);
            previous.put(row.getModule().getName(), row);
        }
        modules.clear();
        for (Module module : current) {
            MiauModuleRow old = previous.get(module.getConfigId() + "\u0000" + module.getName());
            if (old == null) {
                old = previous.get(module.getName());
            }
            MiauModuleRow row = new MiauModuleRow(module);
            if (old != null) {
                row.copyUiState(old);
            }
            modules.add(row);
        }
        clampScroll();
    }

    void clearTextFocus() {
        for (MiauModuleRow module : modules) {
            module.clearTextFocus();
        }
    }

    void onGuiClosed() {
        dragging = false;
        for (MiauModuleRow module : modules) {
            module.onGuiClosed();
        }
        openAnimation.snap(opened ? 1.0F : 0.0F);
    }

    void stopDragging() {
        dragging = false;
    }

    void update(int mouseX, int mouseY, int screenHeight) {
        if (dragging) {
            x = mouseX + dragX;
            y = mouseY + dragY;
        }
        contentHeight = calculateContentHeight();
        openAnimation.animateTo(opened ? 1.0F : 0.0F, OPEN_ANIMATION_MS);
        float progress = openAnimation.get();
        int effectiveY = Math.round(y + layoutOffsetY);
        int availableBody = Math.max(0, screenHeight - effectiveY - 10 - HEADER_HEIGHT);
        viewportHeight = Math.min(Math.round(contentHeight * progress), availableBody);
        clampScroll();
    }

    void setLayoutOffsetY(float offset) {
        layoutOffsetY = Math.max(0.0F, offset);
    }

    float getAnimatedHeight() {
        return HEADER_HEIGHT + viewportHeight + CONTENT_BOTTOM_INSET;
    }

    boolean isDragging() {
        return dragging;
    }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        layoutOffsetY = 0.0F;
    }

    void clampToScreen(int screenWidth, int screenHeight) {
        x = Math.max(0, Math.min(x, Math.max(0, screenWidth - width)));
        y = Math.max(0, Math.min(y, Math.max(0, screenHeight - HEADER_HEIGHT)));
    }

    void bringToFront() {
        interactionOrder = ++interactionSequence;
    }

    long getInteractionOrder() {
        return interactionOrder;
    }

    public Module.category getCategory() {
        return category;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public boolean isOpened() {
        return opened;
    }

    public void applyState(int x, int y, boolean opened) {
        this.x = x;
        this.y = y;
        this.opened = opened;
        layoutOffsetY = 0.0F;
        contentHeight = calculateContentHeight();
        openAnimation.snap(opened ? 1.0F : 0.0F);
        viewportHeight = opened ? contentHeight : 0;
    }

    private int calculateContentHeight() {
        int total = 0;
        for (MiauModuleRow module : modules) {
            total += module.getHeight();
        }
        return total;
    }

    private void clampScroll() {
        scroll = Math.max(0.0F, Math.min(scroll, Math.max(0, contentHeight - viewportHeight)));
    }

    private boolean isInsideViewport(int mouseX, int mouseY) {
        return MiauStyle.hovered(x, renderY + HEADER_HEIGHT, width, viewportHeight, mouseX, mouseY);
    }

    private List<Module> getCurrentModules() {
        if (Miau.moduleManager == null) {
            return Collections.emptyList();
        }
        if (category != Module.category.configs) {
            return new ArrayList<Module>(Miau.moduleManager.inCategory(category));
        }
        List<Module> result = new ArrayList<Module>();
        if (Miau.configManager != null) {
            for (Config config : Miau.configManager.configs) {
                result.add(config.getModule());
            }
        }
        return result;
    }

    private void drawCategoryIcon() {
        String icon = iconName(category);
        int color = MiauStyle.accent(x + width * 0.5D, renderY + HEADER_HEIGHT * 0.5D);
        if (!MiauStyle.drawTexture(icon, x + width - 15,
                MiauStyle.centeredTextY(renderY, HEADER_HEIGHT, MiauStyle.categoryFont()) - 1, 9, 9, color)) {
            Gui.drawRect(x + width - 14, renderY + HEADER_HEIGHT / 2 - 4,
                    x + width - 5, renderY + HEADER_HEIGHT / 2 + 4, color);
        }
    }

    private static String iconName(Module.category category) {
        switch (category) {
            case movement:
                return "move";
            case render:
            case fun:
                return "visuals";
            case scripts:
            case configs:
                return "script";
            case combat:
                return "combat";
            case player:
                return "player";
            case world:
            case minigames:
            case bedwar_utils:
                return "world";
            default:
                return "exploit";
        }
    }
}
