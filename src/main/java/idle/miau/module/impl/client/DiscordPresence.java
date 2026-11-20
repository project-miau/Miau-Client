package idle.miau.module.impl.client;

import idle.miau.module.Module;

public final class DiscordPresence extends Module {
    public DiscordPresence() {
        super("DiscordPresence", category.other);
        setDescription("Shows your client activity in Discord.");
    }

    @Override
    public void onEnable() {
        idle.miau.utility.discord.DiscordPresence.getInstance().start(mc);
    }

    @Override
    public void onDisable() {
        idle.miau.utility.discord.DiscordPresence.getInstance().shutdown();
    }
}
