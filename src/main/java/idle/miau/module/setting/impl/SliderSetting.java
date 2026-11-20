package idle.miau.module.setting.impl;

import com.google.gson.JsonObject;
import idle.miau.event.PostSetSliderEvent;
import idle.miau.module.setting.Setting;
import idle.miau.mcp.McpRuntime;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderSetting extends Setting {
    private String settingName;
    private String[] options = null;
    private double defaultValue;
    private double max;
    private double min;
    private double intervals;
    public boolean isString;
    private String suffix = "";
    private String minString;
    public boolean canBeDisabled;
    public GroupSetting groupSetting;
    private String[] legacyConfigKeys;

    public SliderSetting(GroupSetting groupSetting, String settingName, double defaultValue, double min, double max, double intervals) {
        super(settingName);
        this.groupSetting = groupSetting;
        this.settingName = settingName;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.intervals = intervals;
        this.isString = false;
        this.legacyConfigKeys = new String[0];
    }

    public SliderSetting(String settingName, double defaultValue, double min, double max, double intervals) {
        this((GroupSetting) null, settingName, defaultValue, min, max, intervals);
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, String suffix, double defaultValue, double min, double max, double intervals) {
        this(groupSetting, settingName, defaultValue, min, max, intervals);
        this.suffix = suffix;
    }

    public SliderSetting(String settingName, String suffix, double defaultValue, double min, double max, double intervals) {
        this((GroupSetting) null, settingName, defaultValue, min, max, intervals);
        this.suffix = suffix;
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, String suffix, String minString,
                         double defaultValue, double min, double max, double intervals) {
        this(groupSetting, settingName, suffix, defaultValue, min, max, intervals);
        this.minString = minString;
    }

    public SliderSetting(String settingName, String suffix, String minString,
                         double defaultValue, double min, double max, double intervals) {
        this(null, settingName, suffix, minString, defaultValue, min, max, intervals);
    }

    public SliderSetting(String settingName, String suffix, double defaultValue, double min, double max,
                         double intervals, String... legacyConfigKeys) {
        this((GroupSetting) null, settingName, defaultValue, min, max, intervals);
        this.suffix = suffix;
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, String suffix, double defaultValue,
                         double min, double max, double intervals, String... legacyConfigKeys) {
        this(groupSetting, settingName, suffix, defaultValue, min, max, intervals);
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public SliderSetting(String settingName, boolean canBeDisabled, double defaultValue, double min, double max, double intervals) {
        this(settingName, defaultValue, min, max, intervals);
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(GroupSetting group, String settingName, boolean canBeDisabled, double defaultValue, double min, double max, double intervals) {
        this(group, settingName, defaultValue, min, max, intervals);
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(String settingName, String suffix, boolean canBeDisabled, double defaultValue, double min, double max, double intervals) {
        this(settingName, defaultValue, min, max, intervals);
        this.suffix = suffix;
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(String settingName, String suffix, String minString, boolean canBeDisabled,
                         double defaultValue, double min, double max, double intervals) {
        this(settingName, suffix, defaultValue, min, max, intervals);
        this.minString = minString;
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, String suffix, String minString,
                         boolean canBeDisabled, double defaultValue, double min, double max, double intervals) {
        this(groupSetting, settingName, suffix, defaultValue, min, max, intervals);
        this.minString = minString;
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, int defaultValue, String[] options) {
        super(settingName);
        this.groupSetting = groupSetting;
        this.settingName = settingName;
        this.options = options;
        this.defaultValue = defaultValue;
        this.min = 0;
        this.max = options.length - 1;
        this.intervals = 1;
        this.isString = true;
        this.legacyConfigKeys = new String[0];
    }

    public SliderSetting(String settingName, int defaultValue, String[] options) {
        this((GroupSetting) null, settingName, defaultValue, options);
    }

    public SliderSetting(String settingName, boolean canBeDisabled, int defaultValue, String[] options) {
        this(settingName, defaultValue, options);
        this.canBeDisabled = canBeDisabled;
    }

    public SliderSetting(String settingName, int defaultValue, String[] options, String... legacyConfigKeys) {
        this((GroupSetting) null, settingName, defaultValue, options);
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public SliderSetting(String settingName, String suffix, int defaultValue, String[] options) {
        this((GroupSetting) null, settingName, defaultValue, options);
        this.suffix = suffix;
    }

    public SliderSetting(GroupSetting groupSetting, String settingName, String suffix, int defaultValue, String[] options) {
        this(groupSetting, settingName, defaultValue, options);
        this.suffix = suffix;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public String getMinString() {
        return minString;
    }

    public void setMinString(String minString) {
        this.minString = minString;
    }

    public String[] getOptions() {
        return options;
    }

    public double getIntervals() {
        return this.intervals;
    }

    public String getName() {
        return this.settingName;
    }

    @Override
    public String getConfigKey() {
        return groupSetting == null ? getName() : groupSetting.getName() + "." + getName();
    }

    public double getInput() {
        return roundToInterval(this.defaultValue, 4);
    }

    public double getMin() {
        return this.min;
    }

    public double getMax() {
        return this.max;
    }

    public double setValue(double newValue) {
        newValue = correctValue(newValue, this.min, this.max);
        newValue = (double) Math.round(newValue * (1.0D / this.intervals)) / (1.0D / this.intervals);
        return this.defaultValue = newValue;
    }

    public void setValueWithEvent(double newValue) {
        double prev = this.defaultValue;
        McpRuntime.EVENTS.post(new PostSetSliderEvent(prev, this.setValue(newValue)));
    }

    public void setValueRaw(double n) {
        this.defaultValue = n;
    }

    public void setValueRawWithEvent(double n) {
        double prev = this.defaultValue;
        this.defaultValue = n;
        McpRuntime.EVENTS.post(new PostSetSliderEvent(prev, n));
    }

    public static double correctValue(double v, double i, double a) {
        v = Math.max(i, v);
        v = Math.min(a, v);
        return v;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public static double roundToInterval(double v, int p) {
        if (p < 0) {
            return 0.0D;
        } else {
            BigDecimal bd = new BigDecimal(v);
            bd = bd.setScale(p, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
    }

    @Override
    public void loadConfig(JsonObject data) {
        String configKey = getConfigKey();
        String legacyKey = getName();
        String[] configKeys = new String[legacyConfigKeys.length + 2];
        configKeys[0] = configKey;
        configKeys[1] = legacyKey;
        System.arraycopy(legacyConfigKeys, 0, configKeys, 2, legacyConfigKeys.length);
        for (String key : configKeys) {
            if (key == null || !data.has(key) || !data.get(key).isJsonPrimitive()) {
                continue;
            }
            try {
                double newValue = data.getAsJsonPrimitive(key).getAsDouble();
                if (newValue == -1) {
                    setValueRaw(newValue);
                } else {
                    setValue(newValue);
                }
                return;
            } catch (Exception ignored) {
            }
        }
    }
}
