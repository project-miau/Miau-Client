package idle.miau.helper;

import idle.miau.Miau;
import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;

public class DebugHelper implements IMinecraftInstance {
    public static boolean BACKGROUND;

    @Subscribe
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Miau.DEBUG || ev.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        if (mc.currentScreen == null) {
            RenderUtils.renderBPS(true, true);
        }
    }
}
