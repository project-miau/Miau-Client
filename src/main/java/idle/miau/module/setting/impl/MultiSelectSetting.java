package idle.miau.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import idle.miau.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MultiSelectSetting extends Setting {
    private final String[] options;
    private final Set<String> selected = new LinkedHashSet<String>();
    public GroupSetting groupSetting;

    public MultiSelectSetting(String name, String[] options, String... defaults) {
        this(null, name, options, defaults);
    }

    public MultiSelectSetting(GroupSetting groupSetting, String name, String[] options, String... defaults) {
        super(name);
        this.groupSetting = groupSetting;
        this.options = options == null ? new String[0] : options.clone();
        if (defaults != null) {
            for (String value : defaults) {
                setSelected(value, true);
            }
        }
    }

    public String[] getOptions() {
        return options.clone();
    }

    public List<String> getSelected() {
        return Collections.unmodifiableList(new ArrayList<String>(selected));
    }

    public int getSelectedCount() {
        return selected.size();
    }

    public boolean isSelected(String option) {
        String canonical = findOption(option);
        return canonical != null && selected.contains(canonical);
    }

    public boolean setSelected(String option, boolean enabled) {
        String canonical = findOption(option);
        if (canonical == null) {
            return false;
        }
        return enabled ? selected.add(canonical) : selected.remove(canonical);
    }

    public boolean toggle(String option) {
        String canonical = findOption(option);
        if (canonical == null) {
            return false;
        }
        if (selected.remove(canonical)) {
            return true;
        }
        selected.add(canonical);
        return true;
    }

    public void clear() {
        selected.clear();
    }

    public void selectAll() {
        selected.clear();
        Collections.addAll(selected, options);
    }

    public void selectOnly(String option) {
        String canonical = findOption(option);
        if (canonical == null) {
            return;
        }
        selected.clear();
        selected.add(canonical);
    }

    public JsonArray toJsonArray() {
        JsonArray array = new JsonArray();
        for (String option : selected) {
            array.add(new JsonPrimitive(option));
        }
        return array;
    }

    @Override
    public String getConfigKey() {
        return groupSetting == null ? getName() : groupSetting.getName() + "." + getName();
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) {
            return;
        }
        String key = data.has(getConfigKey()) ? getConfigKey() : data.has(getName()) ? getName() : null;
        if (key == null) {
            return;
        }
        JsonElement value = data.get(key);
        if (value.isJsonArray()) {
            selected.clear();
            for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    setSelected(entry.getAsString(), true);
                }
            }
            return;
        }
        if (!value.isJsonPrimitive()) {
            return;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            int index = primitive.getAsInt();
            if (index >= 0 && index < options.length) {
                selectOnly(options[index]);
            }
            return;
        }
        String[] values = primitive.getAsString().split(",");
        selected.clear();
        for (String entry : values) {
            setSelected(entry.trim(), true);
        }
    }

    private String findOption(String value) {
        if (value == null) {
            return null;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(value.trim())) {
                return option;
            }
        }
        return null;
    }
}
