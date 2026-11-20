package idle.miau.module.impl.combat.velocity;

import net.minecraft.client.Minecraft;
import idle.miau.event.AntiCheatFlagEvent;
import idle.miau.event.AttackEvent;
import idle.miau.event.HitSlowDownEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.PrePlayerInputEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.entity.living.LivingEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;

public abstract class VelocityMode {
    protected final VelocityModule owner;
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;
    private boolean suppressed;

    protected VelocityMode(String name, VelocityModule owner) {
        this.name = name;
        this.owner = owner;
    }

    public final String getName() {
        return name;
    }

    protected final void register(Setting setting) {
        owner.registerSetting(setting);
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    protected final boolean isSuppressed() {
        return suppressed;
    }

    public String getInfo() {
        return name;
    }

    public boolean isHorizontalZero() {
        return false;
    }

    public boolean isAutoVlEnabled() {
        return false;
    }

    public long getAutoVlDelay() {
        return 0L;
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

    public void onReceivePacketHigh(ReceivePacketEvent event) {
    }

    public void onReceivePacket(ReceivePacketEvent event) {
    }

    public void onAttack(AttackEvent event) {
    }

    public void onHitSlowDown(HitSlowDownEvent event) {
    }

    public void onAntiCheatFlag(AntiCheatFlagEvent event) {
    }

    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
    }

    public void onPreUpdate(PreUpdateEvent event) {
    }

    public void onPrePlayerInput(PrePlayerInputEvent event) {
    }

    public void onPostMotion(PostMotionEvent event) {
    }

    public void onSendPacket(SendPacketEvent event) {
    }

    public void onWorldUnload(WorldEvent.Unload event) {
    }
}
