package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

public class Toggle extends Command {
    public Toggle() {
        super("toggle", "Toggle a module on or off.", CommandCategory.MODULES, new String[] {"toggle <module>"}, "t");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            syntaxError();
            return;
        }

        Module module = findModule(input.joinArguments(0));
        if (module == null) {
            replyWithHeader("&cModule not found.");
            return;
        }

        module.toggle();
        replyWithHeader("&7" + module.getName() + " " + (module.isEnabled() ? "&aenabled" : "&cdisabled") + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        List<String> completions = new ArrayList<>();
        String query = input.argumentCount() == 0 ? "" : normalize(input.joinArguments(0));

        for (Module module : ModuleManager.modules) {
            if (normalize(module.getName()).startsWith(query)) {
                completions.add(module.getName());
            }
        }
        return completions;
    }

    @Override
    public int getSuggestionArgumentStart(CommandInput input) {
        return 0;
    }

    private Module findModule(String query) {
        String normalizedQuery = normalize(query);
        for (Module module : ModuleManager.modules) {
            if (normalize(module.getName()).equals(normalizedQuery)) {
                return module;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
