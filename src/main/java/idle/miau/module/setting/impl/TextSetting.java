package idle.miau.module.setting.impl;

import com.google.gson.JsonObject;

import idle.miau.module.setting.Setting;

public class TextSetting extends Setting {
    private final String placeholder;
    private final int maxLength;
    private final Runnable onSubmit;
    private String text;
    private boolean sensitive;
    private boolean masked;
    private long revealUntil;
    public GroupSetting group;

    public TextSetting(String name, String text, String placeholder, int maxLength) {
        this(name, text, placeholder, maxLength, null);
    }

    public TextSetting(
            String name, String text, String placeholder, int maxLength, Runnable onSubmit) {
        super(name);
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = Math.max(1, maxLength);
        this.onSubmit = onSubmit;
        setText(text);
    }

    public TextSetting(
            GroupSetting group, String name, String text, String placeholder, int maxLength) {
        this(group, name, text, placeholder, maxLength, null);
    }

    public TextSetting(
            GroupSetting group,
            String name,
            String text,
            String placeholder,
            int maxLength,
            Runnable onSubmit) {
        this(name, text, placeholder, maxLength, onSubmit);
        this.group = group;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        String next = text == null ? "" : text;
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        this.text = next;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public TextSetting setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
        if (sensitive) this.masked = true;
        return this;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public TextSetting setMasked(boolean masked) {
        this.masked = masked;
        return this;
    }

    public boolean isMasked() {
        return masked && System.currentTimeMillis() >= revealUntil;
    }

    public void revealTemporarily(long durationMs) {
        revealUntil = System.currentTimeMillis() + Math.max(0L, durationMs);
    }

    @Override
    public String getConfigKey() {
        return group == null ? getName() : group.getName() + "." + getName();
    }

    public void submit() {
        if (onSubmit != null) {
            onSubmit.run();
        }
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (sensitive || data == null) {
            return;
        }
        String key = getConfigKey();
        if (data.has(key) && data.get(key).isJsonPrimitive()) {
            setText(data.get(key).getAsString());
        }
    }
}
