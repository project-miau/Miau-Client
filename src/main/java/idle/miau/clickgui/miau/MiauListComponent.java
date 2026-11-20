package idle.miau.clickgui.miau;

import idle.miau.clickgui.ClickGuiInput;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.InventoryItemListSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.PlayerListSetting;
import idle.miau.module.setting.impl.PotionListSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.utility.BlockSearchIndex;
import idle.miau.utility.ItemSearchIndex;
import idle.miau.utility.PlayerRelationsManager;
import idle.miau.utility.PotionSearchIndex;
import idle.miau.utility.shader.RoundedUtils;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MiauListComponent extends MiauSettingComponent {
    private static final int MAX_SUGGESTIONS = 4;
    private static final int MAX_ENTRIES = 5;

    private final MiauTextField field;
    private boolean opened;
    private int entryOffset;

    MiauListComponent(Setting setting, Module owner) {
        super(setting, owner);
        field = new MiauTextField(placeholder(setting), maxLength(setting));
    }

    @Override
    int getHeight() {
        if (!opened) {
            return MiauStyle.ROW_HEIGHT;
        }
        int suggestions = visibleSuggestions().size();
        int entries = Math.min(MAX_ENTRIES, Math.max(0, entryCount() - entryOffset));
        return MiauStyle.ROW_HEIGHT * (2 + suggestions + entries + (entryCount() > 0 ? 1 : 0));
    }

    @Override
    void draw(int mouseX, int mouseY) {
        MiauStyle.hoverRow(x - 3, y, width, mouseX, mouseY);
        String count = " (" + entryCount() + ")";
        String label = MiauStyle.trim(setting.getName() + count, MiauStyle.settingFont(), (int) width - 20);
        MiauStyle.settingFont().drawString(label, x + 2, y + 3, MiauStyle.WHITE, false);
        MiauStyle.settingFont().drawString(opened ? "-" : "+", x + width - 10, y + 3, MiauStyle.accent(x + width * 0.5D, y + MiauStyle.ROW_HEIGHT * 0.5D), false);
        if (!opened) {
            return;
        }

        float rowY = y + MiauStyle.ROW_HEIGHT;
        field.draw(x, rowY + 1, width - 6);
        rowY += MiauStyle.ROW_HEIGHT;

        for (Suggestion suggestion : visibleSuggestions()) {
            boolean over = MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY);
            RoundedUtils.drawRound(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, 2.0F,
                    over ? MiauStyle.ACTIVE_HOVER : MiauStyle.ACTIVE);
            MiauStyle.settingFont().drawString(MiauStyle.trim(suggestion.display, MiauStyle.settingFont(), (int) width - 8), x + 2, rowY + 3, MiauStyle.accent(x + width * 0.5D, rowY + MiauStyle.ROW_HEIGHT * 0.5D), false);
            rowY += MiauStyle.ROW_HEIGHT;
        }

        List<Entry> entries = entries();
        clampEntryOffset(entries.size());
        int end = Math.min(entries.size(), entryOffset + MAX_ENTRIES);
        for (int i = entryOffset; i < end; i++) {
            Entry entry = entries.get(i);
            boolean over = MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY);
            if (over) {
                RoundedUtils.drawRound(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, 2.0F, MiauStyle.HOVER);
            }
            String slot = entry.slot == null ? "" : " [" + entry.slot + "]";
            String display = MiauStyle.trim(entry.display + slot, MiauStyle.settingFont(), (int) width - 18);
            MiauStyle.settingFont().drawString(display, x + 2, rowY + 3, MiauStyle.MUTED, false);
            MiauStyle.settingFont().drawString("x", x + width - 11, rowY + 3, MiauStyle.WHITE, false);
            rowY += MiauStyle.ROW_HEIGHT;
        }

        if (!entries.isEmpty()) {
            boolean over = MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY);
            RoundedUtils.drawRound(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, 2.0F,
                    over ? MiauStyle.ACTIVE_HOVER : MiauStyle.ACTIVE);
            MiauStyle.settingFont().drawString("Clear", x + 2, rowY + 3, MiauStyle.WHITE, false);
        }
    }

    @Override
    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (MiauStyle.hovered(x - 3, y, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY) && (button == 0 || button == 1)) {
            opened = !opened;
            if (!opened) {
                field.unfocus();
            }
            return true;
        }
        if (!opened) {
            return false;
        }
        float rowY = y + MiauStyle.ROW_HEIGHT;
        if (MiauStyle.hovered(x, rowY, width - 6, 14, mouseX, mouseY) && button == 0) {
            field.focus();
            return true;
        }
        rowY += MiauStyle.ROW_HEIGHT;

        for (Suggestion suggestion : visibleSuggestions()) {
            if (MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY) && button == 0) {
                add(suggestion.value);
                field.setText("");
                markChanged();
                return true;
            }
            rowY += MiauStyle.ROW_HEIGHT;
        }

        List<Entry> entries = entries();
        clampEntryOffset(entries.size());
        int end = Math.min(entries.size(), entryOffset + MAX_ENTRIES);
        for (int i = entryOffset; i < end; i++) {
            Entry entry = entries.get(i);
            if (MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY)) {
                if (button == 1 && setting instanceof InventoryItemListSetting) {
                    InventoryItemListSetting inventory = (InventoryItemListSetting) setting;
                    int current = inventory.getAssignedSlot(entry.value);
                    inventory.setAssignedSlot(entry.value, current % 9 + 1);
                }
                else if (button == 0) {
                    remove(entry.value);
                }
                markChanged();
                return true;
            }
            rowY += MiauStyle.ROW_HEIGHT;
        }

        if (!entries.isEmpty() && MiauStyle.hovered(x - 3, rowY, width, MiauStyle.ROW_HEIGHT, mouseX, mouseY) && button == 0) {
            clear();
            entryOffset = 0;
            markChanged();
            return true;
        }
        field.unfocus();
        return false;
    }

    @Override
    boolean keyTyped(char typedChar, int keyCode) {
        if (!field.isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            List<Suggestion> suggestions = visibleSuggestions();
            String value = suggestions.isEmpty() ? field.getText().trim() : suggestions.get(0).value;
            if (!value.isEmpty()) {
                add(value);
                field.setText("");
                markChanged();
            }
            return true;
        }
        field.keyTyped(typedChar, keyCode);
        return true;
    }

    @Override
    boolean mouseWheel(int mouseX, int mouseY, int wheel) {
        if (!opened || !hovered(mouseX, mouseY) || entryCount() <= MAX_ENTRIES) {
            return false;
        }
        int rows = Math.max(1, Math.round(Math.abs(ClickGuiInput.scrollSteps(wheel))));
        entryOffset += wheel > 0 ? -rows : rows;
        clampEntryOffset(entryCount());
        return true;
    }

    @Override
    void clearTextFocus() {
        field.unfocus();
    }

    private List<Suggestion> visibleSuggestions() {
        if (!opened || !field.isFocused() || field.getText().trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Suggestion> result = suggestions(field.getText());
        if (result.size() > MAX_SUGGESTIONS) {
            return result.subList(0, MAX_SUGGESTIONS);
        }
        return result;
    }

    private List<Suggestion> suggestions(String query) {
        List<Suggestion> result = new ArrayList<Suggestion>();
        if (setting instanceof PotionListSetting) {
            for (PotionSearchIndex.PotionEntry entry : PotionSearchIndex.search(query, (PotionListSetting) setting)) {
                result.add(new Suggestion(entry.key, entry.displayName));
            }
        }
        else if (setting instanceof ItemListSetting) {
            for (ItemSearchIndex.GroupedItemResult entry : ItemSearchIndex.searchGrouped(query, (ItemListSetting) setting)) {
                String value = entry.isSingleVariant() ? entry.variants.get(0).storageId : entry.getAllSelectionStorageId();
                result.add(new Suggestion(value, entry.getGroupLabel()));
            }
        }
        else if (setting instanceof BlockListSetting) {
            for (BlockSearchIndex.GroupedBlockResult entry : BlockSearchIndex.searchGrouped(query, (BlockListSetting) setting)) {
                String value = entry.isSingleVariant() ? entry.variants.get(0).storageId : entry.registryId + ":*";
                result.add(new Suggestion(value, entry.getGroupLabel()));
            }
        }
        return result;
    }

    private List<Entry> entries() {
        List<Entry> result = new ArrayList<Entry>();
        if (setting instanceof PlayerListSetting) {
            for (PlayerRelationsManager.PlayerEntry entry : ((PlayerListSetting) setting).getEntries()) {
                result.add(new Entry(entry.getKey(), entry.getDisplayName(), null));
            }
        }
        else if (setting instanceof StringListSetting) {
            for (String entry : ((StringListSetting) setting).getEntries()) {
                result.add(new Entry(entry, entry, null));
            }
        }
        else if (setting instanceof PotionListSetting) {
            for (String entry : ((PotionListSetting) setting).getPotions()) {
                result.add(new Entry(entry, PotionSearchIndex.getDisplayName(entry), null));
            }
        }
        else if (setting instanceof ItemListSetting) {
            for (String entry : ((ItemListSetting) setting).getItems()) {
                Integer slot = setting instanceof InventoryItemListSetting ? ((InventoryItemListSetting) setting).getAssignedSlot(entry) : null;
                result.add(new Entry(entry, ItemSearchIndex.getDisplayName(entry), slot));
            }
        }
        else if (setting instanceof BlockListSetting) {
            for (String entry : ((BlockListSetting) setting).getBlocks()) {
                result.add(new Entry(entry, BlockSearchIndex.getDisplayName(entry), null));
            }
        }
        return result;
    }

    private int entryCount() {
        if (setting instanceof PlayerListSetting) {
            return ((PlayerListSetting) setting).getEntries().size();
        }
        if (setting instanceof StringListSetting) {
            return ((StringListSetting) setting).getEntries().size();
        }
        if (setting instanceof BlockListSetting) {
            return ((BlockListSetting) setting).getBlocks().size();
        }
        return 0;
    }

    private void add(String value) {
        if (setting instanceof PlayerListSetting) {
            ((PlayerListSetting) setting).addPlayer(value);
        }
        else if (setting instanceof StringListSetting) {
            ((StringListSetting) setting).addEntry(value);
        }
        else if (setting instanceof PotionListSetting) {
            ((PotionListSetting) setting).addPotion(value);
        }
        else if (setting instanceof ItemListSetting) {
            ((ItemListSetting) setting).addItem(value);
        }
        else if (setting instanceof BlockListSetting) {
            ((BlockListSetting) setting).addBlock(value);
        }
    }

    private void remove(String value) {
        if (setting instanceof PlayerListSetting) {
            ((PlayerListSetting) setting).removePlayer(value);
        }
        else if (setting instanceof StringListSetting) {
            ((StringListSetting) setting).removeEntry(value);
        }
        else if (setting instanceof PotionListSetting) {
            ((PotionListSetting) setting).removePotion(value);
        }
        else if (setting instanceof ItemListSetting) {
            ((ItemListSetting) setting).removeItem(value);
        }
        else if (setting instanceof BlockListSetting) {
            ((BlockListSetting) setting).removeBlock(value);
        }
    }

    private void clear() {
        if (setting instanceof PlayerListSetting) {
            ((PlayerListSetting) setting).clearPlayers();
        }
        else if (setting instanceof StringListSetting) {
            ((StringListSetting) setting).clearEntries();
        }
        else if (setting instanceof BlockListSetting) {
            ((BlockListSetting) setting).getBlocks().clear();
        }
    }

    private void clampEntryOffset(int size) {
        entryOffset = Math.max(0, Math.min(entryOffset, Math.max(0, size - MAX_ENTRIES)));
    }

    private static String placeholder(Setting setting) {
        if (setting instanceof PlayerListSetting) {
            return ((PlayerListSetting) setting).getPlaceholder();
        }
        if (setting instanceof StringListSetting) {
            return ((StringListSetting) setting).getPlaceholder();
        }
        if (setting instanceof PotionListSetting) {
            return "Search potion effects...";
        }
        if (setting instanceof ItemListSetting) {
            return "Search items...";
        }
        return "Search blocks...";
    }

    private static int maxLength(Setting setting) {
        if (setting instanceof PlayerListSetting) {
            return ((PlayerListSetting) setting).getMaxLength();
        }
        if (setting instanceof StringListSetting) {
            return ((StringListSetting) setting).getMaxLength();
        }
        return 96;
    }

    private static final class Suggestion {
        final String value;
        final String display;

        Suggestion(String value, String display) {
            this.value = value;
            this.display = display;
        }
    }

    private static final class Entry {
        final String value;
        final String display;
        final Integer slot;

        Entry(String value, String display, Integer slot) {
            this.value = value;
            this.display = display;
            this.slot = slot;
        }
    }
}
