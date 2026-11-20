package idle.miau.module.setting.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import idle.miau.module.setting.Setting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

public final class RangeSetting extends Setting {
    private final double minBound;
    private final double maxBound;
    private final double interval;
    private final Random random = new Random();
    private String suffix = "";
    private final String[] legacyConfigKeys;
    private double minimum;
    private double maximum;
    public GroupSetting groupSetting;

    public RangeSetting(String name, double minBound, double maxBound,
                        double minimum, double maximum, double interval) {
        this(null, name, "", minBound, maxBound, minimum, maximum, interval);
    }

    public RangeSetting(String name, String suffix, double minBound, double maxBound,
                        double minimum, double maximum, double interval, String... legacyConfigKeys) {
        this(null, name, suffix, minBound, maxBound, minimum, maximum, interval, legacyConfigKeys);
    }

    public RangeSetting(GroupSetting groupSetting, String name, double minBound, double maxBound,
                        double minimum, double maximum, double interval) {
        this(groupSetting, name, "", minBound, maxBound, minimum, maximum, interval);
    }

    public RangeSetting(GroupSetting groupSetting, String name, String suffix, double minBound, double maxBound,
                        double minimum, double maximum, double interval, String... legacyConfigKeys) {
        super(name);
        this.groupSetting = groupSetting;
        this.suffix = suffix != null ? suffix : "";
        this.minBound = minBound;
        this.maxBound = maxBound;
        this.interval = interval;
        this.minimum = correct(minimum);
        this.maximum = Math.max(this.minimum, correct(maximum));
        this.legacyConfigKeys = legacyConfigKeys != null ? legacyConfigKeys : new String[0];
    }

    public String getSuffix() {
        return suffix;
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public double getMinBound() {
        return minBound;
    }

    public double getMaxBound() {
        return maxBound;
    }

    public double getInterval() {
        return interval;
    }

    public void setMinimum(double value) {
        minimum = Math.min(maximum, correct(value));
    }

    public void setMaximum(double value) {
        maximum = Math.max(minimum, correct(value));
    }

    public double getRandomInRange() {
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.addProperty("min", minimum);
        object.addProperty("max", maximum);
        return object;
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
        if (key == null && legacyConfigKeys != null) {
            for (String legacyKey : legacyConfigKeys) {
                if (data.has(legacyKey)) {
                    key = legacyKey;
                    break;
                }
            }
        }
        if (key == null) {
            return;
        }
        JsonElement value = data.get(key);
        try {
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                double loadedMinimum = object.has("min")
                        ? correct(object.get("min").getAsDouble()) : minimum;
                double loadedMaximum = object.has("max")
                        ? correct(object.get("max").getAsDouble()) : maximum;
                setRange(loadedMinimum, loadedMaximum);
            }
            else if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                if (array.size() >= 2) {
                    setRange(correct(array.get(0).getAsDouble()), correct(array.get(1).getAsDouble()));
                }
            }
            else if (value.isJsonPrimitive()) {
                double val = correct(value.getAsDouble());
                setRange(val, val);
            }
        }
        catch (Exception ignored) {
        }
    }

    private double correct(double value) {
        double clamped = Math.max(minBound, Math.min(maxBound, value));
        double rounded = Math.round(clamped / interval) * interval;
        return new BigDecimal(rounded).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
    }

    public void setRange(double first, double second) {
        minimum = Math.min(first, second);
        maximum = Math.max(first, second);
    }
}
