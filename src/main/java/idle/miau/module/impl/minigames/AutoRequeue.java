package idle.miau.module.impl.minigames;

import idle.miau.event.PreUpdateEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.util.IChatComponent;
import idle.miau.mcp.event.client.ClientChatReceivedEvent;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Subscribe;

public class AutoRequeue extends Module {
    private SliderSetting delay;

    private String receivedMessage = "";
    private long receiveTime = 0;

    public AutoRequeue() {
        super("AutoRequeue", category.minigames);
        setDescription("Automatically queues another game after a match ends.");
        this.registerSetting(new DescriptionSetting("Automatically requeues games."));
        this.registerSetting(delay = new SliderSetting("Delay", " second", 0.5, 0, 5, 0.1));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        receivedMessage = "";
    }

    @Subscribe
    public void onPreUpdate(PreUpdateEvent e) {
        if (!receivedMessage.isEmpty() && System.currentTimeMillis() - receiveTime >= delay.getInput() * 1000) {
            mc.thePlayer.sendChatMessage(receivedMessage);
            receivedMessage = "";
        }
    }

    @Subscribe
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || !Utils.nullCheck()) {
            return;
        }
        String stripped = Utils.stripColor(e.message.getUnformattedText());
        if (stripped.isEmpty() || !stripped.contains("play again")) {
            return;
        }
        if (e.message != null) {
            for (IChatComponent component : e.message.getSiblings()) {
                if (component != null && component.getFormattedText().contains("Click here")) {
                    if (component.getChatStyle() != null && component.getChatStyle().getChatClickEvent() != null && component.getChatStyle().getChatClickEvent().getValue().startsWith("/")) {
                        this.receivedMessage = component.getChatStyle().getChatClickEvent().getValue();
                        this.receiveTime = System.currentTimeMillis();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            receivedMessage = "";
        }
    }
}
