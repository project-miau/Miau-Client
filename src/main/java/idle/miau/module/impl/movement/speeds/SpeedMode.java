package idle.miau.module.impl.movement.speeds;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PreMotionEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.StrafeEvent;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import net.minecraft.client.Minecraft;

public abstract class SpeedMode {
    protected final SpeedModule owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;

    protected SpeedMode(String name, SpeedModule owner) {
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

    public void onButtonToggled(ButtonSetting setting) {
        updateVisibility();
    }

    public void onSettingChanged(Setting setting) {
        updateVisibility();
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onPreUpdate(PreUpdateEvent event) {
    }

    public void onClientRotation(ClientRotationEvent event) {
    }

    public void onPrePlayerInput(PrePlayerInputEvent event) {
    }

    public void onPreMotion(PreMotionEvent event) {
    }

    public void onStrafe(StrafeEvent event) {
    }

    public void onReceivePacket(ReceivePacketEvent event) {
    }
}
