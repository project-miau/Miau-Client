package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

public final class Hide extends Command {
    public Hide() {
        super(
                "hide",
                "Toggle a module's HUD visibility or hide every module.",
                CommandCategory.MODULES,
                new String[]{"hide <module>", "hide all", "hide"},
                "hideall"
        );
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0 || input.argumentCount() == 1 && input.argumentEquals(0, "all")) {
            hideAll();
            return;
        }

        Module module = findModule(input.joinArguments(0));
        if (module == null) {
            replyWithHeader("&cModule not found.");
            return;
        }

        module.setHidden(!module.isHidden());
        ModuleManager.sort();
        replyWithHeader(
                "&7" + module.getName() + " is now "
                        + (module.isHidden() ? "&chidden" : "&avisible")
                        + " &7in HUD."
        );
    }

    @Override
    public List<String> suggest(CommandInput input) {
        List<String> completions = new ArrayList<String>();
        String query = ModuleSettingsCommand.normalize(input.joinArguments(0));

        if ("all".startsWith(query)) {
            completions.add("all");
        }
        for (Module module : ModuleManager.modules) {
            if (ModuleSettingsCommand.normalize(module.getName()).startsWith(query)) {
                completions.add(module.getName());
            }
        }
        return completions;
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
    }

    private void hideAll() {
        int count = 0;
        for (Module module : ModuleManager.modules) {
            if (!module.isHidden()) {
                module.setHidden(true);
                count++;
            }
        }
        ModuleManager.sort();
        replyWithHeader("&7Hidden &c" + count + " &7module" + (count == 1 ? "" : "s") + " from HUD.");
    }

    private Module findModule(String query) {
        String normalizedQuery = ModuleSettingsCommand.normalize(query);
        for (Module module : ModuleManager.modules) {
            if (ModuleSettingsCommand.normalize(module.getName()).equals(normalizedQuery)) {
                return module;
            }
        }
        return null;
    }
}
