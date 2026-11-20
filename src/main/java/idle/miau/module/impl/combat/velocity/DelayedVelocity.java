package idle.miau.module.impl.combat.velocity;

import java.util.concurrent.ConcurrentLinkedDeque;
import idle.miau.management.LagPacketPolicy;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Mouse;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.entity.living.LivingEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatTargeting;

public final class DelayedVelocity extends VelocityMode {
    public final SliderSetting minimumDelay;
    public final SliderSetting maximumDelay;
    public final SliderSetting delayDistanceToTarget;
    public final SliderSetting delayChance;
    public final ButtonSetting delayInAir;
    public final ButtonSetting delayLookingAtPlayer;
    public final ButtonSetting delayRequireLeftMouse;
    public final ButtonSetting delayRestrictHeldItem;
    public final ItemListSetting delayWhitelistedItems;

    private final ConcurrentLinkedDeque<Packet<?>> delayedPackets = new ConcurrentLinkedDeque<Packet<?>>();
    private long lagStartTime = -1L;
    private long targetDelay;

    public DelayedVelocity(VelocityModule owner) {
        super("Delayed", owner);
        register(minimumDelay = new SliderSetting("Minimum delay", "ms", 100.0D, 0.0D, 1000.0D, 10.0D));
        register(maximumDelay = new SliderSetting("Maximum delay", "ms", 200.0D, 50.0D, 1000.0D, 10.0D));
        register(delayDistanceToTarget = new SliderSetting("Delay distance to target", 6.0D, 3.0D, 12.0D, 0.1D));
        register(delayChance = new SliderSetting("Delay chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        register(delayInAir = new ButtonSetting("Delay in air", true));
        register(delayLookingAtPlayer = new ButtonSetting("Delay looking at player", false));
        register(delayRequireLeftMouse = new ButtonSetting("Delay require left mouse", false));
        register(delayRestrictHeldItem = new ButtonSetting("Delay restrict held item", false));
        register(delayWhitelistedItems = new ItemListSetting("Delay whitelisted items",
                "standard-delay-whitelisted-items", "oldgrim-delay-whitelisted-items"));
    }

    @Override
    public void updateVisibility() {
        boolean visible = owner.isMode("Delayed");
        minimumDelay.setVisible(visible, owner);
        maximumDelay.setVisible(visible, owner);
        delayDistanceToTarget.setVisible(visible, owner);
        delayChance.setVisible(visible, owner);
        delayInAir.setVisible(visible, owner);
        delayLookingAtPlayer.setVisible(visible, owner);
        delayRequireLeftMouse.setVisible(visible, owner);
        delayRestrictHeldItem.setVisible(visible, owner);
        delayWhitelistedItems.setVisible(visible && delayRestrictHeldItem.isToggled(), owner);
    }

    @Override
    public void onEnable() {
        delayedPackets.clear();
        lagStartTime = -1L;
    }

    @Override
    public void onDisable() {
        flushDelayedPackets();
    }

    @Override
    public void setSuppressed(boolean suppressed) {
        super.setSuppressed(suppressed);
        if (suppressed) {
            flushDelayedPackets();
        }
    }

    @Override
    public void onReceivePacketHigh(ReceivePacketEvent event) {
        if (isSuppressed() || event.isCanceled()) {
            return;
        }
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            flushDelayedPackets();
            return;
        }
        if (LagPacketPolicy.isInboundConnectionCritical(event.getPacket())) {
            return;
        }
        if (lagStartTime != -1L) {
            event.setCanceled(true);
            delayedPackets.addLast(event.getPacket());
            return;
        }
        if (!(event.getPacket() instanceof S12PacketEntityVelocity) || delayConditionsFailed()) {
            return;
        }
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) event.getPacket();
        if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }
        if (delayChance.getInput() < 100.0D && Math.random() * 100.0D >= delayChance.getInput()) {
            return;
        }

        event.setCanceled(true);
        delayedPackets.addLast(event.getPacket());
        lagStartTime = System.currentTimeMillis();
        long minimum = (long) minimumDelay.getInput();
        long maximum = (long) maximumDelay.getInput();
        if (minimum > maximum) {
            minimum = maximum;
        }
        targetDelay = minimum + (long) (Math.random() * (maximum - minimum + 1L));
    }

    @Override
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (lagStartTime != -1L && (delayConditionsFailed()
                || System.currentTimeMillis() - lagStartTime >= targetDelay)) {
            flushDelayedPackets();
        }
    }

    @Override
    public void onWorldUnload(WorldEvent.Unload event) {
        flushDelayedPackets();
    }

    private boolean delayConditionsFailed() {
        if (isSuppressed() || mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
            return true;
        }
        double distanceSq = delayDistanceToTarget.getInput() * delayDistanceToTarget.getInput();
        if (CombatTargeting.findTarget(distanceSq, true) == null) {
            return true;
        }
        if (delayInAir.isToggled() && mc.thePlayer.onGround) {
            return true;
        }
        if (delayLookingAtPlayer.isToggled()
                && CombatTargeting.getMouseOverTarget(distanceSq, true) == null) {
            return true;
        }
        if (delayRequireLeftMouse.isToggled() && !Mouse.isButtonDown(0)) {
            return true;
        }
        if (delayRestrictHeldItem.isToggled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            return held == null || !delayWhitelistedItems.matches(held);
        }
        return false;
    }

    private void flushDelayedPackets() {
        lagStartTime = -1L;
        if (mc.thePlayer != null && mc.getNetHandler() != null) {
            Packet<?> packet;
            while ((packet = delayedPackets.pollFirst()) != null) {
                owner.receiveDelayedPacket(packet);
            }
        }
        delayedPackets.clear();
    }
}
