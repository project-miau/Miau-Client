package idle.miau.module.impl.player;

import idle.miau.event.PreUpdateEvent;
import idle.miau.event.WindowClickEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ItemChoiceSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.TimerUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

public class InvManager extends Module {
  private static final Minecraft mc = Minecraft.getMinecraft();
  private int actionDelay = 0;
  private int oDelay = 0;
  private boolean inventoryOpen = false;
  private final TimerUtil autoArmorTime = new TimerUtil();
  public final SliderSetting minDelay = new SliderSetting("Min delay", " ticks", 1, 0, 20, 1);
  public final SliderSetting maxDelay = new SliderSetting("Max delay", " ticks", 2, 0, 20, 1);
  public final SliderSetting openDelay = new SliderSetting("Open delay", " ticks", 1, 0, 20, 1);
  private final ButtonSetting visual = new ButtonSetting("Visual", true);
  public final ButtonSetting autoArmor = new ButtonSetting("Auto armor", true);
  public final SliderSetting autoArmorInterval = new SliderSetting("Auto armor interval", " ticks", 0, 0, 100, 1);
  public final ButtonSetting dropTrash = new ButtonSetting("Drop trash", false);
  public final ButtonSetting checkDurability = new ButtonSetting("Check durability", true);
  public final SliderSetting swordSlot = new ItemChoiceSetting("Sword slot", 1);
  public final SliderSetting pickaxeSlot = new ItemChoiceSetting("Pickaxe slot", 3);
  public final SliderSetting shovelSlot = new ItemChoiceSetting("Shovel slot", 4);
  public final SliderSetting axeSlot = new ItemChoiceSetting("Axe slot", 5);
  public final SliderSetting blocksSlot = new ItemChoiceSetting("Blocks slot", 2);
  public final SliderSetting blocks = new SliderSetting("Blocks", 128, 64, 2304, 1);
  public final SliderSetting projectileSlot = new ItemChoiceSetting("Projectile slot", 7);
  public final SliderSetting projectiles = new SliderSetting("Projectiles", 64, 16, 2304, 1);
  public final SliderSetting goldAppleSlot = new ItemChoiceSetting("Gold apple slot", 9);
  public final SliderSetting arrow = new SliderSetting("Arrows", 256, 0, 2304, 1);
  public final SliderSetting bowSlot = new ItemChoiceSetting("Bow slot", 8);
  private int visualWindowId = -1;
  private int visualSlot = -1;

  private boolean isValidGameMode() {
    GameType gameType = mc.playerController.getCurrentGameType();
    return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
  }

  private int convertSlotIndex(int slot) {
    if (slot >= 36) {
      return 8 - (slot - 36);
    } else {
      return slot <= 8 ? slot + 36 : slot;
    }
  }

  private void clickSlot(int windowId, int slotId, int mouseButtonClicked, int mode) {
    if (visual.isToggled()) {
      visualWindowId = windowId;
      visualSlot = slotId;
    } else {
      clearVisualSlot();
    }
    mc.playerController.windowClick(windowId, slotId, mouseButtonClicked, mode, mc.thePlayer);
  }

  private int getStackSize(int slot) {
    if (slot == -1) {
      return 0;
    } else {
      ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
      return stack != null ? stack.stackSize : 0;
    }
  }

  public InvManager() {
    super("InvManager", category.player);
    setDescription("Sorts your inventory, equips armor and drops trash.");
    registerSetting(minDelay);
    registerSetting(maxDelay);
    registerSetting(openDelay);
    registerSetting(visual);
    registerSetting(autoArmor);
    registerSetting(autoArmorInterval);
    registerSetting(dropTrash);
    registerSetting(checkDurability);
    registerSetting(swordSlot);
    registerSetting(pickaxeSlot);
    registerSetting(shovelSlot);
    registerSetting(axeSlot);
    registerSetting(blocksSlot);
    registerSetting(blocks);
    registerSetting(projectileSlot);
    registerSetting(projectiles);
    registerSetting(goldAppleSlot);
    registerSetting(arrow);
    registerSetting(bowSlot);
  }

  @Override
  public void onSlide(SliderSetting setting) {
    if (setting == minDelay && minDelay.getInput() > maxDelay.getInput()) {
      maxDelay.setValue(minDelay.getInput());
    } else if (setting == maxDelay && maxDelay.getInput() < minDelay.getInput()) {
      minDelay.setValue(maxDelay.getInput());
    }
  }

  @Override
  public void guiUpdate() {
    autoArmorInterval.setVisible(autoArmor.isToggled(), this);
  }

  @Subscribe
  public void onUpdate(PreUpdateEvent event) {
      if (this.actionDelay > 0) {
        this.actionDelay--;
      }
      if (this.oDelay > 0) {
        this.oDelay--;
      }
      if (!(mc.currentScreen instanceof GuiInventory) || !(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
        this.inventoryOpen = false;
        clearVisualSlot();
        if (this.actionDelay <= 0 && this.isEnabled() && this.isValidGameMode() && this.autoArmor.isToggled()
            && this.autoArmorTime.hasTimeElapsed((int) this.autoArmorInterval.getInput() * 50L)) {
          handleHotbarMovingAutoArmor();
        }
      } else {
        if (!this.inventoryOpen) {
          this.inventoryOpen = true;
          this.oDelay = (int) this.openDelay.getInput() + 1;
          this.autoArmorTime.reset();
        }
        if (this.oDelay <= 0 && this.actionDelay <= 0) {
          if (this.isEnabled() && this.isValidGameMode()) {
            ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
            ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
            for (int i = 0; i < 4; i++) {
              equippedArmorSlots.set(i, ItemUtils.findArmorInventorySlot(i, true));
              inventoryArmorSlots.set(i, ItemUtils.findArmorInventorySlot(i, false));
            }
            int preferredSwordHotbarSlot = (int) this.swordSlot.getInput() - 1;
            int inventorySwordSlot =
                ItemUtils.findSwordInInventorySlot(
                    preferredSwordHotbarSlot, this.checkDurability.isToggled());
            if (inventorySwordSlot == -1)
              inventorySwordSlot =
                  ItemUtils.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
            int preferredPickaxeHotbarSlot = (int) this.pickaxeSlot.getInput() - 1;
            int inventoryPickaxeSlot =
                ItemUtils.findInventorySlot(
                    "pickaxe", preferredPickaxeHotbarSlot, this.checkDurability.isToggled());
            if (inventoryPickaxeSlot == -1)
              inventoryPickaxeSlot =
                  ItemUtils.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
            int preferredShovelHotbarSlot = (int) this.shovelSlot.getInput() - 1;
            int inventoryShovelSlot =
                ItemUtils.findInventorySlot(
                    "shovel", preferredShovelHotbarSlot, this.checkDurability.isToggled());
            if (inventoryShovelSlot == -1)
              inventoryShovelSlot =
                  ItemUtils.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
            int preferredAxeHotbarSlot = (int) this.axeSlot.getInput() - 1;
            int inventoryAxeSlot =
                ItemUtils.findInventorySlot(
                    "axe", preferredAxeHotbarSlot, this.checkDurability.isToggled());
            if (inventoryAxeSlot == -1)
              inventoryAxeSlot = ItemUtils.findInventorySlot("axe", preferredAxeHotbarSlot, false);
            int preferredBlocksHotbarSlot = (int) this.blocksSlot.getInput() - 1;
            int inventoryBlocksSlot =
                ItemUtils.findInventorySlot(preferredBlocksHotbarSlot, ItemUtils.ItemType.Block);
            int preferredProjectileHotbarSlot = (int) this.projectileSlot.getInput() - 1;
            int inventoryProjectileSlot =
                ItemUtils.findInventorySlot(
                    preferredProjectileHotbarSlot, ItemUtils.ItemType.Projectile);
            if (inventoryProjectileSlot == -1)
              inventoryProjectileSlot =
                  ItemUtils.findInventorySlot(
                      preferredProjectileHotbarSlot, ItemUtils.ItemType.FishRod);
            int preferredGoldAppleHotbarSlot = (int) this.goldAppleSlot.getInput() - 1;
            int inventoryGoldAppleSlot =
                ItemUtils.findInventorySlot(
                    preferredGoldAppleHotbarSlot, ItemUtils.ItemType.GoldApple);
            int preferredBowHotbarSlot = (int) this.bowSlot.getInput() - 1;
            int inventoryBowSlot =
                ItemUtils.findBowInventorySlot(
                    preferredBowHotbarSlot, this.checkDurability.isToggled());
            if (inventoryBowSlot == -1)
              inventoryBowSlot = ItemUtils.findBowInventorySlot(preferredBowHotbarSlot, false);
            if (this.autoArmor.isToggled()
                && this.autoArmorTime.hasTimeElapsed((int) this.autoArmorInterval.getInput() * 50L)) {
              for (int i = 0; i < 4; i++) {
                int equippedSlot = equippedArmorSlots.get(i);
                int inventorySlot = inventoryArmorSlots.get(i);
                if (equippedSlot != -1 || inventorySlot != -1) {
                  int playerArmorSlot = 39 - i;
                  if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                    if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                      if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                        this.clickSlot(
                            mc.thePlayer.inventoryContainer.windowId,
                            this.convertSlotIndex(playerArmorSlot),
                            0,
                            1);
                      } else {
                        this.clickSlot(
                            mc.thePlayer.inventoryContainer.windowId,
                            this.convertSlotIndex(playerArmorSlot),
                            1,
                            4);
                      }
                    } else {
                      int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                      this.clickSlot(
                          mc.thePlayer.inventoryContainer.windowId,
                          this.convertSlotIndex(armorToEquipSlot),
                          0,
                          1);
                      this.autoArmorTime.reset();
                    }
                    return;
                  }
                }
              }
            }
            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
            if (preferredSwordHotbarSlot >= 0
                && preferredSwordHotbarSlot <= 8
                && inventorySwordSlot != -1) {
              usedHotbarSlots.add(preferredSwordHotbarSlot);
              if (inventorySwordSlot != preferredSwordHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventorySwordSlot),
                    preferredSwordHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredPickaxeHotbarSlot >= 0
                && preferredPickaxeHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot)
                && inventoryPickaxeSlot != -1) {
              usedHotbarSlots.add(preferredPickaxeHotbarSlot);
              if (inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryPickaxeSlot),
                    preferredPickaxeHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredShovelHotbarSlot >= 0
                && preferredShovelHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredShovelHotbarSlot)
                && inventoryShovelSlot != -1) {
              usedHotbarSlots.add(preferredShovelHotbarSlot);
              if (inventoryShovelSlot != preferredShovelHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryShovelSlot),
                    preferredShovelHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredAxeHotbarSlot >= 0
                && preferredAxeHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredAxeHotbarSlot)
                && inventoryAxeSlot != -1) {
              usedHotbarSlots.add(preferredAxeHotbarSlot);
              if (inventoryAxeSlot != preferredAxeHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryAxeSlot),
                    preferredAxeHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredBlocksHotbarSlot >= 0
                && preferredBlocksHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredBlocksHotbarSlot)
                && inventoryBlocksSlot != -1) {
              usedHotbarSlots.add(preferredBlocksHotbarSlot);
              if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryBlocksSlot),
                    preferredBlocksHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredProjectileHotbarSlot >= 0
                && preferredProjectileHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredProjectileHotbarSlot)
                && inventoryProjectileSlot != -1) {
              usedHotbarSlots.add(preferredProjectileHotbarSlot);
              if (inventoryProjectileSlot != preferredProjectileHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryProjectileSlot),
                    preferredProjectileHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredGoldAppleHotbarSlot >= 0
                && preferredGoldAppleHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredGoldAppleHotbarSlot)
                && inventoryGoldAppleSlot != -1) {
              usedHotbarSlots.add(preferredGoldAppleHotbarSlot);
              if (inventoryGoldAppleSlot != preferredGoldAppleHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryGoldAppleSlot),
                    preferredGoldAppleHotbarSlot,
                    2);
                return;
              }
            }
            if (preferredBowHotbarSlot >= 0
                && preferredBowHotbarSlot <= 8
                && !usedHotbarSlots.contains(preferredBowHotbarSlot)
                && inventoryBowSlot != -1) {
              usedHotbarSlots.add(preferredBowHotbarSlot);
              if (inventoryBowSlot != preferredBowHotbarSlot) {
                this.clickSlot(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.convertSlotIndex(inventoryBowSlot),
                    preferredBowHotbarSlot,
                    2);
                return;
              }
            }
            if (this.dropTrash.isToggled()) {
              int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
              int currentProjectileCount = this.getStackSize(inventoryProjectileSlot);
              int currentArrowCount = 0;
              for (int i = 0; i < 36; i++) {
                if (!equippedArmorSlots.contains(i)
                    && !inventoryArmorSlots.contains(i)
                    && !isConfiguredToolSlot(
                        i,
                        preferredSwordHotbarSlot,
                        preferredPickaxeHotbarSlot,
                        preferredShovelHotbarSlot,
                        preferredAxeHotbarSlot)
                    && inventorySwordSlot != i
                    && inventoryPickaxeSlot != i
                    && inventoryShovelSlot != i
                    && inventoryAxeSlot != i
                    && inventoryBlocksSlot != i
                    && inventoryProjectileSlot != i
                    && inventoryGoldAppleSlot != i
                    && inventoryBowSlot != i) {
                  ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                  if (stack != null) {
                    boolean isBlock = ItemUtils.isBlock(stack);
                    boolean isProjectile = ItemUtils.isProjectile(stack);
                    boolean isArrow = ItemUtils.ItemType.Arrow.contains(stack);
                    if (isBlock) {
                      currentBlockCount += stack.stackSize;
                    }
                    if (isProjectile) {
                      currentProjectileCount += stack.stackSize;
                    }
                    if (isArrow) {
                      currentArrowCount += stack.stackSize;
                    }
                    if (isBlock
                        ? currentBlockCount > (int) this.blocks.getInput()
                        : isProjectile
                            ? currentProjectileCount > (int) this.projectiles.getInput()
                            : isArrow
                                ? currentArrowCount > (int) this.arrow.getInput()
                                : ItemUtils.isNotSpecialItem(stack)) {
                      this.clickSlot(
                          mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                      return;
                    }
                  }
                }
              }
            }
          }
        }
      }
  }

  private void handleHotbarMovingAutoArmor() {
    if (mc.thePlayer == null || mc.playerController == null || mc.theWorld == null) {
      return;
    }
    for (int i = 0; i < 4; i++) {
      int playerArmorSlot = 39 - i;
      if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
        continue;
      }

      int bestHotbarSlot = -1;
      double bestProtection = 0.0;
      for (int slot = 0; slot < 9; slot++) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack == null) continue;
        if (!(stack.getItem() instanceof net.minecraft.item.ItemArmor)) continue;
        net.minecraft.item.ItemArmor armor = (net.minecraft.item.ItemArmor) stack.getItem();
        if (armor.armorType != i) continue;
        if (this.checkDurability.isToggled() && stack.isItemDamaged()) {
          if (stack.getMaxDamage() - stack.getItemDamage() < 30) {
            continue;
          }
        }
        double protection = ItemUtils.getArmorProtection(stack);
        if (protection >= bestProtection) {
          bestHotbarSlot = slot;
          bestProtection = protection;
        }
      }

      if (bestHotbarSlot != -1) {
        int previousSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = bestHotbarSlot;
        mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C09PacketHeldItemChange(bestHotbarSlot));

        ItemStack armorStack = mc.thePlayer.inventory.getStackInSlot(bestHotbarSlot);
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, armorStack);

        mc.thePlayer.inventory.currentItem = previousSlot;
        mc.getNetHandler().addToSendQueue(new net.minecraft.network.play.client.C09PacketHeldItemChange(previousSlot));

        this.autoArmorTime.reset();
        this.actionDelay = org.apache.commons.lang3.RandomUtils.nextInt(
            (int) this.minDelay.getInput() + 1, (int) this.maxDelay.getInput() + 2);
        return;
      }
    }
  }

  static boolean isConfiguredToolSlot(
      int inventorySlot,
      int swordSlot,
      int pickaxeSlot,
      int shovelSlot,
      int axeSlot) {
    return inventorySlot >= 0
        && inventorySlot < 9
        && (inventorySlot == swordSlot
            || inventorySlot == pickaxeSlot
            || inventorySlot == shovelSlot
            || inventorySlot == axeSlot);
  }

  public boolean shouldHighlightSlot(int windowId, int slotId) {
    return isEnabled() && visual.isToggled()
        && visualWindowId == windowId && visualSlot == slotId;
  }

  private void clearVisualSlot() {
    visualWindowId = -1;
    visualSlot = -1;
  }

  @Override
  public void onDisable() {
    clearVisualSlot();
  }

  @Subscribe
  public void onClick(WindowClickEvent event) {
    this.actionDelay =
        RandomUtils.nextInt((int) this.minDelay.getInput() + 1, (int) this.maxDelay.getInput() + 2);
  }
}
