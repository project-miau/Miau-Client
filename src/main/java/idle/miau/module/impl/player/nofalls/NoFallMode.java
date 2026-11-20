package idle.miau.module.impl.player.nofalls;

import net.minecraft.client.Minecraft;
import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.Setting;

public abstract class NoFallMode {
    protected final NoFallModule owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;

    protected NoFallMode(String name, NoFallModule owner) {
        this.name = name;
        this.owner = owner;
    }

    public final String getName() {
        return name;
    }

    protected final void register(Setting setting) {
        owner.registerSetting(setting);
    }

    public void updateVisibility() {
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onClientRotation(ClientRotationEvent event) {
    }

    public void onPreUpdate(PreUpdateEvent event) {
    }

    public void onPostUpdate(PostUpdateEvent event) {
    }

    public void onPreMotion(PreMotionEvent event) {
    }

    public void onWorldUnload(WorldEvent.Unload event) {
    }
}
