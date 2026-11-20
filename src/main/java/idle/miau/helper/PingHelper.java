package idle.miau.helper;

import idle.miau.utility.Utils;
import idle.miau.mcp.event.client.ClientChatReceivedEvent;
import idle.miau.mcp.event.Subscribe;

public class PingHelper {
    private static final long TIMEOUT_MS = 10000L;
    private static boolean waiting;
    private static long sendTime;

    @Subscribe
    public void onChatMessageRecieved(ClientChatReceivedEvent event) {
        if (!waiting || !Utils.nullCheck()) return;
        if (System.currentTimeMillis() - sendTime > TIMEOUT_MS) {
            reset(true);
            return;
        }
        if (Utils.stripColor(event.message.getUnformattedText()).startsWith("Unknown")) {
            event.setCanceled(true);
            int ping = Math.max(0, (int) (System.currentTimeMillis() - sendTime) - 20);
            Utils.sendMessage("&7Your ping: &b" + ping + "&7ms.");
            reset(true);
        }
    }

    public static void checkPing(boolean ignored) {
        if (!Utils.nullCheck()) return;
        if (waiting && System.currentTimeMillis() - sendTime <= TIMEOUT_MS) {
            Utils.sendMessage("&7Please wait before checking again.");
            return;
        }
        reset(true);
        Utils.sendMessage("&7Checking ping...");
        waiting = true;
        sendTime = System.currentTimeMillis();
        Utils.mc.thePlayer.sendChatMessage("/...");
    }

    public static void reset(boolean ignored) {
        waiting = false;
        sendTime = 0L;
    }
}
