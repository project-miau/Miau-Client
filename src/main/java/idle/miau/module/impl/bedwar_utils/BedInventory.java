package idle.miau.module.impl.bedwar_utils;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ItemChoiceSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.List;

public class BedInventory extends Module {
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 36;
    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END = 45;
    private static final String[] ITEM_OPTIONS = {
            "None", "Sword", "Pickaxe", "Axe", "Shears",
            "Bow", "Blocks", "Arrow", "Gapple", "Potion", "Fireball", "KnockBack Stick"
    };

    private final SliderSetting delaySetting;
    private final SliderSetting slot1;
    private final SliderSetting slot2;
    private final SliderSetting slot3;
    private final SliderSetting slot4;
    private final SliderSetting slot5;
    private final SliderSetting slot6;
    private final SliderSetting slot7;
    private final SliderSetting slot8;
    private final SliderSetting slot9;
    private final SliderSetting[] hotbarSettings;

    private long lastActionTime;
    private boolean processed;

    public BedInventory() {
        super("BedInventory", category.bedwar_utils);
        setDescription("Organizes BedWars items into your chosen hotbar slots.");
        this.registerSetting(delaySetting = new SliderSetting("Delay (ms)", 100, 0, 1000, 10));
        this.registerSetting(slot1 = new ItemChoiceSetting("Slot 1", 1, ITEM_OPTIONS));
        this.registerSetting(slot2 = new ItemChoiceSetting("Slot 2", 2, ITEM_OPTIONS));
        this.registerSetting(slot3 = new ItemChoiceSetting("Slot 3", 3, ITEM_OPTIONS));
        this.registerSetting(slot4 = new ItemChoiceSetting("Slot 4", 4, ITEM_OPTIONS));
        this.registerSetting(slot5 = new ItemChoiceSetting("Slot 5", 5, ITEM_OPTIONS));
        this.registerSetting(slot6 = new ItemChoiceSetting("Slot 6", 6, ITEM_OPTIONS));
        this.registerSetting(slot7 = new ItemChoiceSetting("Slot 7", 7, ITEM_OPTIONS));
        this.registerSetting(slot8 = new ItemChoiceSetting("Slot 8", 8, ITEM_OPTIONS));
        this.registerSetting(slot9 = new ItemChoiceSetting("Slot 9", 9, ITEM_OPTIONS));
        this.hotbarSettings = new SliderSetting[]{
                slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9
        };
    }

    @Override
    public void onDisable() {
        processed = false;
    }

    @Override
    public void onUpdate() {
        if (!(mc.currentScreen instanceof GuiInventory)) {
            processed = false;
            return;
        }

        if (processed) {
            return;
        }

        long now = System.currentTimeMillis();
        long delay = (long) delaySetting.getInput();
        if (delay > 0 && now - lastActionTime < delay) {
            return;
        }

        Container container = mc.thePlayer.openContainer;
        if (container == null) {
            return;
        }

        List<Slot> slots = container.inventorySlots;
        if (slots == null || slots.size() < HOTBAR_END) {
            return;
        }

        if (delay <= 0) {
            int maxCycles = 50;
            while (maxCycles-- > 0) {
                if (moveResourceToMainInventory(container, slots, now)) {
                    continue;
                }
                if (sortNextHotbarSlot(container, slots, now)) {
                    continue;
                }
                break;
            }
            processed = true;
            return;
        }

        if (moveResourceToMainInventory(container, slots, now)) {
            return;
        }

        if (sortNextHotbarSlot(container, slots, now)) {
            return;
        }

        processed = true;
    }

    private boolean moveResourceToMainInventory(Container container, List<Slot> slots, long now) {
        for (int slotIndex = HOTBAR_START; slotIndex < HOTBAR_END; slotIndex++) {
            ItemStack stack = getStack(slots, slotIndex);
            if (!isResource(stack) || !hasMainInventorySpace(slots, stack)) {
                continue;
            }

            mc.playerController.windowClick(container.windowId, slotIndex, 0, 1, mc.thePlayer);
            lastActionTime = now;
            return true;
        }
        return false;
    }

    private boolean sortNextHotbarSlot(Container container, List<Slot> slots, long now) {
        boolean[] reservedSlots = new boolean[HOTBAR_END];

        for (int hotbarIndex = 0; hotbarIndex < hotbarSettings.length; hotbarIndex++) {
            ItemCategory category = getSelectedCategory(hotbarSettings[hotbarIndex]);
            if (category == ItemCategory.NONE) {
                continue;
            }

            int targetSlot = HOTBAR_START + hotbarIndex;
            int sourceSlot = findBestSlot(slots, category, targetSlot, reservedSlots);
            if (sourceSlot == -1) {
                continue;
            }

            if (sourceSlot == targetSlot) {
                reservedSlots[targetSlot] = true;
                continue;
            }

            mc.playerController.windowClick(container.windowId, sourceSlot, hotbarIndex, 2, mc.thePlayer);
            lastActionTime = now;
            return true;
        }
        return false;
    }

    private int findBestSlot(List<Slot> slots, ItemCategory category, int targetSlot,
                             boolean[] reservedSlots) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        ItemStack targetStack = getStack(slots, targetSlot);

        if (!reservedSlots[targetSlot] && matches(category, targetStack)) {
            bestSlot = targetSlot;
            bestScore = getScore(category, targetStack);
        }

        for (int slotIndex = MAIN_INVENTORY_START; slotIndex < HOTBAR_END; slotIndex++) {
            if (slotIndex == targetSlot || reservedSlots[slotIndex]) {
                continue;
            }

            ItemStack stack = getStack(slots, slotIndex);
            if (!matches(category, stack)) {
                continue;
            }

            double score = getScore(category, stack);
            if (score > bestScore) {
                bestSlot = slotIndex;
                bestScore = score;
            }
        }

        return bestSlot;
    }

    private boolean hasMainInventorySpace(List<Slot> slots, ItemStack source) {
        for (int slotIndex = MAIN_INVENTORY_START; slotIndex < MAIN_INVENTORY_END; slotIndex++) {
            Slot slot = slots.get(slotIndex);
            if (slot == null) {
                continue;
            }
            if (!slot.getHasStack()) {
                return true;
            }

            ItemStack target = slot.getStack();
            if (target != null
                    && target.isItemEqual(source)
                    && ItemStack.areItemStackTagsEqual(target, source)
                    && target.stackSize < Math.min(target.getMaxStackSize(), slot.getItemStackLimit(target))) {
                return true;
            }
        }
        return false;
    }

    private ItemStack getStack(List<Slot> slots, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        return slot != null && slot.getHasStack() ? slot.getStack() : null;
    }

    private ItemCategory getSelectedCategory(SliderSetting setting) {
        int index = (int) setting.getInput();
        ItemCategory[] categories = ItemCategory.values();
        return index >= 0 && index < categories.length ? categories[index] : ItemCategory.NONE;
    }

    private boolean matches(ItemCategory category, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return false;
        }

        Item item = stack.getItem();
        switch (category) {
            case SWORD:
                return item instanceof ItemSword;
            case PICKAXE:
                return item instanceof ItemPickaxe;
            case AXE:
                return item instanceof ItemAxe;
            case SHEARS:
                return item instanceof ItemShears;
            case BOW:
                return item instanceof ItemBow;
            case BLOCKS:
                return item instanceof ItemBlock;
            case ARROW:
                return item == Items.arrow;
            case GAPPLE:
                return item instanceof ItemAppleGold;
            case POTION:
                return item instanceof ItemPotion;
            case FIREBALL:
                return isFireballItem(item);
            case KNOCKBACK_STICK:
                return item == Items.stick;
            default:
                return false;
        }
    }

    static boolean isFireballItem(Item item) {
        return item == Items.fire_charge;
    }

    private double getScore(ItemCategory category, ItemStack stack) {
        switch (category) {
            case SWORD:
                return ItemUtils.getAttackBonus(stack);
            case PICKAXE:
            case AXE:
                return ItemUtils.getToolEfficiency(stack);
            case SHEARS:
                return 1.0D + EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            case BOW:
                return ItemUtils.getBowAttackBonus(stack);
            case KNOCKBACK_STICK:
                return EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
            default:
                return stack.stackSize;
        }
    }

    private boolean isResource(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        Item item = stack.getItem();
        return item == Items.iron_ingot
                || item == Items.gold_ingot
                || item == Items.diamond
                || item == Items.emerald;
    }

    private enum ItemCategory {
        NONE,
        SWORD,
        PICKAXE,
        AXE,
        SHEARS,
        BOW,
        BLOCKS,
        ARROW,
        GAPPLE,
        POTION,
        FIREBALL,
        KNOCKBACK_STICK
    }
}
