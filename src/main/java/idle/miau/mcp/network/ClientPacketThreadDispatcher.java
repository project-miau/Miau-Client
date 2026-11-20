package idle.miau.mcp.network;

import idle.miau.management.LagPacketPolicy;
import net.minecraft.network.Packet;
import net.minecraft.util.IThreadListener;

public final class ClientPacketThreadDispatcher {
    private ClientPacketThreadDispatcher() {
    }

    public static boolean enqueueIfNeeded(
            IThreadListener threadListener,
            Packet<?> packet,
            Runnable packetTask) {
        if (threadListener == null || packet == null || packetTask == null
                || LagPacketPolicy.requiresImmediateInboundHandling(packet)
                || threadListener.isCallingFromMinecraftThread()) {
            return false;
        }
        threadListener.addScheduledTask(packetTask);
        return true;
    }
}
