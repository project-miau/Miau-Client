package idle.miau.command.impl;

import idle.miau.Miau;
import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.utility.PlayerRelationsManager;
import idle.miau.utility.Utils;

import java.util.List;

public class Friend extends Command {
    public Friend() {
        super("friend", "Add, remove, list, or clear friends.", CommandCategory.GENERAL, new String[] {"friend <name>", "friend clear"}, "f");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            List<PlayerRelationsManager.PlayerEntry> entries = Miau.playerRelationsManager.getEntries(PlayerRelationsManager.RelationType.FRIEND);
            replyWithHeader("&b" + entries.size() + " &7friend" + (entries.size() == 1 ? "" : "s") + ".");
            for (PlayerRelationsManager.PlayerEntry entry : entries) {
                replyWithHeader(" &b" + entry.getDisplayName());
            }
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String name = input.getArgument(0);
        if ("clear".equalsIgnoreCase(name)) {
            int cleared = Miau.playerRelationsManager.getCount(PlayerRelationsManager.RelationType.FRIEND);
            Miau.playerRelationsManager.clearFriends();
            replyWithHeader("&b" + cleared + " &7friend" + (cleared == 1 ? "" : "s") + " cleared.");
            return;
        }

        if (Utils.addFriend(name)) {
            replyWithHeader("&7Added &afriend&7: &b" + name);
        }
        else {
            Utils.removeFriend(name);
            replyWithHeader("&7Removed &afriend&7: &b" + name);
        }
    }
}
