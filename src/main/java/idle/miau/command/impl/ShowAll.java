package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;

public class ShowAll extends Command {
    public ShowAll() {
        super("show", "Show every module in the HUD list.", CommandCategory.MODULES, new String[] {"show", "show all"}, "showall");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() > 0 && !"all".equalsIgnoreCase(input.getArgument(0))) {
            syntaxError();
            return;
        }

        int count = 0;
        for (Module module : ModuleManager.modules) {
            if (module.isHidden()) {
                module.setHidden(false);
                count++;
            }
        }
        replyWithHeader("&7Made &a" + count + " &7module" + (count == 1 ? "" : "s") + " visible in HUD.");
    }
}
