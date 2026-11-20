package idle.miau.clickgui;

import idle.miau.Miau;
import idle.miau.clickgui.components.Component;
import idle.miau.clickgui.components.FocusableTextComponent;
import idle.miau.clickgui.components.impl.BindComponent;
import idle.miau.clickgui.components.impl.CategoryComponent;
import idle.miau.clickgui.components.impl.ModuleComponent;
import idle.miau.clickgui.modern.MiauClickGui;
import idle.miau.module.Module;
import idle.miau.utility.Timer;
import idle.miau.utility.Utils;
import idle.miau.utility.shader.BlurUtils;
import idle.miau.utility.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import idle.miau.mcp.event.client.GuiScreenEvent;
import idle.miau.mcp.McpRuntime;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClickGui extends GuiScreen {
    private ScheduledFuture sf;
    private Timer logoSmoothWidth;
    private Timer logoSmoothLength;
    private Timer smoothEntity;
    private Timer backgroundFade;
    private Timer blurSmooth;
    private ScaledResolution sr;
    public static ArrayList<CategoryComponent> categories;
    private int actualScreenWidth;
    private int actualScreenHeight;
    private double previousScale;
    private static boolean isNotFirstOpen;
    private boolean pendingScaleRefresh;
    private boolean blurAvailable = true;

    public ClickGui() {
        categories = new ArrayList();
        int y = 5;
        for (Module.category c : GuiCategories.all()) {
            CategoryComponent categoryComponent = new CategoryComponent(c);
            categoryComponent.setY(y, false);
            categories.add(categoryComponent);
            y += 20;
        }
    }

    private void resolveCategoryOverlaps() {
        List<CategoryComponent> sorted = new ArrayList<>(categories);
        sorted.sort(Comparator.comparingDouble(CategoryComponent::getY));
        for (int index = 0; index < sorted.size(); index++) {
            CategoryComponent current = sorted.get(index);
            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                CategoryComponent previous = sorted.get(previousIndex);
                float horizontalOverlap =
                        Math.min(previous.getX() + previous.getWidth(), current.getX() + current.getWidth())
                                - Math.max(previous.getX(), current.getX());
                if (horizontalOverlap < Math.min(previous.getWidth(), current.getWidth()) * 0.5f) {
                    continue;
                }
                float previousBottom = previous.getY() + previous.getHeight();
                if (current.getY() >= previousBottom) {
                    continue;
                }
                current.setY(previousBottom, true);
            }
        }
    }

    public void initMain() {
        (this.logoSmoothWidth = this.smoothEntity = this.blurSmooth = this.backgroundFade = new Timer(500.0F)).start();
        this.sf = Miau.getScheduledExecutor().schedule(() -> mc.addScheduledTask(() -> {
            if (mc.currentScreen == this) {
                (this.logoSmoothLength = new Timer(650.0F)).start();
            }
        }), 650L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void initGui() {
        super.initGui();
        double configuredScale = getConfiguredGuiScale();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = configuredScale;
        }
        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
        }
        resolveCategoryOverlaps();
        if (Double.compare(this.previousScale, configuredScale) != 0) {
            for (CategoryComponent categoryComponent : categories) {
                categoryComponent.limitPositions();
            }
        }
        for (CategoryComponent categoryComponent : categories) {
            if (categoryComponent.category == Module.category.configs) {
                categoryComponent.reloadModules(true);
            } else if (categoryComponent.category == Module.category.official_config) {
                categoryComponent.reloadOfficialConfigs();
            } else {
                categoryComponent.reloadModules();
            }
        }
        this.previousScale = configuredScale;
    }
    private List<CategoryComponent> getCategoriesInRenderOrder() {
        List<CategoryComponent> renderOrder = new ArrayList<>(categories);
        renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
        return renderOrder;
    }
    private CategoryComponent getTopmostUnderCursor(List<CategoryComponent> renderOrder, int x, int y) {
        for (int i = renderOrder.size() - 1; i >= 0; i--) {
            if (renderOrder.get(i).overRect(x, y)) {
                return renderOrder.get(i);
            }
        }
        return null;
    }

    public void drawScreen(int x, int y, float p) {
        int logicalMouseX = toLogicalCoordinate(x);
        int logicalMouseY = toLogicalCoordinate(y);

        if (idle.miau.module.impl.client.ClickGui.backgroundBlur.getInput() != 0 && blurAvailable) {
            try {
                if (BlurUtils.prepareBlur()) {
                    RoundedUtils.drawRound(0, 0, this.actualScreenWidth, this.actualScreenHeight, 0.0f, true, Color.black);
                    float inputToRange = (float) (3 * ((idle.miau.module.impl.client.ClickGui.backgroundBlur.getInput() + 35) / 100));
                    blurAvailable = BlurUtils.blurEnd(2, this.blurSmooth.getValueFloat(0, inputToRange, 1));
                    if (!blurAvailable) {
                        mc.getFramebuffer().bindFramebuffer(true);
                    }
                }
                else {
                    blurAvailable = false;
                }
            }
            catch (RuntimeException | LinkageError exception) {
                blurAvailable = false;
                Utils.log.warn("ClickGUI blur was disabled", exception);
                mc.getFramebuffer().bindFramebuffer(true);
            }
        }
        if (idle.miau.module.impl.client.ClickGui.darkBackground.isToggled()) {
            drawRect(0, 0, this.actualScreenWidth, this.actualScreenHeight, (int) (this.backgroundFade.getValueFloat(0.0F, 0.7F, 2) * 255.0F) << 24);
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(getRenderScale(), getRenderScale(), 1.0D);

        int r;
        if (!idle.miau.module.impl.client.ClickGui.removeWatermark.isToggled()) {
            int h = this.height / 4;
            int wd = this.width / 2;
            int w_c = 30 - this.logoSmoothWidth.getValueInt(0, 30, 3);
            this.drawCenteredString(this.fontRendererObj, "m", wd + 1 - w_c, h - 15, Utils.getChroma(2L, 1500L));
            this.drawCenteredString(this.fontRendererObj, "i", wd - w_c, h - 5, Utils.getChroma(2L, 1000L));
            this.drawCenteredString(this.fontRendererObj, "a", wd - w_c, h + 5, Utils.getChroma(2L, 500L));
            this.drawCenteredString(this.fontRendererObj, "u", wd + 1 + w_c, h + 20, Utils.getChroma(2L, 0L));
            this.drawVerticalLine(wd - 10 - w_c, h - 30, h + 43, Color.white.getRGB());
            this.drawVerticalLine(wd + 10 + w_c, h - 30, h + 43, Color.white.getRGB());
            if (this.logoSmoothLength != null) {
                r = this.logoSmoothLength.getValueInt(0, 20, 2);
                this.drawHorizontalLine(wd - 10, wd - 10 + r, h - 29, -1);
                this.drawHorizontalLine(wd + 10, wd + 10 - r, h + 42, -1);
            }
        }

        List<CategoryComponent> renderOrder = getCategoriesInRenderOrder();
        CategoryComponent topmostUnderCursor = getTopmostUnderCursor(renderOrder, logicalMouseX, logicalMouseY);
        for (CategoryComponent c : renderOrder) {
            c.render(this.fontRendererObj);
            c.mousePosition(logicalMouseX, logicalMouseY, c == topmostUnderCursor);

            for (Component m : c.getModules()) {
                m.drawScreen(logicalMouseX, logicalMouseY);
            }
        }

        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        if (!idle.miau.module.impl.client.ClickGui.removePlayerModel.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableBlend();
            GuiInventory.drawEntityOnScreen(this.width + 15 - this.smoothEntity.getValueInt(0, 40, 2), this.height - 10, 40, (float) (this.width - 25 - logicalMouseX), (float) (this.height - 50 - logicalMouseY), this.mc.thePlayer);
            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));
        CategoryComponent topmostCategory = null;
        for (CategoryComponent category : inputOrder) {
            if (category.overRect(mouseX, mouseY)) {
                topmostCategory = category;
                break;
            }
        }

        if (topmostCategory != null) {
            topmostCategory.markInteracted();
        }

        if (mouseButton == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
            }
            if (topmostCategory != null && topmostCategory.draggable(mouseX, mouseY)) {
                topmostCategory.overTitle(true);
                topmostCategory.xx = mouseX - topmostCategory.getX();
                topmostCategory.yy = mouseY - topmostCategory.getY();
                topmostCategory.dragging = true;
            }
        }

        if (mouseButton == 1 && topmostCategory != null && topmostCategory.overTitle(mouseX, mouseY)) {
            topmostCategory.mouseClicked(!topmostCategory.isOpened());
            Miau.markCurrentConfigUnsaved();
        }

        if (topmostCategory != null && topmostCategory.isOpened() && !topmostCategory.getModules().isEmpty() && !topmostCategory.overTitle(mouseX, mouseY)) {
            for (ModuleComponent component : topmostCategory.getModules()) {
                if (component.onClick(mouseX, mouseY, mouseButton)) {
                    break;
                }
            }
        }

        if (mouseButton == 0 || mouseButton == 1) {
            FocusableTextComponent focusedComponent = findFocusedTextComponentAt(mouseX, mouseY);
            enforceSingleFocusedTextInput(focusedComponent);
        }
    }

    public void mouseReleased(int x, int y, int button) {
        boolean categoryMoved = false;
        if (button == 0) {
            for (CategoryComponent category : categories) {
                categoryMoved |= category.dragging;
                category.overTitle(false);
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.mouseReleased(x, y, button);
                    }
                }
            }
        }
        if (categoryMoved) {
            Miau.markCurrentConfigUnsaved();
        }
        if (pendingScaleRefresh) {
            pendingScaleRefresh = false;
            refreshLayoutForConfiguredScale();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheelInput = Mouse.getEventDWheel();
        if (wheelInput != 0) {
            int mouseX = ClickGuiInput.eventMouseX(Mouse.getEventX(), mc.displayWidth, width);
            int mouseY = ClickGuiInput.eventMouseY(Mouse.getEventY(), mc.displayHeight, height);
            CategoryComponent category = getTopmostUnderCursor(
                    getCategoriesInRenderOrder(), mouseX, mouseY);
            if (category != null) {
                category.onScroll(wheelInput, mouseX, mouseY);
            }
        }
    }
    public void refreshAfterConfigLoad() {
        if (mc == null) {
            mc = Minecraft.getMinecraft();
        }
        refreshLayoutForConfiguredScale();
    }

    @Override
    public void setWorldAndResolution(Minecraft p_setWorldAndResolution_1_, final int p_setWorldAndResolution_2_, final int p_setWorldAndResolution_3_) {
        this.mc = p_setWorldAndResolution_1_;
        this.itemRender = p_setWorldAndResolution_1_.getRenderItem();
        this.fontRendererObj = p_setWorldAndResolution_1_.fontRendererObj;
        refreshScaledResolution();
        if (!McpRuntime.EVENTS.post(new GuiScreenEvent.InitGuiEvent.Pre(this, this.buttonList))) {
            this.buttonList.clear();
            this.initGui();
        }
        McpRuntime.EVENTS.post(new GuiScreenEvent.InitGuiEvent.Post(this, this.buttonList));
    }

    @Override
    public void keyTyped(char t, int k) {
        FocusableTextComponent activeTextInput = getActiveFocusedTextInput();
        if (k == Keyboard.KEY_ESCAPE) {
            if (activeTextInput != null) {
                activeTextInput.unfocusTextInput();
                return;
            }
            if (!binding()) {
                this.mc.displayGuiScreen(null);
                return;
            }
        }

        if (activeTextInput != null) {
            for (CategoryComponent category : categories) {
                if (!category.isOpened() || category.getModules().isEmpty()) {
                    continue;
                }
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
            return;
        }

        for (CategoryComponent category : categories) {
            if (category.isOpened() && !category.getModules().isEmpty()) {
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
        }
    }

    @Override
    public void onGuiClosed() {
        this.logoSmoothLength = null;
        if (this.sf != null) {
            this.sf.cancel(true);
            this.sf = null;
        }
        for (CategoryComponent c : categories) {
            c.dragging = false;
            c.onGuiClosed();
            for (Component m : c.getModules()) {
                m.onGuiClosed();
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean binding() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                for (Component component : m.settings) {
                    if (component instanceof BindComponent && ((BindComponent) component).isBinding) {
                        return true;
                    }
                    if (component instanceof FocusableTextComponent && ((FocusableTextComponent) component).isTextInputFocused()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean unfocusFocusedTextInput() {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private FocusableTextComponent getActiveFocusedTextInput() {
        FocusableTextComponent activeComponent = null;
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused()) {
                            if (activeComponent == null) {
                                activeComponent = textComponent;
                            }
                            else {
                                textComponent.unfocusTextInput();
                            }
                        }
                    }
                }
            }
        }
        return activeComponent;
    }

    private FocusableTextComponent findFocusedTextComponentAt(int mouseX, int mouseY) {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));

        for (CategoryComponent category : inputOrder) {
            if (!category.isOpened() || !category.overRect(mouseX, mouseY)) {
                continue;
            }

            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent.isTextInputFocused() && textComponent.containsClick(mouseX, mouseY)) {
                            return textComponent;
                        }
                    }
                }
            }
        }

        return null;
    }

    private void enforceSingleFocusedTextInput(FocusableTextComponent focusedComponentToKeep) {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent textComponent = (FocusableTextComponent) component;
                        if (textComponent != focusedComponentToKeep && textComponent.isTextInputFocused()) {
                            textComponent.unfocusTextInput();
                        }
                    }
                }
            }
        }
    }

    public void onSliderChange() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                m.onSliderChange();
            }
        }
    }

    public void requestScaleRefresh() {
        this.pendingScaleRefresh = true;
    }

    private void refreshLayoutForConfiguredScale() {
        refreshScaledResolution();
        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenSize(this.width, this.height);
            categoryComponent.limitPositions();
        }
        this.buttonList.clear();
        initGui();
    }

    private void refreshScaledResolution() {
        this.sr = new ScaledResolution(mc);
        this.actualScreenWidth = this.sr.getScaledWidth();
        this.actualScreenHeight = this.sr.getScaledHeight();

        double targetScaleFactor = getTargetGuiScaleFactor();
        this.width = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayWidth / targetScaleFactor));
        this.height = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayHeight / targetScaleFactor));
    }

    private int getMaximumGuiScaleFactor() {
        int scaleFactor = 1;
        while (mc.displayWidth / (scaleFactor + 1) >= 320 && mc.displayHeight / (scaleFactor + 1) >= 240) {
            ++scaleFactor;
        }

        if (mc.isUnicode() && scaleFactor % 2 != 0 && scaleFactor != 1) {
            --scaleFactor;
        }

        return scaleFactor;
    }

    private double getTargetGuiScaleFactor() {
        return Math.max(1.0D, Math.min(getMaximumGuiScaleFactor(), getConfiguredGuiScale() * 2.0D));
    }

    private int toLogicalCoordinate(int coordinate) {
        return (int) Math.floor(coordinate / getRenderScale());
    }

    private double getRenderScale() {
        return actualScreenWidth <= 0 || width <= 0 ? 1.0D : (double) actualScreenWidth / (double) width;
    }

    public static double getActiveRenderScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return 1.0D;
        }
        if (minecraft.currentScreen instanceof ClickGui) {
            return ((ClickGui) minecraft.currentScreen).getRenderScale();
        }
        if (minecraft.currentScreen instanceof MiauClickGui miau) {
            return miau.getRenderScale();
        }
        return 1.0D;
    }

    private double getConfiguredGuiScale() {
        return idle.miau.module.impl.client.ClickGui.getClickGuiScale();
    }
}
