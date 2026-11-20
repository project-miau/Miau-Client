package idle.miau.clickgui.panel;

import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.PotionListSetting;
import idle.miau.utility.BlockSearchIndex;
import idle.miau.utility.ItemSearchIndex;
import idle.miau.utility.PotionSearchIndex;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PanelItemCatalog {
    private static List<Entry> items;
    private static List<Entry> blocks;

    private PanelItemCatalog() {
    }

    static List<Entry> entries(BlockListSetting setting) {
        if (setting instanceof PotionListSetting) {
            return PotionSearchIndex.search("", new PotionListSetting("Potions")).stream()
                    .map(entry -> new Entry(entry.displayName, entry.key)).toList();
        }
        if (setting instanceof ItemListSetting) {
            if (items == null) {
                Map<String, Entry> entries = new LinkedHashMap<>();
                for (ItemSearchIndex.ItemEntry entry : ItemSearchIndex.getEntries()) {
                    entries.put(entry.storageId, new Entry(entry.displayName, entry.storageId));
                    String registry = ItemSearchIndex.getRegistryId(entry.storageId);
                    if (registry != null && ItemSearchIndex.getVariants(registry).size() > 1) {
                        String wildcard = ItemSearchIndex.getAllSelectionStorageId(registry);
                        entries.putIfAbsent(wildcard, new Entry(ItemSearchIndex.getDisplayName(wildcard), wildcard));
                    }
                }
                items = sorted(entries);
            }
            return items;
        }
        if (blocks == null) {
            Map<String, Entry> entries = new LinkedHashMap<>();
            for (BlockSearchIndex.BlockEntry entry : BlockSearchIndex.getEntries()) {
                entries.put(entry.storageId, new Entry(entry.displayName, entry.storageId));
                String registry = BlockSearchIndex.getRegistryId(entry.storageId);
                if (registry != null && BlockSearchIndex.getVariants(registry).size() > 1) {
                    String wildcard = registry + ":*";
                    entries.putIfAbsent(wildcard, new Entry(BlockSearchIndex.getDisplayName(wildcard), wildcard));
                }
            }
            blocks = sorted(entries);
        }
        return blocks;
    }

    private static List<Entry> sorted(Map<String, Entry> entries) {
        return entries.values().stream().sorted(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::value)).toList();
    }

    record Entry(String label, String value) {
    }
}
