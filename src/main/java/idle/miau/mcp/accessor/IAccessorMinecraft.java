package idle.miau.mcp.accessor;

import net.minecraft.network.NetworkManager;
import net.minecraft.util.Timer;

public interface IAccessorMinecraft {
    Timer getTimer();

    NetworkManager getMyNetworkManager();

    int getRightClickDelayTimer();

    void setRightClickDelayTimer(int delay);

    void setLeftClickCounter(int delay);

    void callRightClickMouse();

    void callClickMouse();

    void invokeDispatchKeypresses();
}
