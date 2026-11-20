package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Unbind extends Command {
    public Unbind() {
        super("unbind", "Remove a module key binding.", CommandCategory.MODULES, new String[] {"unbind <module>"}, "ub");
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

        module.setBind(0);
        replyWithHeader("&7Unbound &b" + module.getName() + "&7.");
    }

    @Override
    public List<String> suggest(CommandInput input) {
        String query = input.argumentCount() == 0 ? "" : input.joinArguments(0);
        List<String> completions = new ArrayList<>();
        String normalized = normalize(query);
        for (Module module : ModuleManager.modules) {
            String moduleName = module.getName();
            if (normalize(moduleName).startsWith(normalized)) {
                completions.add(moduleName);
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
        return value == null ? "" : value.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
