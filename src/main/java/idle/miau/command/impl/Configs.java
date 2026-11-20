package idle.miau.command.impl;

import idle.miau.Miau;
import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.utility.Utils;
import idle.miau.utility.config.Config;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Configs extends Command {
    public Configs() {
        super("config", "Manage local configurations.", CommandCategory.CONFIG,
                new String[] {"config", "config list", "config refresh", "config folder", "config save <name>",
                        "config load <name>", "config delete <name>", "config rename <old> <new>"},
                "c", "cfg");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            handleList();
            return;
        }

        String subCommand = input.getArgument(0).toLowerCase();
        switch (subCommand) {
            case "list":
            case "ls":
                handleList();
                return;
            case "refresh":
            case "reload":
                handleRefresh();
                return;
            case "folder":
            case "dir":
            case "open":
                handleFolder();
                return;
            case "save":
            case "s":
                handleSave(input);
                return;
            case "load":
            case "l":
                handleLoad(input);
                return;
            case "delete":
            case "remove":
            case "r":
                handleDelete(input);
                return;
            case "rename":
                handleRename(input);
                return;
            default:
                syntaxError();
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.argumentCount() == 1) {
            return filterSuggestions(input, "list", "refresh", "folder", "save", "s", "load", "l", "delete", "remove", "r", "rename");
        }

        String subCommand = input.getArgument(0);
        if (isLoadOrDeleteCommand(subCommand)) {
            return suggestConfigNames(input.joinArguments(1));
        }

        if (isRenameCommand(subCommand) && input.argumentCount() == 2) {
            return suggestConfigNames(input.joinArguments(1));
        }

        return new ArrayList<>();
    }

    private void handleList() {
        List<File> files = new ArrayList<>(Miau.configManager.getConfigFiles());
        files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        if (files.isEmpty()) {
            replyWithHeader("&7No configs found.");
            return;
        }

        replyWithHeader("&b" + files.size() + " &7config" + (files.size() == 1 ? "" : "s") + ":");
        String currentName = Miau.currentConfig == null ? null : Miau.currentConfig.getName();
        for (File file : files) {
            String fileName = file.getName();
            String configName = fileName.substring(0, fileName.length() - ".json".length());
            boolean current = currentName != null && currentName.equalsIgnoreCase(configName);
            reply(" &7" + configName + (current ? " &7(&bcurrent&7)" : ""));
        }
    }

    private void handleRefresh() {
        if (Miau.configManager == null) {
            replyWithHeader("&cConfig manager is unavailable.");
            return;
        }
        Miau.configManager.loadConfigs();
    }

    private void handleFolder() {
        File directory = Miau.configManager.directory;
        if (!directory.exists() && !directory.mkdirs()) {
            replyWithHeader("&cFailed to create the config folder.");
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            replyWithHeader("&cOpening folders is not supported on this system.");
            return;
        }
        try {
            Desktop.getDesktop().open(directory);
            replyWithHeader("&7Opened config folder: &b" + directory.getAbsolutePath());
        }
        catch (Exception exception) {
            replyWithHeader("&cFailed to open config folder: &b" + directory.getAbsolutePath());
        }
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        String subCommand = input.getArgument(0);
        if (isLoadOrDeleteCommand(subCommand) || isRenameCommand(subCommand)) {
            return 1;
        }
        return super.getSuggestionArgumentStart(input);
    }

    private void handleSave(CommandInput input) {
        if (input.argumentCount() < 2) {
            if (Miau.currentConfig != null) {
                Utils.sendMessage("&7Saved config: &b" + Miau.currentConfig);
                Miau.configManager.saveConfig(Miau.currentConfig);
            } else {
                syntaxError();
            }
            return;
        }

        String saveName = input.joinArguments(1);
        Config savedConfig = Miau.configManager.getConfig(saveName);
        if (savedConfig == null) {
            savedConfig = Miau.configManager.createConfig(saveName, 0);
            if (savedConfig == null) {
                return;
            }
        } else if (!Miau.configManager.saveConfig(savedConfig)) {
            replyWithHeader("&cFailed to save config: &b" + saveName);
            return;
        }
        replyWithHeader("&7Saved config: &b" + saveName);
    }

    private void handleLoad(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }

        String loadName = input.joinArguments(1);
        if (Miau.configManager.getConfig(loadName) == null) {
            replyWithHeader("&b" + loadName + " &7does not exist");
            return;
        }

        if (Miau.configManager.loadConfig(loadName)) {
            replyWithHeader("&7Enabled config: &b" + loadName);
        }
        else {
            replyWithHeader("&cFailed to load config: &b" + loadName);
        }
    }

    private void handleDelete(CommandInput input) {
        if (input.argumentCount() < 2) {
            syntaxError();
            return;
        }

        String deleteName = input.joinArguments(1);
        if (Miau.configManager.getConfig(deleteName) == null) {
            replyWithHeader("&cConfig &b" + deleteName + " &7does not exist");
            return;
        }

        Miau.configManager.deleteConfig(deleteName);
        replyWithHeader("&7Removed config: &b" + deleteName);
        Miau.configManager.loadConfigs();
    }

    private void handleRename(CommandInput input) {
        if (input.argumentCount() < 3) {
            syntaxError();
            return;
        }

        RenameArguments renameArguments = parseRenameArguments(input.joinArguments(1));
        if (renameArguments == null) {
            replyWithHeader("&7Rename requires an existing config name followed by the new name.");
            return;
        }

        String oldName = renameArguments.oldName;
        String newName = renameArguments.newName;
        Config oldConfig = Miau.configManager.getConfig(oldName);
        if (oldConfig == null) {
            replyWithHeader("&b" + oldName + " &7does not exist");
            return;
        }

        if (Miau.configManager.getConfig(newName) != null) {
            replyWithHeader("&b" + newName + " &7already exists");
            return;
        }

        if (!Miau.configManager.renameConfig(oldConfig, newName)) {
            return;
        }

        replyWithHeader("&b" + oldName + " &7renamed to &b" + oldConfig.getName());
    }

    private List<String> suggestConfigNames(String query) {
        String loweredQuery = query == null ? "" : query.toLowerCase();
        List<String> configNames = new ArrayList<>();
        for (Config config : Miau.configManager.configs) {
            if (config.getName().toLowerCase().startsWith(loweredQuery)) {
                configNames.add(config.getName());
            }
        }
        return configNames;
    }

    private boolean isLoadOrDeleteCommand(String subCommand) {
        return "load".equalsIgnoreCase(subCommand)
            || "l".equalsIgnoreCase(subCommand)
            || "delete".equalsIgnoreCase(subCommand)
            || "remove".equalsIgnoreCase(subCommand)
            || "r".equalsIgnoreCase(subCommand);
    }

    private boolean isRenameCommand(String subCommand) {
        return "rename".equalsIgnoreCase(subCommand);
    }

    private RenameArguments parseRenameArguments(String remaining) {
        if (remaining == null) {
            return null;
        }

        String trimmed = remaining.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        for (Config config : Miau.configManager.configs) {
            String configName = config.getName();
            if (trimmed.length() <= configName.length()) {
                continue;
            }

            if (!trimmed.regionMatches(true, 0, configName, 0, configName.length())) {
                continue;
            }

            if (!Character.isWhitespace(trimmed.charAt(configName.length()))) {
                continue;
            }

            if (bestMatch == null || configName.length() > bestMatch.length()) {
                bestMatch = configName;
            }
        }

        if (bestMatch == null) {
            return null;
        }

        String newName = trimmed.substring(bestMatch.length()).trim();
        if (newName.isEmpty()) {
            return null;
        }

        return new RenameArguments(bestMatch, newName);
    }

    private static final class RenameArguments {
        private final String oldName;
        private final String newName;

        private RenameArguments(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }
    }
}
