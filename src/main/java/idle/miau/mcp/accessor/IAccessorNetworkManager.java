package idle.miau.mcp.accessor;

import net.minecraft.network.INetHandler;

public interface IAccessorNetworkManager {
    INetHandler getPacketListener();
}
