package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.ModuleManager;
import idle.miau.utility.Utils;
import keystrokesmod.Raven;
import keystrokesmod.script.Script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScriptCommand extends Command {
    public ScriptCommand() {
        super("script", "Manage Java scripts from miau/scripts.", CommandCategory.UTILITY,
                new String[]{"script", "script load", "script list", "script refresh"}, "scripts");
    }

    @Override
    public void execute(CommandInput input) {
        String subCommand = input.getArguments().length >= 1
                ? input.getArgument(0).toLowerCase(Locale.ROOT) : "load";
        switch (subCommand) {
            case "list":
                list();
                return;
            case "refresh":
            case "load":
                ModuleManager.scriptManager.loadScripts(System.currentTimeMillis());
                Utils.sendMessage("&7Scripts refreshed from &bmiau/scripts&7.");
                return;
            default:
                Utils.sendMessage("&cUsage: .script <load|list|refresh>");
        }
    }

    private void list() {
        if (Raven.scriptManager == null) {
            Utils.sendMessage("&cScript manager is unavailable.");
            return;
        }
        Map<Script, idle.miau.module.Module> scripts = Raven.scriptManager.scripts;
        if (scripts.isEmpty()) {
            Utils.sendMessage("&7No scripts loaded. Use &b.script refresh&7.");
            return;
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<Script, idle.miau.module.Module> entry : scripts.entrySet()) {
            Script script = entry.getKey();
            String status = script.error ? " &c[compile error]" : "";
            lines.add(" &7- &f" + script.name + status);
        }
        lines.sort(String.CASE_INSENSITIVE_ORDER);

        Utils.sendMessage("&b" + lines.size() + " &7script" + (lines.size() == 1 ? "" : "s") + ":");
        for (String line : lines) {
            Utils.sendMessage(line);
        }
    }

    @Override
    public List<String> suggest(CommandInput input) {
        if (input.getArguments().length <= 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("list");
            suggestions.add("refresh");
            suggestions.add("load");
            return suggestions;
        }
        return Collections.emptyList();
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
    }
}
