package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.utility.Utils;
import idle.miau.utility.system.SystemUtils;

public class Name extends Command {
    public Name() {
        super("name", "Copy your current username.", CommandCategory.GENERAL, new String[] {"name"}, "ign");
    }

    @Override
    public void execute(CommandInput input) {
        if (!Utils.nullCheck()) {
            return;
        }

        SystemUtils.addToClipboard(mc.thePlayer.getName());
        replyWithHeader("&7Copied &b" + mc.thePlayer.getName() + " &7to clipboard");
    }
}
