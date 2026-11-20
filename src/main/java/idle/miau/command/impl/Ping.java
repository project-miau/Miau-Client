package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.helper.PingHelper;

public class Ping extends Command {
    public Ping() {
        super("ping", "Estimate your server round-trip latency.", CommandCategory.UTILITY, new String[] {"ping"});
    }

    @Override
    public void execute(CommandInput input) {
        PingHelper.checkPing(true);
    }
}
