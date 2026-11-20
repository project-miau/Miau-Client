package idle.miau.module.impl.bedwar_utils;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class AutoEnder extends Module {
    public enum Mode {
        NONE, DEPOSIT, WITHDRAW
    }

    private final SliderSetting speed;
    private final KeySetting depositKey;
    private final KeySetting withdrawKey;
    private final ButtonSetting depositGold;
    private final ButtonSetting depositIron;
    private final ButtonSetting depositEmerald;
    private final ButtonSetting depositDiamond;
    private final ButtonSetting autoClose;

    private Mode currentMode = Mode.NONE;
    private long lastClickTime = 0L;

    public AutoEnder() {
        super("AutoEnder", category.bedwar_utils);
        setDescription("Deposits or withdraws resources using an ender chest.");
        this.registerSetting(speed = new SliderSetting("Speed (ms)", 100, 0, 1000, 10));
        this.registerSetting(depositKey = new KeySetting("Deposit Key", 0));
        this.registerSetting(withdrawKey = new KeySetting("Withdraw Key", 0));
        this.registerSetting(depositGold = new ButtonSetting("Gold", true));
        this.registerSetting(depositIron = new ButtonSetting("Iron", true));
        this.registerSetting(depositEmerald = new ButtonSetting("Emerald", true));
        this.registerSetting(depositDiamond = new ButtonSetting("Diamond", true));
        this.registerSetting(autoClose = new ButtonSetting("Auto Close", true));
        this.closetModule = true;
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    @Override
    public void onDisable() {
        currentMode = Mode.NONE;
    }

    @Override
    public void onUpdate() {
        if (depositKey.isPressed()) {
            currentMode = Mode.DEPOSIT;
        } else if (withdrawKey.isPressed()) {
            currentMode = Mode.WITHDRAW;
        }

        if (currentMode == Mode.NONE) {
            return;
        }

        if (!(mc.currentScreen instanceof GuiChest)) {
            currentMode = Mode.NONE;
            return;
        }

        if (!(mc.thePlayer.openContainer instanceof ContainerChest)) {
            currentMode = Mode.NONE;
            return;
        }

        ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        IInventory chestInventory = container.getLowerChestInventory();
        if (chestInventory == null) {
            currentMode = Mode.NONE;
            return;
        }

        if (!isEnderChest(chestInventory)) {
            currentMode = Mode.NONE;
            return;
        }

        long now = System.currentTimeMillis();
        long delay = (long) speed.getInput();

        boolean depositing = (currentMode == Mode.DEPOSIT);
        int chestSize = chestInventory.getSizeInventory();
        int startSlot = depositing ? chestSize : 0;
        int endSlot = depositing ? container.inventorySlots.size() : chestSize;

        if (delay <= 0) {
            for (int i = startSlot; i < endSlot; i++) {
                Slot slot = container.inventorySlots.get(i);
                if (slot == null || !slot.getHasStack() || !shouldTransfer(slot.getStack())) {
                    continue;
                }

                mc.playerController.windowClick(container.windowId, i, 0, 1, mc.thePlayer);
            }
            currentMode = Mode.NONE;
            if (autoClose.isToggled()) {
                mc.thePlayer.closeScreen();
            }
            return;
        }

        if (now - lastClickTime < delay) {
            return;
        }

        boolean foundAny = false;
        for (int i = startSlot; i < endSlot; i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot == null || !slot.getHasStack() || !shouldTransfer(slot.getStack())) {
                continue;
            }

            mc.playerController.windowClick(container.windowId, i, 0, 1, mc.thePlayer);
            lastClickTime = now;
            foundAny = true;
            return;
        }

        if (!foundAny) {
            currentMode = Mode.NONE;
            if (autoClose.isToggled()) {
                mc.thePlayer.closeScreen();
            }
        }
    }

    private boolean isEnderChest(IInventory inventory) {
        String name = inventory.getName();
        String displayName = inventory.getDisplayName().getUnformattedText();
        return name.contains("Ender Chest")
                || name.contains("container.enderchest")
                || displayName.contains("Ender Chest")
                || displayName.contains("enderChest");
    }

    private boolean shouldTransfer(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        return (stack.getItem() == Items.gold_ingot && depositGold.isToggled())
                || (stack.getItem() == Items.iron_ingot && depositIron.isToggled())
                || (stack.getItem() == Items.emerald && depositEmerald.isToggled())
                || (stack.getItem() == Items.diamond && depositDiamond.isToggled());
    }
}
