package idle.miau.clickgui.modern;

import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.*;
import idle.miau.utility.BlockSearchIndex;
import idle.miau.utility.ItemSearchIndex;
import idle.miau.utility.PotionSearchIndex;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

final class MiauList extends MiauText {
    private boolean opened;
    private final List<Entry> suggestions = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();

    MiauList(Setting setting, Module owner, MiauClickGui gui) {
        super(setting, owner, gui, setting instanceof StringListSetting list ? list.getMaxLength()
                : setting instanceof PlayerListSetting list ? list.getMaxLength() : 96);
    }

    @Override
    float height(MiauRenderer renderer) {
        refresh();
        return opened ? 32 + (entries.size() + suggestions.size()) * 13 : 17;
    }

    private void refresh() {
        entries.clear();
        suggestions.clear();
        if (setting instanceof StringListSetting list) {
            list.getEntries().forEach(value -> entries.add(new Entry(value, value)));
        } else if (setting instanceof PlayerListSetting list) {
            list.getEntries().forEach(value -> entries.add(new Entry(value.getKey(), value.getDisplayName())));
        } else if (setting instanceof BlockListSetting list) {
            for (String value : list.getBlocks()) {
                String name = setting instanceof PotionListSetting ? PotionSearchIndex.getDisplayName(value)
                        : setting instanceof ItemListSetting ? ItemSearchIndex.getDisplayName(value) : BlockSearchIndex.getDisplayName(value);
                if (setting instanceof InventoryItemListSetting inventory && inventory.getAssignedSlot(value) != null) {
                    name += " [" + inventory.getAssignedSlot(value) + "]";
                }
                entries.add(new Entry(value, name));
            }
        }
        String query = field.getText().trim();
        if (!opened || query.isEmpty()) return;
        if (setting instanceof PotionListSetting list) {
            PotionSearchIndex.search(query, list).stream().limit(4).forEach(value -> suggestions.add(new Entry(value.key, value.displayName)));
        } else if (setting instanceof ItemListSetting list) {
            ItemSearchIndex.searchGrouped(query, list).stream().limit(4).forEach(value -> suggestions.add(new Entry(
                    value.isSingleVariant() ? value.variants.get(0).storageId : value.getAllSelectionStorageId(), value.getGroupLabel())));
        } else if (setting instanceof BlockListSetting list) {
            BlockSearchIndex.searchGrouped(query, list).stream().limit(4).forEach(value -> suggestions.add(new Entry(
                    value.isSingleVariant() ? value.variants.get(0).storageId : value.registryId + ":*", value.getGroupLabel())));
        }
    }

    @Override
    void draw(MiauRenderer renderer, float mouseX, float mouseY) {
        background(renderer);
        String name = setting.getName() + " (" + entries.size() + ")";
        renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, name, 7, width - 23), x + 5, y + 10.5f, 7, -1);
        renderer.chevron(x + width - 12, y + 8.5f, 10, opened ? 1 : 0, -1);
        if (!opened) return;
        drawField(renderer, y + 18);
        float nextY = y + 32;
        for (Entry entry : suggestions) {
            renderer.rect(x + 5, nextY, width - 10, 12, 2, MiauRenderer.opacity(MiauRenderer.primary(), .25f));
            renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, entry.name, 7, width - 16), x + 7, nextY + 9, 7, -1);
            nextY += 13;
        }
        for (Entry entry : entries) {
            renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, entry.name, 7, width - 23), x + 7, nextY + 9, 7, 0xff808080);
            renderer.text(MiauRenderer.MEDIUM, "×", x + width - 12, nextY + 9, 7, -1);
            nextY += 13;
        }
    }

    @Override
    boolean click(float mouseX, float mouseY, int button) {
        if (mouseY < y + 17) {
            if (button != 1 && button != 0) return false;
            opened = !opened;
            if (!opened) cancel();
            return true;
        }
        if (!opened) return false;
        if (mouseY < y + 32) {
            if (button != 0) return false;
            focusAt(mouseX);
            return true;
        }
        int index = (int) ((mouseY - y - 32) / 13);
        if (index < suggestions.size()) {
            if (button != 0) return false;
            add(suggestions.get(index).value);
            field.setText("");
        } else {
            index -= suggestions.size();
            if (index >= entries.size()) return false;
            String value = entries.get(index).value;
            if (button == 1 && setting instanceof InventoryItemListSetting inventory) {
                Integer current = inventory.getAssignedSlot(value);
                inventory.setAssignedSlot(value, current == null || current == 9 ? 1 : current + 1);
            } else if (button == 0) remove(value);
            else return false;
        }
        changed();
        return true;
    }

    @Override
    boolean key(char character, int code) {
        if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
            String value = suggestions.isEmpty() ? field.getText().trim() : suggestions.get(0).value;
            if (!value.isEmpty()) {
                add(value);
                field.setText("");
                changed();
            }
            return true;
        }
        return super.key(character, code);
    }

    private void add(String value) {
        if (setting instanceof StringListSetting list) list.addEntry(value);
        else if (setting instanceof PlayerListSetting list) list.addPlayer(value);
        else if (setting instanceof ItemListSetting list) list.addItem(value);
        else if (setting instanceof PotionListSetting list) list.addPotion(value);
        else if (setting instanceof BlockListSetting list) list.addBlock(value);
    }

    private void remove(String value) {
        if (setting instanceof StringListSetting list) list.removeEntry(value);
        else if (setting instanceof PlayerListSetting list) list.removePlayer(value);
        else if (setting instanceof ItemListSetting list) list.removeItem(value);
        else if (setting instanceof PotionListSetting list) list.removePotion(value);
        else if (setting instanceof BlockListSetting list) list.removeBlock(value);
    }

    private record Entry(String value, String name) {}
}
