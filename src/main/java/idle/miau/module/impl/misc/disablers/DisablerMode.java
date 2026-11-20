package idle.miau.module.impl.misc.disablers;

import net.minecraft.client.Minecraft;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PushOutOfBlockEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.mcp.event.world.WorldEvent;

public abstract class DisablerMode {
    protected final DisablerModule owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;

    protected DisablerMode(String name, DisablerModule owner) {
        this.name = name;
        this.owner = owner;
    }

    public final String getName() {
        return name;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onPreUpdate(PreUpdateEvent event) {
    }

    public void onPreMotion(PreMotionEvent event) {
    }

    public void onPushOutOfBlock(PushOutOfBlockEvent event) {
    }

    public void onReceivePacket(ReceivePacketEvent event) {
    }

    public void onStrafe(StrafeEvent event) {
    }

    public void onSendPacket(SendPacketEvent event) {
    }

    public void onWorldUnload(WorldEvent.Unload event) {
    }

    public void onGuiUpdate() {
    }
}
