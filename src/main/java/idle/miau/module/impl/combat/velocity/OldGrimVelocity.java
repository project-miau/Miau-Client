package idle.miau.module.impl.combat.velocity;

import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.entity.living.LivingEvent;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;

public final class OldGrimVelocity extends VelocityMode {
    public final SliderSetting chance;
    public final ButtonSetting autoVL;
    public final SliderSetting autoVLDelay;

    private int grimTCancel;
    private int updates;

    public OldGrimVelocity(VelocityModule owner) {
        super("OldGrim", owner);
        register(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        register(autoVL = new ButtonSetting("Auto VL", false));
        register(autoVLDelay = new SliderSetting("Re-enable delay", "ms", 2000.0D, 500.0D, 10000.0D, 100.0D));
    }

    @Override
    public boolean isAutoVlEnabled() {
        return autoVL.isToggled();
    }

    @Override
    public long getAutoVlDelay() {
        return (long) autoVLDelay.getInput();
    }

    @Override
    public void updateVisibility() {
        boolean active = owner.isMode("OldGrim");
        chance.setVisible(active, owner);
        autoVL.setVisible(active, owner);
        autoVLDelay.setVisible(active && autoVL.isToggled(), owner);
    }

    @Override
    public void onEnable() {
        grimTCancel = 0;
        updates = 0;
    }

    @Override
    public void onDisable() {
        grimTCancel = 0;
        updates = 0;
    }

    @Override
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!Utils.nullCheck() || isSuppressed()) {
            return;
        }
        double value = chance.getInput();
        if (value <= 0.0D || value < 100.0D && Math.random() >= value / 100.0D) {
            return;
        }
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                event.setCanceled(true);
                grimTCancel = 6;
            }
        }
        if (event.getPacket() instanceof S32PacketConfirmTransaction && grimTCancel > 0) {
            event.setCanceled(true);
            grimTCancel--;
        }
    }

    @Override
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!Utils.nullCheck() || isSuppressed() || event.entity != mc.thePlayer || mc.thePlayer.isDead) {
            return;
        }
        updates++;
        if (updates >= 10) {
            updates = 0;
            if (grimTCancel > 0) {
                grimTCancel--;
            }
        }
    }
}
