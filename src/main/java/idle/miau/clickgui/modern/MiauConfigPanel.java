package idle.miau.clickgui.modern;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.utility.Utils;
import idle.miau.utility.config.Config;
import idle.miau.utility.config.online.OnlineConfigEntry;
import idle.miau.utility.config.online.OnlineConfigService;
import idle.miau.utility.config.online.OnlineConfigSource;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class MiauConfigPanel extends MiauPanel {
    private static final String[] LOCAL_ACTIONS = {"Create", "Save", "Refresh", "Folder"};
    private final OnlineConfigService service = new OnlineConfigService();
    private final Map<OnlineConfigSource, Catalog> catalogs = new EnumMap<>(OnlineConfigSource.class);
    private final MiauModuleList local;
    private final MiauInput name;
    private Source selected = Source.OFFICIAL;
    private String status = "Click a config to load";
    private boolean applying;
    private float listTop;
    private float listHeight;

    MiauConfigPanel(MiauClickGui gui) {
        super(gui);
        for (OnlineConfigSource source : OnlineConfigSource.values()) catalogs.put(source, new Catalog());
        local = new MiauModuleList(gui);
        name = new MiauInput("Config name", "Type a config name...", gui, () -> runLocal("Create"));
    }

    @Override
    void open() {
        refreshLocal();
        if (selected != Source.LOCAL) requestIfNeeded(selected.source);
    }

    void refreshLocal() {
        List<Module> modules = Miau.configManager == null ? List.of()
                : Miau.configManager.configs.stream().map(config -> (Module) config.getModule()).toList();
        local.setModules(modules);
    }

    @Override
    void drawContent(MiauRenderer renderer, float mouseX, float mouseY) {
        String current = Miau.currentConfig == null ? "No active config" : Miau.currentConfig.getName();
        heading(renderer, "Config Manager", "\ue8b8", renderer.trim(MiauRenderer.MEDIUM, current, 8, width / 2));
        float sourceWidth = (width - 24) / 3;
        for (Source source : Source.values()) {
            button(renderer, source.title, x + 10 + source.ordinal() * (sourceWidth + 2), y + 34,
                    sourceWidth, 20, mouseX, mouseY, source == selected);
        }
        if (selected == Source.LOCAL) {
            name.drawAt(renderer, x + 10, y + 62, width - 20, mouseX, mouseY);
            float actionWidth = (width - 32) / LOCAL_ACTIONS.length;
            for (int index = 0; index < LOCAL_ACTIONS.length; index++) {
                button(renderer, LOCAL_ACTIONS[index], x + 10 + index * (actionWidth + 4), y + 94,
                        actionWidth, 20, mouseX, mouseY, index == 0);
            }
            listTop = y + 122;
        } else {
            Catalog catalog = catalogs.get(selected.source);
            renderer.text(MiauRenderer.MEDIUM, catalog.loading ? "Loading configs..."
                    : catalog.entries.size() + " configs available", x + 10, y + 75, 8, 0xffaaaaaa);
            button(renderer, "Refresh", x + width - 68, y + 62, 58, 20, mouseX, mouseY, false);
            listTop = y + 90;
        }
        listHeight = Math.max(0, y + height - 26 - listTop);
        if (selected == Source.LOCAL) local.draw(renderer, x + 10, listTop, width - 20, listHeight, mouseX, mouseY);
        else drawOnline(renderer, catalogs.get(selected.source), mouseX, mouseY);
        footer(renderer, selected == Source.LOCAL && status.equals("Click a config to load")
                ? "Left click to load | Right click to manage | Middle click to bind" : status);
    }

    private void drawOnline(MiauRenderer renderer, Catalog catalog, float mouseX, float mouseY) {
        catalog.clamp(listHeight);
        float offset = Math.min(catalog.maximum(listHeight), Math.max(0, catalog.scroll.value()));
        renderer.clip(x + 10, listTop, width - 20, listHeight, () -> {
            if (!catalog.message.isEmpty()) {
                renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, catalog.message, 9, width - 40),
                        x + 20, listTop + 18, 9, 0xffaaaaaa);
            } else {
                for (int index = 0; index < catalog.entries.size(); index++) {
                    OnlineConfigEntry entry = catalog.entries.get(index);
                    float rowY = listTop + index * 44 - offset;
                    renderer.rect(x + 10, rowY, width - 26, 42, 3,
                            MiauSetting.inside(mouseX, mouseY, x + 10, rowY, width - 26, 42) ? 0x25ffffff : 0x40000000);
                    renderer.text(MiauRenderer.BOLD, renderer.trim(MiauRenderer.BOLD, entry.getName(), 9, width - 82),
                            x + 20, rowY + 14, 9, -1);
                    String detail = entry.getAuthor() + "  |  " + entry.getUpdatedAt();
                    if (!entry.getDescription().isEmpty()) detail = entry.getDescription() + "  |  " + entry.getAuthor();
                    renderer.text(MiauRenderer.MEDIUM, renderer.trim(MiauRenderer.MEDIUM, detail, 8, width - 82),
                            x + 20, rowY + 30, 8, 0xffaaaaaa);
                    renderer.text(MiauRenderer.OUTLINED, "\ue2c4", x + width - 38, rowY + 25, 13, MiauRenderer.primary());
                }
            }
        });
        scrollbar(renderer, x + width - 12, listTop, listHeight, catalog.entries.size() * 44, offset);
    }

    @Override
    void click(float mouseX, float mouseY, int button) {
        if (button == 0) {
            float sourceWidth = (width - 24) / 3;
            for (Source source : Source.values()) {
                if (MiauSetting.inside(mouseX, mouseY, x + 10 + source.ordinal() * (sourceWidth + 2), y + 34, sourceWidth, 20)) {
                    cancel();
                    selected = source;
                    if (source == Source.LOCAL) refreshLocal();
                    else requestIfNeeded(source.source);
                    return;
                }
            }
        }
        if (selected == Source.LOCAL) {
            if (button == 0) {
                if (name.hit(mouseX, mouseY)) {
                    name.click(mouseX, mouseY, button);
                    return;
                }
                float actionWidth = (width - 32) / LOCAL_ACTIONS.length;
                for (int index = 0; index < LOCAL_ACTIONS.length; index++) {
                    if (MiauSetting.inside(mouseX, mouseY, x + 10 + index * (actionWidth + 4), y + 94, actionWidth, 20)) {
                        runLocal(LOCAL_ACTIONS[index]);
                        return;
                    }
                }
            }
            local.click(mouseX, mouseY, button);
            return;
        }
        if (button != 0) return;
        if (MiauSetting.inside(mouseX, mouseY, x + width - 68, y + 62, 58, 20)) {
            request(selected.source);
            return;
        }
        Catalog catalog = catalogs.get(selected.source);
        if (catalog.loading || !catalog.message.isEmpty()
                || !MiauSetting.inside(mouseX, mouseY, x + 10, listTop, width - 26, listHeight)) return;
        float offset = Math.min(catalog.maximum(listHeight), Math.max(0, catalog.scroll.value()));
        int index = (int) ((mouseY - listTop + offset) / 44);
        if (index >= 0 && index < catalog.entries.size()) load(catalog.entries.get(index), selected.source);
    }

    private void runLocal(String action) {
        if (Miau.configManager == null) {
            status = "Config manager unavailable";
            return;
        }
        switch (action) {
            case "Create" -> {
                if (name.text().isEmpty()) {
                    status = "Type a config name first";
                    return;
                }
                Config created = Miau.configManager.createConfig(name.text(), 0);
                status = created == null ? "Failed to create config" : "Created config: " + created.getName();
                if (created != null) name.clear();
            }
            case "Save" -> {
                Config config = Miau.currentConfig;
                status = config == null ? "Load or create a config first"
                        : Miau.configManager.saveConfig(config) ? "Saved config: " + config.getName() : "Failed to save config";
            }
            case "Refresh" -> {
                Miau.configManager.loadConfigs();
                status = "Local configs refreshed";
            }
            case "Folder" -> {
                try {
                    Desktop.getDesktop().open(Miau.configManager.directory);
                } catch (Exception error) {
                    status = Miau.configManager.directory.getAbsolutePath();
                }
            }
        }
        refreshLocal();
    }

    private void requestIfNeeded(OnlineConfigSource source) {
        Catalog catalog = catalogs.get(source);
        if (!catalog.loading && (source == OnlineConfigSource.OFFICIAL || !catalog.loaded)) request(source);
    }

    private void request(OnlineConfigSource source) {
        Catalog catalog = catalogs.get(source);
        if (catalog.loading) return;
        catalog.loading = true;
        catalog.loaded = false;
        catalog.message = "Loading configs...";
        service.list(source, entries -> {
            catalog.entries.clear();
            catalog.entries.addAll(entries);
            catalog.entries.sort(Comparator.comparing(OnlineConfigEntry::getName, String.CASE_INSENSITIVE_ORDER));
            catalog.message = entries.isEmpty() ? "No configs found" : "";
            catalog.loading = false;
            catalog.loaded = true;
        }, error -> {
            catalog.message = "Failed: " + error;
            catalog.loading = false;
        });
    }

    private void load(OnlineConfigEntry entry, OnlineConfigSource source) {
        if (applying) return;
        applying = true;
        status = "Loading " + entry.getName() + "...";
        service.load(source, entry, loaded -> {
            applying = false;
            status = loaded ? "Loaded config: " + entry.getName() : "Failed to apply config";
            if (loaded) {
                Utils.sendMessage("&7Loaded " + source.getPath() + " config: &b" + entry.getName());
                refreshLocal();
            }
        }, error -> {
            applying = false;
            status = "Failed: " + error;
        });
    }

    @Override
    void scroll(float mouseX, float mouseY, float steps) {
        if (selected == Source.LOCAL) {
            local.scroll(mouseX, mouseY, steps);
        } else if (MiauSetting.inside(mouseX, mouseY, x + 10, listTop, width - 20, listHeight)) {
            Catalog catalog = catalogs.get(selected.source);
            catalog.target -= steps * 44;
            catalog.clamp(listHeight);
            catalog.scroll.to(catalog.target);
        }
    }

    @Override
    void cancel() {
        name.cancel();
        local.cancel();
    }

    private enum Source {
        OFFICIAL("Official", OnlineConfigSource.OFFICIAL), LOCAL("Local", null), USER("User", OnlineConfigSource.USER);

        final String title;
        final OnlineConfigSource source;

        Source(String title, OnlineConfigSource source) {
            this.title = title;
            this.source = source;
        }
    }

    private static final class Catalog {
        final List<OnlineConfigEntry> entries = new ArrayList<>();
        final MiauAnimation scroll = new MiauAnimation(250, MiauAnimation.Curve.EXPO);
        String message = "";
        boolean loaded;
        boolean loading;
        float target;

        float maximum(float height) { return Math.max(0, entries.size() * 44 - height); }

        void clamp(float height) {
            float bounded = Math.max(0, Math.min(maximum(height), target));
            if (bounded != target) {
                target = bounded;
                scroll.to(target);
            }
        }
    }
}
