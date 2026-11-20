package idle.miau.module.impl.movement;

import idle.miau.clickgui.ClickGui;
import idle.miau.clickgui.modern.MiauClickGui;
import idle.miau.event.PostSprintUpdateEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InvMove extends Module {
    private static final int LEGIT_MODE = 0;
    private static final int HYPIXEL_MODE = 1;
    private static final int GRIM_MODE = 2;
    private static final String[] MODES = {"Legit", "Hypixel", "Grim"};

    private final SliderSetting mode;
    private final ButtonSetting clickGui;
    private final ButtonSetting lockMoveKey;
    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private final Map<KeyBinding, Boolean> movementKeys = new LinkedHashMap<>();
    private KeyBinding[] grimMovementKeys = new KeyBinding[0];

    private boolean keysPressed;
    private boolean grimScreenActive;
    private C16PacketClientStatus pendingStatus;
    private int delayTicks;

    public InvMove() {
        super("InvMove", category.movement);
        setDescription("Allows movement while selected menus are open.");
        registerSetting(mode = new SliderSetting("Mode", LEGIT_MODE, MODES));
        registerSetting(clickGui = new ButtonSetting("Click GUI", true));
        registerSetting(lockMoveKey = new ButtonSetting("Lock move key", false));

        initializeMovementKeys();
    }

    @Override
    public String getInfo() {
        return MODES[currentMode()];
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            resetRuntimeState();
        }
    }

    @Override
    public void onDisable() {
        if (mc != null && keysPressed && mc.currentScreen != null) {
            KeyBinding.unPressAllKeys();
        }
        keysPressed = false;
        grimScreenActive = false;
        flushClickQueue();
        sendPendingStatus();
        delayTicks = 0;
        resetMovementKeys();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            flushClickQueue();
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.currentScreen instanceof MiauClickGui miau && !miau.allowsMovement()) {
            releaseManagedKeys();
            return;
        }
        if (currentMode() == GRIM_MODE) {
            updateGrimMovement();
            return;
        }

        if (isClickGuiScreen() && clickGui.isToggled()) {
            pressMovementKeys(true);
            return;
        }

        if (canInvWalk()) {
            if (hasStoredMovementKeys() && lockMoveKey.isToggled()) {
                restoreMovementKeys();
            } else {
                pressMovementKeys(true);
            }
        } else {
            releaseManagedKeys();
            sendPendingStatus();
            if (delayTicks > 0) {
                delayTicks--;
            }
        }
    }

    @Subscribe
    public void onPostSprintUpdate(PostSprintUpdateEvent event) {
        if (currentMode() == GRIM_MODE
                && (mc.currentScreen instanceof GuiInventory
                || mc.currentScreen instanceof GuiChest)) {
            mc.thePlayer.setSprinting(false);
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (currentMode() == GRIM_MODE) {
            return;
        }

        if (event.getPacket() instanceof C16PacketClientStatus) {
            handleClientStatus(event, (C16PacketClientStatus) event.getPacket());
            return;
        }
        if (event.getPacket() instanceof C0DPacketCloseWindow) {
            handleCloseWindow(event, (C0DPacketCloseWindow) event.getPacket());
            return;
        }
        if (event.getPacket() instanceof C0EPacketClickWindow) {
            handleClickWindow(event, (C0EPacketClickWindow) event.getPacket());
        }
    }

    private void handleClientStatus(
            SendPacketEvent event, C16PacketClientStatus packet) {
        storeMovementKeys();
        if (currentMode() == LEGIT_MODE
                && packet.getStatus()
                == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            event.setCanceled(true);
            pendingStatus = packet;
        }
    }

    private void handleCloseWindow(
            SendPacketEvent event, C0DPacketCloseWindow packet) {
        if (packet.getWindowId() == 0 && pendingStatus != null) {
            pendingStatus = null;
            event.setCanceled(true);
        }
    }

    private void handleClickWindow(
            SendPacketEvent event, C0EPacketClickWindow packet) {
        if (isOutsideDrop(packet)
                && (currentMode() == HYPIXEL_MODE || packet.getWindowId() == 0)) {
            event.setCanceled(true);
            return;
        }

        if (currentMode() == LEGIT_MODE) {
            if (packet.getWindowId() == 0 && pendingStatus != null) {
                queueClick(event, packet);
            }
        } else if (currentMode() == HYPIXEL_MODE) {
            queueClick(event, packet);
            delayTicks = 8;
        }

        sendPendingStatus();
    }

    private void queueClick(SendPacketEvent event, C0EPacketClickWindow packet) {
        KeyBinding.unPressAllKeys();
        event.setCanceled(true);
        clickQueue.offer(packet);
    }

    private boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)
                || mc.currentScreen instanceof GuiContainerCreative) {
            return false;
        }
        if (currentMode() == LEGIT_MODE) {
            return mc.currentScreen instanceof GuiInventory
                    && pendingStatus != null
                    && clickQueue.isEmpty();
        }
        return currentMode() == HYPIXEL_MODE
                && delayTicks == 0
                && clickQueue.isEmpty();
    }

    private void pressMovementKeys(boolean skipSneak) {
        if (!initializeMovementKeys()) {
            return;
        }
        for (KeyBinding key : movementKeys.keySet()) {
            if (!skipSneak || key != mc.gameSettings.keyBindSneak) {
                setKeyState(key, Utils.isBindDown(key));
            }
        }
        if (ModuleManager.sprint != null && ModuleManager.sprint.isEnabled()) {
            setKeyState(mc.gameSettings.keyBindSprint, true);
        }
        keysPressed = true;
    }

    private void storeMovementKeys() {
        if (!initializeMovementKeys()) {
            return;
        }
        for (Map.Entry<KeyBinding, Boolean> entry : movementKeys.entrySet()) {
            entry.setValue(Utils.isBindDown(entry.getKey()));
        }
    }

    private void restoreMovementKeys() {
        if (!initializeMovementKeys()) {
            return;
        }
        for (Map.Entry<KeyBinding, Boolean> entry : movementKeys.entrySet()) {
            setKeyState(entry.getKey(), entry.getValue());
        }
        if (ModuleManager.sprint != null && ModuleManager.sprint.isEnabled()) {
            setKeyState(mc.gameSettings.keyBindSprint, true);
        }
        keysPressed = true;
    }

    private void releaseManagedKeys() {
        if (!keysPressed) {
            return;
        }
        if (mc.currentScreen != null) {
            KeyBinding.unPressAllKeys();
        } else if (hasStoredMovementKeys()) {
            resetMovementKeys();
            pressMovementKeys(false);
        }
        keysPressed = false;
    }

    private void updateGrimMovement() {
        if (!initializeMovementKeys()) {
            return;
        }
        if (mc.currentScreen == null) {
            stopGrimMovement(true);
            return;
        }
        if (mc.currentScreen instanceof GuiChat
                || isClickGuiScreen() && !clickGui.isToggled()) {
            stopGrimMovement(false);
            return;
        }
        if (!grimScreenActive) {
            storeMovementKeys();
            grimScreenActive = true;
        }
        for (KeyBinding key : grimMovementKeys) {
            boolean pressed = lockMoveKey.isToggled() && hasStoredMovementKeys()
                    ? movementKeys.get(key)
                    : Utils.isBindDown(key);
            setKeyState(key, pressed);
        }
        keysPressed = true;
    }

    private void stopGrimMovement(boolean restorePhysicalInput) {
        if (grimScreenActive) {
            for (KeyBinding key : grimMovementKeys) {
                setKeyState(key, restorePhysicalInput && Utils.isBindDown(key));
            }
        }
        grimScreenActive = false;
        keysPressed = false;
        resetMovementKeys();
    }

    private void flushClickQueue() {
        while (!clickQueue.isEmpty()) {
            PacketUtils.sendPacketNoEvent(clickQueue.poll());
        }
    }

    private void sendPendingStatus() {
        if (pendingStatus != null) {
            PacketUtils.sendPacketNoEvent(pendingStatus);
            pendingStatus = null;
        }
    }

    private void resetRuntimeState() {
        flushClickQueue();
        sendPendingStatus();
        delayTicks = 0;
        releaseManagedKeys();
        grimScreenActive = false;
    }

    private void resetMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> entry : movementKeys.entrySet()) {
            entry.setValue(false);
        }
    }

    private boolean hasStoredMovementKeys() {
        for (Boolean pressed : movementKeys.values()) {
            if (pressed) {
                return true;
            }
        }
        return false;
    }

    private boolean isClickGuiScreen() {
        return mc.currentScreen instanceof ClickGui
                || mc.currentScreen instanceof MiauClickGui;
    }

    private static boolean isOutsideDrop(C0EPacketClickWindow packet) {
        return (packet.getMode() == 3 || packet.getMode() == 4)
                && packet.getSlotId() == -999;
    }

    private static void setKeyState(KeyBinding key, boolean pressed) {
        KeyBinding.setKeyBindState(key.getKeyCode(), pressed);
    }

    private int currentMode() {
        return Math.max(0, Math.min((int) mode.getInput(), MODES.length - 1));
    }

    private boolean initializeMovementKeys() {
        if (mc == null || mc.gameSettings == null) {
            return false;
        }
        if (!movementKeys.isEmpty()) {
            return true;
        }
        movementKeys.put(mc.gameSettings.keyBindForward, false);
        movementKeys.put(mc.gameSettings.keyBindBack, false);
        movementKeys.put(mc.gameSettings.keyBindLeft, false);
        movementKeys.put(mc.gameSettings.keyBindRight, false);
        movementKeys.put(mc.gameSettings.keyBindJump, false);
        movementKeys.put(mc.gameSettings.keyBindSneak, false);
        movementKeys.put(mc.gameSettings.keyBindSprint, false);
        grimMovementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindJump
        };
        return true;
    }
}
