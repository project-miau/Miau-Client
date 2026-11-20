package idle.miau.utility.inventory;

import idle.miau.module.Module;
import idle.miau.utility.ModuleActionService;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;

public interface ConsumableAccess {
    boolean ready();

    float health();

    float maxHealth();

    boolean usingItem();

    int current();

    int type(int slot);

    void remember();

    void select(int slot);

    void use();

    void drop();

    boolean claim();

    void release();

    void disable();

    final class Live implements ConsumableAccess {
        private final Module module;
        private final Minecraft mc = Minecraft.getMinecraft();
        private InventoryPlayer savedInventory;
        private int savedSlot;
        private int selectedSlot;

        public Live(Module module) {
            this.module = module;
        }

        public boolean ready() {
            return InventoryActions.inputReady();
        }

        public float health() {
            return mc.thePlayer.getHealth();
        }

        public float maxHealth() {
            return mc.thePlayer.getMaxHealth();
        }

        public boolean usingItem() {
            return mc.thePlayer.isUsingItem();
        }

        public int current() {
            return mc.thePlayer.inventory.currentItem;
        }

        public int type(int slot) {
            return InventoryActions.type(mc.thePlayer.inventory.getStackInSlot(slot));
        }

        public void remember() {
            InventoryActions.prepareItemUse();
            savedInventory = mc.thePlayer.inventory;
            savedSlot = savedInventory.currentItem;
            selectedSlot = savedSlot;
        }

        public void select(int slot) {
            if (mc.thePlayer.isUsingItem()) {
                mc.playerController.onStoppedUsingItem(mc.thePlayer);
            }
            selectedSlot = slot;
            mc.thePlayer.inventory.currentItem = slot;
            if (mc.thePlayer.inventory == savedInventory && slot == savedSlot) {
                savedInventory = null;
            }
        }

        public void use() {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
        }

        public void drop() {
            InventoryActions.press(mc.gameSettings.keyBindDrop);
        }

        public boolean claim() {
            return ModuleActionService.claim(module);
        }

        public void release() {
            savedInventory = null;
            ModuleActionService.release(module);
        }

        public void disable() {
            module.disable();
        }

        public void cleanup() {
            if (savedInventory != null) {
                if (savedInventory.currentItem == selectedSlot) {
                    savedInventory.currentItem = savedSlot;
                }
                savedInventory = null;
            }
            release();
        }
    }
}
