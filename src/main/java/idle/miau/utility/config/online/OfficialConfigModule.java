package idle.miau.utility.config.online;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.utility.Utils;

public final class OfficialConfigModule extends Module {
    private static final OnlineConfigService SERVICE = new OnlineConfigService();

    private final OnlineConfigEntry entry;
    private boolean loading;

    public OfficialConfigModule(OnlineConfigEntry entry) {
        super(entry.getName(), category.official_config);
        this.entry = entry;
        registerSetting(new DescriptionSetting("By " + entry.getAuthor()));
        if (!entry.getDescription().isEmpty()) {
            registerSetting(new DescriptionSetting(trim(entry.getDescription(), 72)));
        }
        registerSetting(new DescriptionSetting("Downloads: " + entry.getDownloads()));
        ignoreOnSave = true;
    }

    private OfficialConfigModule(String status) {
        super(status, category.official_config);
        entry = null;
        canBeEnabled = false;
        ignoreOnSave = true;
    }

    public static OfficialConfigModule status(String status) {
        return new OfficialConfigModule(status);
    }

    @Override
    public void toggle() {
        if (entry == null || loading) {
            return;
        }
        loading = true;
        SERVICE.load(
                OnlineConfigSource.OFFICIAL,
                entry,
                loaded -> {
                    loading = false;
                    if (loaded) {
                        Utils.sendMessage("&7Loaded official config: &b" + entry.getName());
                    } else {
                        Utils.sendMessage("&cFailed to apply official config: &b" + entry.getName());
                    }
                },
                error -> {
                    loading = false;
                    Utils.sendMessage("&cFailed to load official config: " + error);
                }
        );
    }

    @Override
    public boolean isEnabled() {
        return loading;
    }

    @Override
    public boolean canBeEnabled() {
        return entry != null && !loading;
    }

    public OnlineConfigEntry getEntry() {
        return entry;
    }

    private static String trim(String value, int length) {
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }
}
