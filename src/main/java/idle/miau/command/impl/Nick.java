package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.impl.other.NameHider;

public class Nick extends Command {
    public Nick() {
        super("nick", "Set the nickname used by minigame chat matching.",
                CommandCategory.UTILITY, new String[] {"nick <name/reset>"});
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            if (mc != null) {
                syntaxError();
            }
            return;
        }

        String requestedName = input.joinArguments(0);
        if ("reset".equalsIgnoreCase(requestedName)) {
            NameHider.resetFakeName();
            replySafely("&aNick reset to Name Hider's default:");
            replySafely("\"" + NameHider.fakeName + "\"");
            return;
        }

        NameHider.setFakeName(requestedName);
        replySafely("&aNick has been set to:");
        replySafely("\"" + NameHider.fakeName + "\"");
    }

    private void replySafely(String message) {
        if (mc != null) {
            reply(message);
        }
    }
}
