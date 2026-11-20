package idle.miau.module.setting.impl;

import idle.miau.utility.ItemSearchIndex;
import net.minecraft.item.ItemStack;

import java.util.List;

public class ItemListSetting extends BlockListSetting {
    public ItemListSetting(String name) {
        super(name);
    }

    public ItemListSetting(String name, String... legacyConfigKeys) {
        super(name, legacyConfigKeys);
    }

    public ItemListSetting(GroupSetting group, String name) {
        super(group, name);
    }

    public ItemListSetting(GroupSetting group, String name, String... legacyConfigKeys) {
        super(group, name, legacyConfigKeys);
    }

    public void addItem(String storageId) {
        addBlock(storageId);
    }

    public void removeItem(String storageId) {
        removeBlock(storageId);
    }

    public List<String> getItems() {
        return getBlocks();
    }

    public boolean containsItem(String storageId) {
        return contains(storageId);
    }

    public boolean matches(ItemStack stack) {
        return ItemSearchIndex.matches(getItems(), stack);
    }
}
