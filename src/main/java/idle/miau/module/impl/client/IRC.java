package idle.miau.module.impl.client;

import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.utility.irc.MiauIRC;

public final class IRC extends Module {
    private final ButtonSetting hidePresence;

    public IRC() {
        super("IRC", category.other);
        setDescription("Connects you to the shared client chat.");
        this.registerSetting(hidePresence = new ButtonSetting("Hide my presence", false));
    }

    public boolean isPresenceHidden() {
        return this.isEnabled() && hidePresence != null && hidePresence.isToggled();
    }

    @Override
    public void onEnable() {
        MiauIRC irc = MiauIRC.getInstance();
        irc.setChatEnabled(true);
        irc.start();
    }

    @Override
    public void onDisable() {
        MiauIRC irc = MiauIRC.getInstance();
        irc.setChatEnabled(false);
        irc.shutdown();
    }
}
