package idle.miau.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

public final class WeaponPolicy {
   private final WeaponPolicy.Cache cache = new WeaponPolicy.Cache(0, false, 0);

   public static final WeaponPolicy SHARED = new WeaponPolicy();
   private final java.util.Map<String, Boolean> flags = new java.util.HashMap<>();
   private List<String> extraItems = Collections.emptyList();

   public WeaponPolicy() {
      flags.put("swords", true);
   }

   private boolean flag(String key) {
      return flags.getOrDefault(key, false);
   }

   private List<String> names(String key) {
      return extraItems;
   }

   public void setAllowed(String key, boolean allowed) {
      flags.put(key, allowed);
   }

   public void setExtraItems(List<String> items) {
      extraItems = ItemPolicyService.canonicalize(items);
   }

   public boolean sticksEnabled() {
      return ItemPolicyService.contains(extraItems, 280);
   }

   public void setSticksEnabled(boolean enabled) {
      List<String> items = new ArrayList<>(extraItems);
      items.removeIf(name -> ItemPolicyService.identifier(name) == 280);
      if (enabled) {
         items.add(ItemPolicyService.name(280));
      }
      setExtraItems(items);
   }

   public boolean isAllowed(ItemStack stack, int selectedSlot) {
      if (stack == null) {
         return this.flag("fist");
      } else {
         int groups = enchantmentGroups(stack);
         return this.selectedSetting(selectedSlot) || this.groupAllowed(groups) || this.itemAllowed(stack.getItem());
      }
   }

   private boolean selectedSetting(int selected) {
      if (selected >= 0 && selected < 9) {
         return this.flag(Integer.toString(selected + 1));
      } else {
         switch (selected) {
            case -1:
               return this.flag("fist");
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            default:
               throw new IllegalArgumentException("Slot outside setting records: " + selected);
            case 9:
               return this.flag("enchant_sharpness");
            case 10:
               return this.flag("enchant_knockback");
            case 11:
               return this.flag("enchant_fire_aspect");
            case 12:
               return this.flag("axes");
            case 13:
               return this.flag("swords");
         }
      }
   }

   private boolean groupAllowed(int groups) {
      return (groups & 1) != 0 && this.flag("enchant_sharpness")
         || (groups & 2) != 0 && this.flag("enchant_knockback")
         || Integer.compareUnsigned(groups, 4) >= 0 && this.flag("enchant_fire_aspect");
   }

   private boolean itemAllowed(Item item) {
      return allowedIdentifier(
         item == null ? null : Item.getIdFromItem(item), this.flag("fist"), this.flag("swords"), this.flag("axes"), this.names("extra_items")
      );
   }

   public static boolean allowedIdentifier(Integer identifier, boolean fist, boolean swords, boolean axes, List<String> extraItems) {
      if (identifier == null) {
         return fist;
      } else {
         int id = identifier;
         int bit = id - 258;
         if (Integer.compareUnsigned(bit, 29) < 0) {
            if ((33834496 >>> bit & 1) != 0) {
               return swords;
            }

            if ((270671873 >>> bit & 1) != 0) {
               return axes;
            }
         }

         return id == 0 ? fist : ItemPolicyService.contains(extraItems, id);
      }
   }

   public int firstAllowedSlot(InventoryPlayer inventory) {
      for (int slot = 0; slot < 9; slot++) {
         if (this.flag(Integer.toString(slot + 1))) {
            return slot;
         }

         ItemStack stack = inventory.getStackInSlot(slot);
         if (stack != null && (this.groupAllowed(enchantmentGroups(stack)) || this.itemAllowed(stack.getItem()))) {
            return slot;
         }
      }

      return -1;
   }

   public boolean isWeapon(EntityPlayer player) {
      if (player == null) {
         return false;
      } else {
         this.updateCache(player);
         return this.cache.allowed;
      }
   }

   public int classificationFlags(EntityPlayer player) {
      if (player == null) {
         return 0;
      } else {
         this.updateCache(player);
         return this.cache.groups;
      }
   }

   private void updateCache(final EntityPlayer player) {
      this.cache
         .update(
            player.ticksExisted,
            new WeaponPolicy.Classification() {
               private ItemStack stack;

               @Override
               public int groups() {
                  this.stack = player.inventory.getStackInSlot(player.inventory.currentItem);
                  return this.stack == null ? 0 : WeaponPolicy.enchantmentGroups(this.stack);
               }

               @Override
               public boolean allowed(int groups) {
                  return this.stack == null
                     ? WeaponPolicy.this.flag("fist")
                     : WeaponPolicy.this.selectedSetting(player.inventory.currentItem)
                        || WeaponPolicy.this.groupAllowed(groups)
                        || WeaponPolicy.this.itemAllowed(this.stack.getItem());
               }
            }
         );
   }

   public static int enchantmentGroups(ItemStack stack) {
      NBTTagList tags = stack.getEnchantmentTagList();
      if (tags == null) {
         return 0;
      } else {
         int groups = 0;

         for (int index = 0; index < tags.tagCount(); index++) {
            groups |= enchantmentGroup(tags.getCompoundTagAt(index).getShort("id") & '\uffff');
         }

         return groups;
      }
   }

   public static int enchantmentGroup(int unsignedShort) {
      switch (unsignedShort & 65535) {
         case 16:
            return 1;
         case 17:
         case 18:
         default:
            return 0;
         case 19:
            return 2;
         case 20:
            return 4;
      }
   }

   public static final class Cache {
      public int key;
      public int groups;
      public boolean allowed;

      public Cache(int key, boolean allowed, int groups) {
         this.key = key;
         this.allowed = allowed;
         this.groups = groups;
      }

      public void update(int counter, WeaponPolicy.Classification classification) {
         if (counter != this.key) {
            this.key = counter;
            this.groups = 0;
            this.groups = classification.groups();
            this.allowed = classification.allowed(this.groups);
         }
      }
   }

   public interface Classification {
      int groups();

      boolean allowed(int var1);
   }
}
