package idle.miau.command.impl;

import idle.miau.baritone.BaritoneBridge;
import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.ModuleManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BaritoneCommand extends Command {
    public BaritoneCommand() {
        super("baritone", "Control Baritone navigation, mining and building.", CommandCategory.GENERAL,
                new String[]{"baritone <command>", "baritone help"});
    }
    @Override
    public void execute(CommandInput input) {
        if (ModuleManager.baritone == null || !ModuleManager.baritone.isEnabled()) {
            reply("&cEnable the Baritone module first.");
            return;
        }
        BaritoneBridge.execute(input.argumentCount() == 0 ? "help" : input.joinArguments(0));
    }
    @Override
    public List<String> suggest(CommandInput input) {
        if (ModuleManager.baritone == null || !ModuleManager.baritone.isEnabled()) {
            return Collections.emptyList();
        }
        return Arrays.asList(BaritoneBridge.suggestArguments(input.joinArguments(0)));
    }
}
