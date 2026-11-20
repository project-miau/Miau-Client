package idle.miau.module.impl.player;

import idle.miau.event.PreUpdateEvent;
import idle.miau.event.WindowClickEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.TimerUtil;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {
  private static final String[] MODES = {"Normal", "Advanced"};
  private static final String[] STEAL_MODES = {"Linear", "Random", "Nearest"};
  private static final String[] MENU_NAMES = {
      "mode", "delivery", "menu", "selector", "game", "gui", "server", "inventory",
      "play", "teleporter", "shop", "melee", "armor", "block", "castle", "mini",
      "warp", "teleport", "user", "team", "tool", "sure", "trade", "cancel",
      "accept", "soul", "book", "recipe", "profile", "tele", "port", "map", "kit",
      "select", "lobby", "vault", "lock", "anticheat", "travel", "settings", "user",
      "preference", "compass", "cake", "wars", "buy", "upgrade", "ranged",
      "potions", "utility"
  };
  private static final Minecraft mc = Minecraft.getMinecraft();
  private int clickDelay = 0;
  private int oDelay = 0;
  private boolean inChest = false;
  private boolean warnedFull = false;
  private final SliderSetting mode = new SliderSetting("Mode", 0, MODES);
  private final ButtonSetting visual = new ButtonSetting("Visual", true);
  public final SliderSetting minDelay = new SliderSetting("Min delay", " ticks", 1, 0, 20, 1);
  public final SliderSetting maxDelay = new SliderSetting("Max delay", " ticks", 2, 0, 20, 1);
  public final SliderSetting openDelay = new SliderSetting("Open delay", " ticks", 1, 0, 20, 1);
  public final ButtonSetting autoClose = new ButtonSetting("Auto close", false);
  private final GroupSetting filter = new GroupSetting("Filter");
  public final ButtonSetting nameCheck = new ButtonSetting(filter, "Name check", true);
  public final ButtonSetting skipTrash = new ButtonSetting(filter, "Skip trash", true);
  public final ButtonSetting moreArmor = new ButtonSetting(filter, "More armor", false);
  public final ButtonSetting moreSword = new ButtonSetting(filter, "More sword", false);
  private final SliderSetting startDelay = new SliderSetting(
      "Start Delay", " ticks", 1.0D, 1.0D, 10.0D, 1.0D);
  private final SliderSetting closeDelay = new SliderSetting(
      "Close Delay", " ticks", 1.0D, 1.0D, 10.0D, 1.0D);
  private final RangeSetting advancedDelay = new RangeSetting(
      "Delay", 0.0D, 10.0D, 1.0D, 1.0D, 1.0D);
  private final SliderSetting stealMode = new SliderSetting("Steal Mode", 0, STEAL_MODES);
  private final SliderSetting missClickChance = new SliderSetting(
      "Miss-click chance", "%", 0.0D, 0.0D, 99.0D, 1.0D);
  private final ButtonSetting instant = new ButtonSetting("Instant", false, "Allow instant");
  private final ButtonSetting capBlocks = new ButtonSetting(filter, "Cap blocks", false);
  private final SliderSetting maxBlocks = new SliderSetting(
      filter, "Max blocks", 128.0D, 64.0D, 512.0D, 32.0D);
  private final ButtonSetting chestTitle = new ButtonSetting(filter, "Chest Tittle", true, "Menu Check");
  private final TimerUtil stealTimer = new TimerUtil();
  private final TimerUtil startDelayTimer = new TimerUtil();
  private final TimerUtil closeDelayTimer = new TimerUtil();
  private long currentStealWait;
  public int slot;
  private int visualWindowId = -1;
  private int visualSlot = -1;

  private boolean isValidGameMode() {
    GameType gameType = mc.playerController.getCurrentGameType();
    return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
  }

  private boolean isMoreArmor(ItemStack itemStack) {
    if (itemStack == null) return false;
    if (!this.moreArmor.isToggled()) return false;
    if (!(itemStack.getItem() instanceof ItemArmor)) return false;
    ItemArmor.ArmorMaterial armorMaterial = ((ItemArmor) itemStack.getItem()).getArmorMaterial();
    if (armorMaterial == ItemArmor.ArmorMaterial.DIAMOND) return true;
    return armorMaterial == ItemArmor.ArmorMaterial.IRON && itemStack.isItemEnchanted();
  }

  private boolean isMoreSword(ItemStack itemStack) {
    if (itemStack == null) return false;
    if (!this.moreSword.isToggled()) return false;
    if (!(itemStack.getItem() instanceof ItemSword)) return false;
    String swordMaterial = ((ItemSword) itemStack.getItem()).getToolMaterialName();
    if (Item.ToolMaterial.EMERALD.toString().equals(swordMaterial)) return true;
    if (EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) != 0)
      return true;
    return Item.ToolMaterial.IRON.toString().equals(swordMaterial) && itemStack.isItemEnchanted();
  }

  private boolean isInvManagerRequire(ItemStack itemStack) {
    if (itemStack == null) return false;
    InvManager invManager = ModuleManager.invManager;
    if (ItemUtils.ItemType.Block.contains(itemStack)) {
      return !invManager.isEnabled()
          || ItemUtils.findInventorySlot(ItemUtils.ItemType.Block) < (int) invManager.blocks.getInput();
    }
    if (ItemUtils.ItemType.Projectile.contains(itemStack)) {
      return !invManager.isEnabled()
          || ItemUtils.findInventorySlot(ItemUtils.ItemType.Projectile)
              < (int) invManager.projectiles.getInput();
    }
    if (ItemUtils.ItemType.FishRod.contains(itemStack)) {
      return ItemUtils.findInventorySlot(ItemUtils.ItemType.Projectile) == 0;
    }
    if (ItemUtils.ItemType.Arrow.contains(itemStack)) {
      return !invManager.isEnabled()
          || ItemUtils.findInventorySlot(ItemUtils.ItemType.Arrow) < (int) invManager.arrow.getInput();
    }
    return false;
  }

  private void clickSlot(int windowId, int slotId, int mouseButton, int clickMode) {
    if (visual.isToggled()) {
      visualWindowId = windowId;
      visualSlot = slotId;
    } else {
      clearVisualSlot();
    }
    mc.playerController.windowClick(
        windowId, slotId, mouseButton, clickMode, mc.thePlayer);
  }

  private void shiftClick(int windowId, int slotId) {
    clickSlot(windowId, slotId, 0, 1);
  }

  public ChestStealer() {
    super("ChestStealer", category.player);
    setDescription("Automatically steals items from chests.");
    registerSetting(mode);
    registerSetting(visual);
    registerSetting(minDelay);
    registerSetting(maxDelay);
    registerSetting(openDelay);
    registerSetting(autoClose);
    registerSetting(filter);
    registerSetting(nameCheck);
    registerSetting(skipTrash);
    registerSetting(moreArmor);
    registerSetting(moreSword);
    registerSetting(capBlocks);
    registerSetting(maxBlocks);
    registerSetting(chestTitle);
    registerSetting(startDelay);
    registerSetting(closeDelay);
    registerSetting(advancedDelay);
    registerSetting(stealMode);
    registerSetting(missClickChance);
    registerSetting(instant);
    guiUpdate();
  }

  @Override
  public String getInfo() {
    return MODES[(int) mode.getInput()];
  }

  @Override
  public void guiUpdate() {
    filter.setVisible(true, this);
    boolean advanced = (int) mode.getInput() == 1;
    minDelay.setVisible(!advanced, this);
    maxDelay.setVisible(!advanced, this);
    openDelay.setVisible(!advanced, this);
    autoClose.setVisible(!advanced, this);
    nameCheck.setVisible(!advanced, this);
    skipTrash.setVisible(!advanced, this);
    moreArmor.setVisible(!advanced, this);
    moreSword.setVisible(!advanced, this);
    startDelay.setVisible(advanced, this);
    closeDelay.setVisible(advanced, this);
    advancedDelay.setVisible(advanced, this);
    stealMode.setVisible(advanced, this);
    missClickChance.setVisible(advanced, this);
    instant.setVisible(advanced, this);
    capBlocks.setVisible(advanced, this);
    maxBlocks.setVisible(advanced && capBlocks.isToggled(), this);
    chestTitle.setVisible(advanced, this);
  }

  @Override
  public void onSlide(SliderSetting setting) {
    if (setting == minDelay && minDelay.getInput() > maxDelay.getInput()) {
      maxDelay.setValue(minDelay.getInput());
    } else if (setting == maxDelay && maxDelay.getInput() < minDelay.getInput()) {
      minDelay.setValue(maxDelay.getInput());
    }
    guiUpdate();
  }

  @Override
  public void guiButtonToggled(ButtonSetting setting) {
    guiUpdate();
    super.guiButtonToggled(setting);
  }

  @Override
  public void onCommandSettingChanged(Setting setting) {
    guiUpdate();
  }

  @Subscribe
  public void onUpdate(PreUpdateEvent event) {
      if ((int) mode.getInput() == 1) {
        updateAdvanced();
        return;
      }
      if (this.clickDelay > 0) {
        this.clickDelay--;
      }
      if (this.oDelay > 0) {
        this.oDelay--;
      }
      if (!(mc.currentScreen instanceof GuiChest)) {
        this.inChest = false;
        clearVisualSlot();
      } else {
        Container container = ((GuiChest) mc.currentScreen).inventorySlots;
        if (!(container instanceof ContainerChest)) {
          this.inChest = false;
          clearVisualSlot();
        } else {
          if (!this.inChest) {
            this.inChest = true;
            this.warnedFull = false;
            this.oDelay = (int) this.openDelay.getInput() + 1;
          }
          if (this.oDelay <= 0 && this.clickDelay <= 0) {
            if (this.isEnabled() && this.isValidGameMode()) {
              IInventory inventory = ((ContainerChest) container).getLowerChestInventory();
              if (this.nameCheck.isToggled()) {
                String inventoryName = inventory.getName();
                if (!inventoryName.equals(I18n.format("container.chest"))
                    && !inventoryName.equals(I18n.format("container.chestDouble"))) {
                  return;
                }
              }
              if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                if (!this.warnedFull) {
                  Utils.sendMessage("&7[&b" + this.getName() + "&7] &cYour inventory is full!&r");
                  this.warnedFull = true;
                }
                if (this.autoClose.isToggled()) {
                  mc.thePlayer.closeScreen();
                }
              } else {
                if (this.skipTrash.isToggled()) {
                  int bestSword = -1;
                  double bestDamage = 0.0;
                  int[] bestArmorSlots = new int[] {-1, -1, -1, -1};
                  double[] bestArmorProtection = new double[] {0.0, 0.0, 0.0, 0.0};
                  int bestPickaxeSlot = -1;
                  float bestPickaxeEfficiency = 1.0F;
                  int bestShovelSlot = -1;
                  float bestShovelEfficiency = 1.0F;
                  int bestAxeSlot = -1;
                  float bestAxeEfficiency = 1.0F;
                  int bestBow = -1;
                  double bestBowDamage = 0.0;
                  for (int i = 0; i < inventory.getSizeInventory(); i++) {
                    if (container.getSlot(i).getHasStack()) {
                      ItemStack stack = container.getSlot(i).getStack();
                      Item item = stack.getItem();
                      if (item instanceof ItemSword) {
                        double damage = ItemUtils.getAttackBonus(stack);
                        if (bestSword == -1 || damage > bestDamage) {
                          bestSword = i;
                          bestDamage = damage;
                        }
                      } else if (item instanceof ItemArmor) {
                        int armorType = ((ItemArmor) item).armorType;
                        double protectionLevel = ItemUtils.getArmorProtection(stack);
                        if (bestArmorSlots[armorType] == -1
                            || protectionLevel > bestArmorProtection[armorType]) {
                          bestArmorSlots[armorType] = i;
                          bestArmorProtection[armorType] = protectionLevel;
                        }
                      } else if (item instanceof ItemPickaxe) {
                        float efficiency = ItemUtils.getToolEfficiency(stack);
                        if (bestPickaxeSlot == -1 || efficiency > bestPickaxeEfficiency) {
                          bestPickaxeSlot = i;
                          bestPickaxeEfficiency = efficiency;
                        }
                      } else if (item instanceof ItemSpade) {
                        float efficiency = ItemUtils.getToolEfficiency(stack);
                        if (bestShovelSlot == -1 || efficiency > bestShovelEfficiency) {
                          bestShovelSlot = i;
                          bestShovelEfficiency = efficiency;
                        }
                      } else if (item instanceof ItemAxe) {
                        float efficiency = ItemUtils.getToolEfficiency(stack);
                        if (bestAxeSlot == -1 || efficiency > bestAxeEfficiency) {
                          bestAxeSlot = i;
                          bestAxeEfficiency = efficiency;
                        }
                      } else if (item instanceof ItemBow) {
                        double damage = ItemUtils.getBowAttackBonus(stack);
                        if (bestBow == -1 || damage > bestBowDamage) {
                          bestBow = i;
                          bestBowDamage = damage;
                        }
                      }
                    }
                  }
                  int swordInInventorySlot = ItemUtils.findSwordInInventorySlot(0, true);
                  double damage =
                      swordInInventorySlot != -1
                          ? ItemUtils.getAttackBonus(
                              mc.thePlayer.inventory.getStackInSlot(swordInInventorySlot))
                          : 0.0;
                  if (bestDamage > damage) {
                    this.shiftClick(container.windowId, bestSword);
                    return;
                  }
                  for (int i = 0; i < 4; i++) {
                    int slot = ItemUtils.findArmorInventorySlot(i, true);
                    double protectionLevel =
                        slot != -1
                            ? ItemUtils.getArmorProtection(
                                mc.thePlayer.inventory.getStackInSlot(slot))
                            : 0.0;
                    if (bestArmorProtection[i] > protectionLevel) {
                      this.shiftClick(container.windowId, bestArmorSlots[i]);
                      return;
                    }
                  }
                  int pickaxeSlot = ItemUtils.findInventorySlot("pickaxe", 0, true);
                  float pickaxeEfficiency =
                      pickaxeSlot != -1
                          ? ItemUtils.getToolEfficiency(
                              mc.thePlayer.inventory.getStackInSlot(pickaxeSlot))
                          : 1.0F;
                  if (bestPickaxeEfficiency > pickaxeEfficiency) {
                    this.shiftClick(container.windowId, bestPickaxeSlot);
                    return;
                  }
                  int shovelSlot = ItemUtils.findInventorySlot("shovel", 0, true);
                  float shovelEfficiency =
                      shovelSlot != -1
                          ? ItemUtils.getToolEfficiency(
                              mc.thePlayer.inventory.getStackInSlot(shovelSlot))
                          : 1.0F;
                  if (bestShovelEfficiency > shovelEfficiency) {
                    this.shiftClick(container.windowId, bestShovelSlot);
                    return;
                  }
                  int axeSlot = ItemUtils.findInventorySlot("axe", 0, true);
                  float efficiency =
                      axeSlot != -1
                          ? ItemUtils.getToolEfficiency(
                              mc.thePlayer.inventory.getStackInSlot(axeSlot))
                          : 1.0F;
                  if (bestAxeEfficiency > efficiency) {
                    this.shiftClick(container.windowId, bestAxeSlot);
                    return;
                  }
                  int bowSlot = ItemUtils.findBowInventorySlot(0, true);
                  double bowDamage =
                      bowSlot != -1
                          ? ItemUtils.getBowAttackBonus(
                              mc.thePlayer.inventory.getStackInSlot(bowSlot))
                          : 0.0;
                  if (bestBowDamage > bowDamage) {
                    this.shiftClick(container.windowId, bestBow);
                    return;
                  }
                }
                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                  if (container.getSlot(i).getHasStack()) {
                    ItemStack stack = container.getSlot(i).getStack();
                    if (!this.skipTrash.isToggled()
                        || !ItemUtils.isNotSpecialItem(stack)
                        || isMoreArmor(stack)
                        || isMoreSword(stack)
                        || isInvManagerRequire(stack)) {
                      this.shiftClick(container.windowId, i);
                      return;
                    }
                  }
                }
                if (this.autoClose.isToggled()) {
                  mc.thePlayer.closeScreen();
                }
              }
            }
          }
        }
      }
  }

  private void updateAdvanced() {
    if (!Utils.nullCheck()) {
      return;
    }
    if (mc.thePlayer.openContainer instanceof ContainerChest) {
      ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
      if (chestTitle.isToggled()) {
        String name = container.getLowerChestInventory().getDisplayName()
            .getUnformattedText().toLowerCase();
        for (String blockedName : MENU_NAMES) {
          if (name.contains(blockedName)) {
            return;
          }
        }
      }

      boolean chestEmpty = false;
      boolean openDelayElapsed = startDelayTimer.hasTimeElapsed(
          startDelay.getInput() * 50.0D);
      if (openDelayElapsed) {
        List<Integer> validSlots = new ArrayList<Integer>();
        int blocksInInventory = 0;
        if (capBlocks.isToggled()) {
          for (int inventorySlot = ItemUtils.EXCLUDE_ARMOR_BEGIN;
               inventorySlot < ItemUtils.END; inventorySlot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer
                .getSlot(inventorySlot).getStack();
            if (stack != null && ItemUtils.isValidStack(stack)
                && stack.getItem() instanceof ItemBlock) {
              blocksInInventory += stack.stackSize;
            }
          }
        }

        for (int chestSlot = 0;
             chestSlot < container.getLowerChestInventory().getSizeInventory(); chestSlot++) {
          ItemStack stack = container.getLowerChestInventory().getStackInSlot(chestSlot);
          if (stack == null || !ItemUtils.isValidStack(stack)) {
            continue;
          }
          if (capBlocks.isToggled() && stack.getItem() instanceof ItemBlock
              && blocksInInventory >= maxBlocks.getInput()) {
            continue;
          }
          validSlots.add(chestSlot);
        }

        boolean stealDelayElapsed = stealTimer.hasTimeElapsed(currentStealWait * 50L);
        if (!validSlots.isEmpty()
            && canStealAfterOpenDelay(
                openDelayElapsed, instant.isToggled(), stealDelayElapsed)) {
          if (instant.isToggled()) {
            stealInstant(container, validSlots);
          } else {
            stealAdvanced(container, selectAdvancedSlot(validSlots));
          }
        }

        if (validSlots.isEmpty()) {
          chestEmpty = true;
        }
      }

      if (ItemUtils.isInventoryFull() || chestEmpty) {
        if (closeDelayTimer.hasTimeElapsed(closeDelay.getInput() * 50.0D)) {
          mc.thePlayer.closeScreen();
        }
      } else {
        closeDelayTimer.reset();
      }
    } else {
      currentStealWait = (int) advancedDelay.getRandomInRange();
      startDelayTimer.reset();
    }
  }

  static boolean canStealAfterOpenDelay(
      boolean openDelayElapsed, boolean instantEnabled, boolean stealDelayElapsed) {
    return openDelayElapsed && (instantEnabled || stealDelayElapsed);
  }

  private int selectAdvancedSlot(List<Integer> validSlots) {
    if ((int) stealMode.getInput() == 1) {
      return validSlots.get(ThreadLocalRandom.current().nextInt(validSlots.size()));
    }
    if ((int) stealMode.getInput() == 2) {
      validSlots.sort(Comparator.comparingInt(candidate -> {
        int rowA = candidate / 9;
        int columnA = candidate % 9;
        int rowB = slot / 9;
        int columnB = slot % 9;
        return (columnA - columnB) * (columnA - columnB)
            + (rowA - rowB) * (rowA - rowB);
      }));
    }
    return validSlots.get(0);
  }

  private void stealInstant(ContainerChest container, List<Integer> validSlots) {
    for (int chestSlot : validSlots) {
      ItemStack stack = container.getLowerChestInventory().getStackInSlot(chestSlot);
      if (stack == null || !ItemUtils.isValidStack(stack)) {
        continue;
      }
      slot = chestSlot;
      clickSlot(container.windowId, chestSlot, 0, 1);
    }
    currentStealWait = 0L;
    stealTimer.reset();
  }

  private void stealAdvanced(ContainerChest container, int selectedSlot) {
    if (ThreadLocalRandom.current().nextInt(100) <= missClickChance.getInput()) {
      int row = selectedSlot / 9;
      int column = selectedSlot % 9;
      int offsetRow = Math.max(0, Math.min(
          container.getLowerChestInventory().getSizeInventory() / 9 - 1,
          row + ThreadLocalRandom.current().nextInt(3) - 1));
      int offsetColumn = Math.max(0, Math.min(
          8, column + ThreadLocalRandom.current().nextInt(3) - 1));
      int nearbySlot = offsetRow * 9 + offsetColumn;
      if (nearbySlot != selectedSlot
          && nearbySlot < container.getLowerChestInventory().getSizeInventory()) {
        selectedSlot = nearbySlot;
      }
    }
    slot = selectedSlot;
    clickSlot(container.windowId, selectedSlot, 0, 1);
    currentStealWait = (int) advancedDelay.getRandomInRange();
    stealTimer.reset();
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
  public void onWindowClick(WindowClickEvent event) {
    this.clickDelay =
        RandomUtils.nextInt((int) this.minDelay.getInput() + 1, (int) this.maxDelay.getInput() + 2);
  }
}
