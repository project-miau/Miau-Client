package idle.miau.module.impl.other;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.GuiOpenEvent;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.mcp.gui.GuiButtonExt;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.utility.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

public class HideWindow extends Module {
    private static final String ICON_PATH = "/assets/idle.miau/textures/gui/tv_off.png";
    private static final int ICON_BASE_SIZE = 16;
    private static final float DEFAULT_RELATIVE_X = 0.5F;
    private static final float DEFAULT_RELATIVE_Y = 0.05F;
    private static final int EDIT_OUTLINE_COLOR = -1;

    private final ColorSetting iconColor;
    private final SliderSetting iconScale;
    private final ButtonSetting onlyWhileCrouching;
    private final ButtonSetting whitelist;
    private final StringListSetting whitelistEntries;
    private GuiContainer hiddenGui;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public HideWindow() {
        super("HideWindow", category.other);
        setDescription("Hides container menus while keeping the container open.");
        registerSetting(iconColor = new ColorSetting("Icon color", 255, 255, 255));
        registerSetting(iconScale = new SliderSetting("Icon scale", 1.0, 0.5, 3.0, 0.1));
        registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        registerSetting(onlyWhileCrouching = new ButtonSetting("Only while crouching", false));
        registerSetting(whitelist = new ButtonSetting("Whitelist", false));
        registerSetting(whitelistEntries = new StringListSetting("Whitelist names", "e.g. Upgrades & Traps", 128));
        whitelistEntries.visible = false;
    }

    @Override
    public String getInfo() {
        return hiddenGui == null ? "" : "Hidden";
    }

    @Override
    public void guiUpdate() {
        whitelistEntries.setVisible(whitelist.isToggled(), this);
    }

    @Override
    public void onDisable() {
        if (hiddenGui != null && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
        hiddenGui = null;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui == null || mc.thePlayer == null) {
            return;
        }
        if (event.gui instanceof GuiContainer && !(event.gui instanceof GuiInventory)) {
            if (mc.currentScreen instanceof GuiContainer) {
                hiddenGui = null;
                return;
            }
            GuiContainer gui = (GuiContainer) event.gui;
            if (onlyWhileCrouching.isToggled() && !mc.thePlayer.isSneaking()) {
                return;
            }
            if (whitelist.isToggled() && !matchesWhitelist(gui)) {
                return;
            }
            hiddenGui = gui;
            mc.thePlayer.openContainer = hiddenGui.inventorySlots;
            event.setCanceled(true);
            return;
        }
        if (event.gui instanceof GuiInventory && hiddenGui != null) {
            event.gui = hiddenGui;
            hiddenGui = null;
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S2EPacketCloseWindow) {
            hiddenGui = null;
        }
    }

    @Subscribe
    public void onWorldLoad(WorldEvent.Load event) {
        hiddenGui = null;
    }

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (hiddenGui == null || mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        renderIcon(false);
    }

    public float getPosX() {
        syncPosition();
        return posX;
    }

    public float getPosY() {
        syncPosition();
        return posY;
    }

    public float getRelativePosX() {
        syncPosition();
        return relativePosX;
    }

    public float getRelativePosY() {
        syncPosition();
        return relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPosition();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public void resetPosition() {
        setRelativePosition(DEFAULT_RELATIVE_X, DEFAULT_RELATIVE_Y);
    }

    private void renderIcon(boolean editing) {
        syncPosition();
        ResourceLocation icon = RenderUtils.getIcon(ICON_PATH);
        if (icon == null) {
            return;
        }
        float scale = (float) iconScale.getInput();
        int size = Math.round(ICON_BASE_SIZE * scale);
        float drawX = posX - size / 2.0F;
        float drawY = posY - size / 2.0F;
        RenderUtils.drawIcon(icon, drawX, drawY, size, 0xFF000000 | iconColor.getRGB());
        if (editing) {
            float outLeft = drawX - 2.0F;
            float outTop = drawY - 2.0F;
            float outRight = drawX + size + 2.0F;
            float outBottom = drawY + size + 2.0F;
            RenderUtils.drawRect(outLeft, outTop, outRight, outTop + 1.0F, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outLeft, outBottom - 1.0F, outRight, outBottom, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outLeft, outTop, outLeft + 1.0F, outBottom, EDIT_OUTLINE_COLOR);
            RenderUtils.drawRect(outRight - 1.0F, outTop, outRight, outBottom, EDIT_OUTLINE_COLOR);
        }
    }

    private void syncPosition() {
        syncPosition(new ScaledResolution(mc));
    }

    private void syncPosition(ScaledResolution resolution) {
        int width = Math.max(1, resolution.getScaledWidth());
        int height = Math.max(1, resolution.getScaledHeight());
        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            if (Float.isNaN(posX) || Float.isNaN(posY)) {
                relativePosX = DEFAULT_RELATIVE_X;
                relativePosY = DEFAULT_RELATIVE_Y;
            }
            else {
                relativePosX = posX / width;
                relativePosY = posY / height;
            }
        }
        posX = relativePosX * width;
        posY = relativePosY * height;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;
        int width = Math.max(1, resolution.getScaledWidth());
        int height = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / width;
        relativePosY = absoluteY / height;
    }

    private boolean matchesWhitelist(GuiContainer gui) {
        List<String> entries = whitelistEntries.getEntries();
        if (entries.isEmpty()) {
            return false;
        }
        String title = getContainerTitle(gui);
        if (title.isEmpty()) {
            return false;
        }
        String lowerTitle = title.toLowerCase();
        for (String name : entries) {
            if (lowerTitle.contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String getContainerTitle(GuiContainer gui) {
        if (!(gui.inventorySlots instanceof ContainerChest)) {
            return "";
        }
        return ((ContainerChest) gui.inventorySlots)
                .getLowerChestInventory()
                .getDisplayName()
                .getUnformattedText();
    }

    private final class EditScreen extends GuiScreen {
        private GuiButtonExt resetButton;
        private boolean dragging;
        private float actualX;
        private float actualY;
        private float lastActualX;
        private float lastActualY;
        private int lastMouseX;
        private int lastMouseY;

        @Override
        public void initGui() {
            super.initGui();
            buttonList.add(resetButton = new GuiButtonExt(1, width - 90, height - 25, 85, 20, "Reset position"));
            HideWindow.this.syncPosition(new ScaledResolution(mc));
            actualX = posX;
            actualY = posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(mc);
            if (!dragging) {
                HideWindow.this.syncPosition(resolution);
                actualX = posX;
                actualY = posY;
            }
            Gui.drawRect(0, 0, width, height, -1308622848);
            HideWindow.this.setAbsolutePosition(actualX, actualY, resolution);
            HideWindow.this.renderIcon(true);
            actualX = posX;
            actualY = posY;
            String message = "Drag the icon to reposition it.";
            int textX = resolution.getScaledWidth() / 2 - fontRendererObj.getStringWidth(message) / 2;
            int textY = resolution.getScaledHeight() / 2 - 10;
            RenderUtils.drawColoredString(message, '-', textX, textY, 2L, 0L, true, fontRendererObj);
            try {
                handleInput();
            }
            catch (IOException ignored) {
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
            if (button != 0) {
                return;
            }
            if (dragging) {
                actualX = lastActualX + mouseX - lastMouseX;
                actualY = lastActualY + mouseY - lastMouseY;
                return;
            }
            float scale = (float) iconScale.getInput();
            int size = Math.round(ICON_BASE_SIZE * scale);
            float minX = posX - size / 2.0F - 2.0F;
            float minY = posY - size / 2.0F - 2.0F;
            float maxX = posX + size / 2.0F + 2.0F;
            float maxY = posY + size / 2.0F + 2.0F;
            if (mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY) {
                dragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                lastActualX = actualX;
                lastActualY = actualY;
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                dragging = false;
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button == resetButton) {
                resetPosition();
                actualX = posX;
                actualY = posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
