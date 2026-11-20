package idle.miau.command.impl;

import idle.miau.Miau;
import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.helper.DebugHelper;

public class Debug extends Command {
    public Debug() {
        super("debug", "Toggle diagnostic logging.", CommandCategory.UTILITY, new String[] {"debug"});
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            Miau.DEBUG = !Miau.DEBUG;
            replyWithHeader("&7Debug " + (Miau.DEBUG ? "&aenabled" : "&cdisabled") + "&7.");
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String option = input.getArgument(0).toLowerCase();
        if ("bg".equals(option) || "background".equals(option)) {
            DebugHelper.BACKGROUND = !DebugHelper.BACKGROUND;
            replyWithHeader("&6Background &7debug " + (DebugHelper.BACKGROUND ? "&aenabled" : "&cdisabled") + "&7.");
            return;
        }

        syntaxError();
    }
}
