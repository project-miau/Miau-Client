package idle.miau.utility;

import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.Potion;

public final class ItemUtils {
  public static final int INCLUDE_ARMOR_BEGIN = 5;
  public static final int EXCLUDE_ARMOR_BEGIN = 9;
  public static final int ONLY_HOT_BAR_BEGIN = 36;
  public static final int END = 45;
  private static final Minecraft mc = Minecraft.getMinecraft();
  private static final ArrayList<Integer> specialItems = new SpecialItems();
  private static final List<Block> DEMISE_BLOCK_BLACKLIST = Arrays.asList(
      Blocks.air, Blocks.water, Blocks.flowing_water, Blocks.lava, Blocks.wooden_slab,
      Blocks.chest, Blocks.flowing_lava, Blocks.enchanting_table, Blocks.carpet,
      Blocks.glass_pane, Blocks.skull, Blocks.stained_glass_pane, Blocks.iron_bars,
      Blocks.snow_layer, Blocks.ice, Blocks.packed_ice, Blocks.trapped_chest, Blocks.torch,
      Blocks.anvil, Blocks.noteblock, Blocks.jukebox, Blocks.tnt,
      Blocks.wooden_pressure_plate, Blocks.stone_pressure_plate,
      Blocks.light_weighted_pressure_plate, Blocks.heavy_weighted_pressure_plate,
      Blocks.stone_button, Blocks.wooden_button, Blocks.lever, Blocks.tallgrass,
      Blocks.tripwire, Blocks.tripwire_hook, Blocks.rail, Blocks.waterlily,
      Blocks.red_flower, Blocks.red_mushroom, Blocks.brown_mushroom, Blocks.vine,
      Blocks.trapdoor, Blocks.yellow_flower, Blocks.ladder, Blocks.furnace, Blocks.sand,
      Blocks.cactus, Blocks.dispenser, Blocks.dropper, Blocks.crafting_table,
      Blocks.pumpkin, Blocks.sapling, Blocks.cobblestone_wall, Blocks.oak_fence,
      Blocks.activator_rail, Blocks.detector_rail, Blocks.golden_rail,
      Blocks.redstone_torch, Blocks.acacia_stairs, Blocks.birch_stairs,
      Blocks.brick_stairs, Blocks.dark_oak_stairs, Blocks.jungle_stairs,
      Blocks.nether_brick_stairs, Blocks.oak_stairs, Blocks.quartz_stairs,
      Blocks.red_sandstone_stairs, Blocks.sandstone_stairs, Blocks.spruce_stairs,
      Blocks.stone_brick_stairs, Blocks.stone_stairs, Blocks.double_wooden_slab,
      Blocks.stone_slab, Blocks.double_stone_slab, Blocks.stone_slab2,
      Blocks.double_stone_slab2, Blocks.web, Blocks.gravel,
      Blocks.daylight_detector_inverted, Blocks.daylight_detector, Blocks.soul_sand,
      Blocks.piston, Blocks.piston_extension, Blocks.piston_head, Blocks.sticky_piston,
      Blocks.iron_trapdoor, Blocks.ender_chest, Blocks.end_portal,
      Blocks.end_portal_frame, Blocks.standing_banner, Blocks.wall_banner,
      Blocks.deadbush, Blocks.slime_block, Blocks.acacia_fence_gate,
      Blocks.birch_fence_gate, Blocks.dark_oak_fence_gate, Blocks.jungle_fence_gate,
      Blocks.spruce_fence_gate, Blocks.oak_fence_gate);

  public static boolean isValidStack(ItemStack stack) {
    if (stack == null) {
      return false;
    }
    Item item = stack.getItem();
    return item instanceof ItemBlock && isGoodBlockStack(stack)
        || item instanceof ItemPotion && isBuffPotion(stack)
        || item instanceof ItemArmor && isBestArmor(stack)
        || item instanceof ItemSword && isBestSword(stack)
        || item instanceof ItemFood && isGoodFood(stack)
        || item instanceof ItemTool && isBestTool(stack)
        || item instanceof ItemBow && isBestBow(stack)
        || item instanceof ItemFishingRod
        || item instanceof ItemEnderPearl
        || item instanceof ItemSnowball
        || item == Items.water_bucket
        || item instanceof ItemBoat
        || item instanceof ItemEgg
        || item == Items.arrow
        || item == Items.gold_nugget;
  }

  public static boolean isBuffPotion(ItemStack stack) {
    List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
    if (effects == null || effects.isEmpty()) {
      return false;
    }
    List<Integer> badEffects = Arrays.asList(
        Potion.poison.id, Potion.weakness.id, Potion.wither.id, Potion.blindness.id,
        Potion.digSlowdown.id, Potion.harm.id);
    for (PotionEffect effect : effects) {
      if (badEffects.contains(effect.getPotionID())) {
        return false;
      }
    }
    return true;
  }

  public static boolean isGoodFood(ItemStack stack) {
    ItemFood food = (ItemFood) stack.getItem();
    return food instanceof ItemAppleGold
        || food.getHealAmount(stack) >= 4 && food.getSaturationModifier(stack) >= 0.3F;
  }

  public static boolean isGoodBlockStack(ItemStack stack) {
    return stack.stackSize >= 1
        && !DEMISE_BLOCK_BLACKLIST.contains(Block.getBlockFromItem(stack.getItem()));
  }

  public static boolean isBestBow(ItemStack stack) {
    return isBestItem(stack, candidate -> candidate.getItem() instanceof ItemBow,
        ItemUtils::getDemiseBowDamage, EXCLUDE_ARMOR_BEGIN);
  }

  public static boolean isBestSword(ItemStack stack) {
    return isBestItem(stack, candidate -> candidate.getItem() instanceof ItemSword,
        ItemUtils::getDemiseItemDamage, EXCLUDE_ARMOR_BEGIN);
  }

  public static boolean isBestTool(ItemStack stack) {
    int type = getToolType(stack);
    return isBestItem(stack,
        candidate -> candidate.getItem() instanceof ItemTool
            && getToolType(candidate) == type,
        candidate -> getToolEfficiency(candidate), EXCLUDE_ARMOR_BEGIN);
  }

  public static boolean isBestArmor(ItemStack stack) {
    if (!(stack.getItem() instanceof ItemArmor)) {
      return false;
    }
    int armorType = ((ItemArmor) stack.getItem()).armorType;
    return isBestItem(stack,
        candidate -> candidate.getItem() instanceof ItemArmor
            && ((ItemArmor) candidate.getItem()).armorType == armorType,
        ItemUtils::getDemiseDamageReduction, INCLUDE_ARMOR_BEGIN);
  }

  public static int getToolType(ItemStack stack) {
    Item item = stack.getItem();
    if (item instanceof ItemPickaxe) {
      return 0;
    }
    if (item instanceof ItemAxe) {
      return 1;
    }
    if (item instanceof ItemSpade) {
      return 2;
    }
    return -1;
  }

  public static boolean isInventoryFull() {
    for (int slot = EXCLUDE_ARMOR_BEGIN; slot < END; slot++) {
      if (!mc.thePlayer.inventoryContainer.getSlot(slot).getHasStack()) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBestItem(
      ItemStack target, Predicate<ItemStack> filter,
      ToDoubleFunction<ItemStack> scorer, int begin) {
    ItemStack best = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int slot = begin; slot < END; slot++) {
      ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
      if (stack != null && filter.test(stack)) {
        double score = scorer.applyAsDouble(stack);
        if (score > bestScore) {
          bestScore = score;
          best = stack;
        }
      }
    }
    return best == target || scorer.applyAsDouble(target) > bestScore;
  }

  private static double getDemiseBowDamage(ItemStack stack) {
    return stack.isItemEnchanted()
        ? EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack) : 0.0D;
  }

  private static double getDemiseItemDamage(ItemStack stack) {
    double baseDamage = 0.0D;
    Multimap<String, AttributeModifier> modifiers = stack.getAttributeModifiers();
    for (String attributeName : modifiers.keySet()) {
      if ("generic.attackDamage".equals(attributeName)) {
        Iterator<AttributeModifier> iterator = modifiers.get(attributeName).iterator();
        if (iterator.hasNext()) {
          baseDamage += iterator.next().getAmount();
        }
        break;
      }
    }
    baseDamage += EnchantmentHelper.getEnchantmentLevel(
        Enchantment.sharpness.effectId, stack) * 1.25D;
    double totalDamage = baseDamage * 20.0D;
    int fireAspect = EnchantmentHelper.getEnchantmentLevel(
        Enchantment.fireAspect.effectId, stack);
    if (fireAspect > 0) {
      totalDamage += fireAspect == 1 ? 80.0D : 160.0D;
    }
    return totalDamage;
  }

  private static double getDemiseDamageReduction(ItemStack stack) {
    ItemArmor armor = (ItemArmor) stack.getItem();
    double reduction = armor.damageReduceAmount;
    if (stack.isItemEnchanted()) {
      reduction += EnchantmentHelper.getEnchantmentLevel(
          Enchantment.protection.effectId, stack) * 0.25D;
      reduction += EnchantmentHelper.getEnchantmentLevel(
          Enchantment.blastProtection.effectId, stack) * 0.1D;
      reduction += EnchantmentHelper.getEnchantmentLevel(
          Enchantment.fireProtection.effectId, stack) * 0.15D;
    }
    return reduction;
  }

  public static boolean isNotSpecialItem(ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }
    Item item = itemStack.getItem();
    if (item instanceof ItemPotion) {
      return ((ItemPotion) item)
          .getEffects(itemStack).stream()
              .map(PotionEffect::getPotionID)
              .noneMatch(specialItems::contains);
    }
    if (item instanceof ItemEnderPearl) return false;
    if (item instanceof ItemFood) {
      if (item != Items.spider_eye) return false;
    }
    if (item instanceof ItemMonsterPlacer) return false;
    return item != Items.nether_star;
  }

  public static boolean isBlock(ItemStack itemStack) {
    if (itemStack == null || itemStack.stackSize < 1) {
      return false;
    }
    Item item = itemStack.getItem();
    if (item instanceof ItemBlock) {
      return ItemUtils.isContainerBlock((ItemBlock) item);
    }
    return false;
  }

  public static boolean isProjectile(ItemStack itemStack) {
    if (itemStack == null || itemStack.stackSize < 1) {
      return false;
    }
    Item item = itemStack.getItem();
    if (item instanceof ItemEgg) return true;
    if (item instanceof ItemSnowball) return true;
    return false;
  }

  public static boolean isContainerBlock(ItemBlock itemBlock) {
    Block block = itemBlock.getBlock();
    if (BlockUtils.isInteractable(block)) return false;
    return BlockUtils.isSolid(block);
  }

  public static double getAttackBonus(ItemStack itemStack) {
    double attackBonus = 0.0;
    if (itemStack == null) {
      return 0.0;
    }
    Multimap<String, AttributeModifier> multimap = itemStack.getAttributeModifiers();
    for (String attributeName : multimap.keySet()) {
      if (!attributeName.equals("generic.attackDamage")) continue;
      Iterator<AttributeModifier> iterator = multimap.get(attributeName).iterator();
      if (!iterator.hasNext()) break;
      attackBonus += (iterator.next()).getAmount();
      break;
    }
    if (itemStack.isItemEnchanted()) {
      attackBonus =
          attackBonus
              + (double)
                  EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack)
              + (double)
                      EnchantmentHelper.getEnchantmentLevel(
                          Enchantment.sharpness.effectId, itemStack)
                  * 1.25;
    }
    return attackBonus;
  }

  public static float getToolEfficiency(ItemStack itemStack) {
    float efficiency = 1.0f;
    if (itemStack != null) {
      if (itemStack.getItem() instanceof ItemTool) {
        int enchantLevel;
        efficiency =
            ((ItemTool) itemStack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
        if (efficiency > 1.0f
            && (enchantLevel =
                    EnchantmentHelper.getEnchantmentLevel(
                        Enchantment.efficiency.effectId, itemStack))
                > 0) {
          efficiency += (float) (enchantLevel * enchantLevel + 1);
        }
      }
    }
    return efficiency;
  }

  public static float getToolEfficiency(ItemStack itemStack, Block block) {
    float efficiency = 1.0f;
    if (itemStack != null) {
      efficiency =
          itemStack.canHarvestBlock(block) || !(itemStack.getItem() instanceof ItemPickaxe)
              ? itemStack.getStrVsBlock(block)
              : 1.0f;
      if (itemStack.getItem() instanceof ItemTool) {
        int enchantLevel;
        if (efficiency > 1.0f
            && (enchantLevel =
                    EnchantmentHelper.getEnchantmentLevel(
                        Enchantment.efficiency.effectId, itemStack))
                > 0) {
          efficiency += (float) (enchantLevel * enchantLevel + 1);
        }
      }
    }
    return efficiency;
  }

  public static double getArmorProtection(ItemStack itemStack) {
    double protection = 0.0;
    if (itemStack != null) {
      if (itemStack.getItem() instanceof ItemArmor) {
        protection = 0.0 + (double) ((ItemArmor) itemStack.getItem()).damageReduceAmount;
        if (itemStack.isItemEnchanted()) {
          protection +=
              (double)
                      EnchantmentHelper.getEnchantmentLevel(
                          Enchantment.protection.effectId, itemStack)
                  * 0.8;
          protection +=
              (double)
                      EnchantmentHelper.getEnchantmentLevel(
                          Enchantment.featherFalling.effectId, itemStack)
                  * 0.05;
          protection +=
              (double)
                      EnchantmentHelper.getEnchantmentLevel(
                          Enchantment.projectileProtection.effectId, itemStack)
                  * 0.01;
        }
      }
    }
    return protection;
  }

  public static double getBowAttackBonus(ItemStack itemStack) {
    double attackBonus = 0.0;
    if (itemStack != null) {
      if (itemStack.getItem() instanceof ItemBow) {
        attackBonus = 2;
        if (itemStack.isItemEnchanted()) {
          int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, itemStack);
          if (power > 0) {
            attackBonus += (double) (power + 1) * 0.25;
          }
          attackBonus +=
              (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, itemStack)
                  * 0.25;
          attackBonus +=
              (double)
                      EnchantmentHelper.getEnchantmentLevel(
                          Enchantment.infinity.effectId, itemStack)
                  * 0.05;
        }
      }
    }
    return attackBonus;
  }

  public static int findSwordInInventorySlot(int startSlot, boolean checkDurability) {
    int bestSlot = -1;
    double bestAttackBonus = 0.0;
    if (startSlot < 0) return bestSlot;
    for (int i = 0; i < 36; ++i) {
      int currentSlot = (startSlot + i) % 36;
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(currentSlot);
      if (itemStack == null) continue;
      if (!(itemStack.getItem() instanceof ItemSword)) continue;
      if (checkDurability) {
        if (itemStack.isItemDamaged()) {
          if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
            continue;
          }
        }
      }
      double attackBonus = ItemUtils.getAttackBonus(itemStack);
      if (!(attackBonus > bestAttackBonus)) continue;
      bestSlot = currentSlot;
      bestAttackBonus = attackBonus;
    }
    return bestSlot;
  }

  public static int findBowInventorySlot(int startSlot, boolean checkDurability) {
    int bestSlot = -1;
    double bestAttackBonus = 0.0;
    if (startSlot < 0) return bestSlot;
    for (int i = 0; i < 36; ++i) {
      int currentSlot = (startSlot + i) % 36;
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(currentSlot);
      if (itemStack == null) continue;
      if (!(itemStack.getItem() instanceof ItemBow)) continue;
      if (checkDurability) {
        if (itemStack.isItemDamaged()) {
          if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
            continue;
          }
        }
      }
      double attackBonus = ItemUtils.getBowAttackBonus(itemStack);
      if (!(attackBonus > bestAttackBonus)) continue;
      bestSlot = currentSlot;
      bestAttackBonus = attackBonus;
    }
    return bestSlot;
  }

  public static boolean isToolOfClass(ItemStack stack, String toolClass) {
    if (stack == null || toolClass == null) {
      return false;
    }
    Item item = stack.getItem();
    String normalized = toolClass.toLowerCase(java.util.Locale.ROOT);
    if ("pickaxe".equals(normalized)) {
      return item instanceof ItemPickaxe;
    }
    if ("axe".equals(normalized)) {
      return item instanceof ItemAxe;
    }
    if ("shovel".equals(normalized) || "spade".equals(normalized)) {
      return item instanceof ItemSpade;
    }
    if ("shears".equals(normalized)) {
      return item instanceof ItemShears;
    }
    if ("hoe".equals(normalized)) {
      return item instanceof ItemHoe;
    }
    return item instanceof ItemTool && item.getToolClasses(stack).contains(toolClass);
  }

  public static int findInventorySlot(String toolClass, int startSlot, boolean checkDurability) {
    int bestSlot = -1;
    float bestEfficiency = 1.0f;
    if (startSlot < 0) return bestSlot;
    for (int i = 0; i < 36; ++i) {
      int currentSlot = (startSlot + i) % 36;
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(currentSlot);
      if (itemStack == null) continue;
      if (!isToolOfClass(itemStack, toolClass)) continue;
      if (checkDurability) {
        if (itemStack.isItemDamaged()) {
          if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
            continue;
          }
        }
      }
      float efficiency = ItemUtils.getToolEfficiency(itemStack);
      if (!(efficiency > bestEfficiency)) continue;
      bestSlot = currentSlot;
      bestEfficiency = efficiency;
    }
    return bestSlot;
  }

  public static int findInventorySlot(int currentSlot, Block block) {
    ItemStack currentItem = ItemUtils.mc.thePlayer.inventory.getStackInSlot(currentSlot);
    int bestSlot = currentSlot;
    float bestStrength = getToolEfficiency(currentItem, block);
    for (int i = 0; i < 9; ++i) {
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(i);
      if (itemStack == null) continue;
      float strength = getToolEfficiency(itemStack, block);
      if (!(strength > bestStrength)) continue;
      bestSlot = i;
      bestStrength = strength;
    }
    return bestSlot;
  }

  public static int findAndurilHotbarSlot(int currentSlot) {
    for (int i = currentSlot; i < currentSlot + 9; ++i) {
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(i % 9);
      if (itemStack == null) continue;

      if (itemStack.getItem() instanceof ItemSword && itemStack.hasTagCompound()) {
        ItemSword itemSword = (ItemSword) itemStack.getItem();
        if (Item.ToolMaterial.IRON.toString().equals(itemSword.getToolMaterialName())
            && itemStack.getTagCompound().hasKey("display", 10)) {
          NBTTagList nbttaglist =
              itemStack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
          for (int j = 0; j < nbttaglist.tagCount(); ++j) {
            if (nbttaglist.getStringTagAt(j).contains("\u00a79Justice")) {
              return i % 9;
            }
          }
        }
      }
    }
    return -1;
  }

  public static int findArmorInventorySlot(int armorType, boolean checkDurability) {
    int bestSlot = -1;
    double bestProtection = 0.0;
    for (int i = 0; i < 40; ++i) {
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(i);
      if (itemStack == null) continue;
      if (!(itemStack.getItem() instanceof ItemArmor)) continue;
      if (((ItemArmor) itemStack.getItem()).armorType != armorType) {
        continue;
      }
      if (checkDurability) {
        if (itemStack.isItemDamaged()) {
          if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
            continue;
          }
        }
      }
      double protection = ItemUtils.getArmorProtection(itemStack);
      if (!(protection >= bestProtection)) continue;
      bestSlot = i;
      bestProtection = protection;
    }
    return bestSlot;
  }

  public static int findInventorySlot(int startSlot, ItemType itemType) {
    int bestSlot = -1;
    int maxStackSize = 0;
    if (startSlot < 0) startSlot = 0;
    for (int i = 0; i < 36; ++i) {
      int currentSlot = (startSlot + i) % 36;
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(currentSlot);
      if (itemStack == null) continue;
      if (!itemType.contains(itemStack)) continue;
      if (maxStackSize >= itemStack.stackSize) continue;
      bestSlot = currentSlot;
      maxStackSize = itemStack.stackSize;
    }
    return bestSlot;
  }

  public static int findInventorySlot(ItemType itemType) {
    int stackSize = 0;
    for (int i = 0; i < 36; ++i) {
      ItemStack itemStack = ItemUtils.mc.thePlayer.inventory.getStackInSlot(i);
      if (itemStack == null) continue;
      if (!itemType.contains(itemStack)) continue;
      stackSize += itemStack.stackSize;
    }
    return stackSize;
  }

  public static boolean hasRawUnbreakingEnchant() {
    ItemStack itemStack = mc.thePlayer.getHeldItem();
    if (itemStack == null) {
      return false;
    }
    if (itemStack.hasTagCompound()) {
      NBTTagCompound tag = itemStack.getTagCompound();
      if (tag.hasKey("ExtraAttributes")) {
        NBTTagCompound extra = tag.getCompoundTag("ExtraAttributes");
        if (extra.hasKey("UHCid")) {
          long id = extra.getLong("UHCid");
          if (id == 50006L || id == 50009L) {
            return true;
          }
        }
      }
      if (tag.hasKey("HideFlags")
          && itemStack.getItem() instanceof ItemSpade
          && ((ItemSpade) itemStack.getItem()).getToolMaterial() == Item.ToolMaterial.EMERALD) {
        return true;
      }
    }
    if (itemStack.getItem() instanceof ItemEnchantedBook) {
      return false;
    }
    if (EnchantmentHelper.getEnchantments(itemStack).containsKey(19)) {
      return true;
    }
    return itemStack.getItem() instanceof ItemSword;
  }

  public static boolean isHoldingSword() {
    ItemStack itemStack = mc.thePlayer.getHeldItem();
    return itemStack != null && itemStack.getItem() instanceof ItemSword;
  }

  public static boolean isHoldingTool() {
    ItemStack itemStack = mc.thePlayer.getHeldItem();
    return itemStack != null && itemStack.getItem() instanceof ItemTool;
  }

  public static boolean isEating() {
    ItemStack itemStack = ItemUtils.mc.thePlayer.getHeldItem();
    if (itemStack == null) {
      return false;
    }
    if (ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack))) {
      return false;
    }
    return itemStack.getItemUseAction() == EnumAction.EAT
        || itemStack.getItemUseAction() == EnumAction.DRINK;
  }

  public static boolean isUsingBow() {
    ItemStack itemStack = ItemUtils.mc.thePlayer.getHeldItem();
    if (itemStack == null) {
      return false;
    }
    return itemStack.getItem() instanceof ItemBow;
  }

  public static boolean isHoldingNonEmpty() {
    ItemStack itemStack = ItemUtils.mc.thePlayer.getHeldItem();
    if (itemStack == null || itemStack.stackSize < 1) {
      return false;
    }
    return itemStack.getItem() instanceof ItemBlock;
  }

  public static boolean isHoldingBlock() {
    return ItemUtils.isBlock(ItemUtils.mc.thePlayer.getHeldItem());
  }

  public static boolean hasHoldItem() {
    ItemStack itemStack = ItemUtils.mc.thePlayer.getHeldItem();
    if (itemStack == null || itemStack.stackSize < 1) {
      return false;
    }
    return itemStack.getItem() instanceof ItemFireball;
  }

  static final class SpecialItems extends ArrayList<Integer> {
    SpecialItems() {
      this.add(1);
      this.add(3);
      this.add(5);
      this.add(6);
      this.add(8);
      this.add(10);
      this.add(11);
      this.add(12);
      this.add(14);
      this.add(21);
      this.add(22);
    }
  }

  public enum ItemType {
    Block {
      public boolean contains(ItemStack itemStack) {
        return isBlock(itemStack);
      }
    },
    Projectile {
      public boolean contains(ItemStack itemStack) {
        return isProjectile(itemStack);
      }
    },
    FishRod {
      public boolean contains(ItemStack itemStack) {
        return itemStack.getItem() instanceof ItemFishingRod;
      }
    },
    GoldApple {
      public boolean contains(ItemStack itemStack) {
        return itemStack.getItem() instanceof ItemAppleGold;
      }
    },
    Arrow {
      public boolean contains(ItemStack itemStack) {
        return itemStack.getItem() == Items.arrow;
      }
    };

    public abstract boolean contains(ItemStack itemStack);
  }
}
