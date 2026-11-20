package idle.miau.utility.config.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class OnlineConfigEntry {
    private String id;
    private String name;
    private String description;
    private String author;
    private String updatedAt;
    private int downloads;
    private String version;
    private String source;
    private String type;
    private String status;

    static OnlineConfigEntry fromJson(JsonObject object) {
        OnlineConfigEntry entry = new OnlineConfigEntry();
        entry.id = string(object, "id", "config_id", "setting_id");
        entry.name = string(object, "name");
        entry.description = string(object, "description", "note");
        entry.author = string(object, "author", "contributors");
        entry.updatedAt = string(object, "updatedAt", "date");
        entry.downloads = integer(object, "downloads", "load_count");
        entry.version = string(object, "version");
        entry.source = string(object, "source");
        entry.type = string(object, "type", "setting_type");
        entry.status = string(object, "status", "status_type");
        return entry;
    }

    public String getId() {
        return value(id, "");
    }

    public String getName() {
        return value(name, getId());
    }

    public String getDescription() {
        return value(description, "");
    }

    public String getAuthor() {
        return value(author, "unknown");
    }

    public String getUpdatedAt() {
        return value(updatedAt, "unknown");
    }

    public int getDownloads() {
        return Math.max(0, downloads);
    }

    public String getVersion() {
        return value(version, "");
    }

    public String getSource() {
        return value(source, "");
    }

    public String getType() {
        return value(type, "");
    }

    public String getStatus() {
        return value(status, "");
    }

    private static String value(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String string(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && value.isJsonPrimitive()) {
                try {
                    String text = value.getAsString();
                    if (text != null && !text.trim().isEmpty()) {
                        return text.trim();
                    }
                }
                catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private static int integer(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && value.isJsonPrimitive()) {
                try {
                    return Math.max(0, value.getAsInt());
                }
                catch (Exception ignored) {
                }
            }
        }
        return 0;
    }
}
