package idle.miau.module.setting.impl;

import java.util.List;

public class PotionListSetting extends BlockListSetting {
    public PotionListSetting(String name) {
        super(name);
    }

    public PotionListSetting(String name, String... legacyConfigKeys) {
        super(name, legacyConfigKeys);
    }

    public PotionListSetting(GroupSetting group, String name) {
        super(group, name);
    }

    public PotionListSetting(GroupSetting group, String name, String... legacyConfigKeys) {
        super(group, name, legacyConfigKeys);
    }

    public void addPotion(String potionKey) {
        addBlock(potionKey);
    }

    public void removePotion(String potionKey) {
        removeBlock(potionKey);
    }

    public boolean containsPotion(String potionKey) {
        return contains(potionKey);
    }

    public List<String> getPotions() {
        return getBlocks();
    }
}
