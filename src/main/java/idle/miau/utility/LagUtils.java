package idle.miau.utility;

import idle.miau.management.LagPacketPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class LagUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long DEFAULT_TIMER_DELAY = 100L;
    public static final long BLINK_DELAY = 9999999L;

    public enum PacketType {
        REGULAR(new Class[]{C0FPacketConfirmTransaction.class, S1CPacketEntityMetadata.class}),
        VELOCITY(new Class[]{S12PacketEntityVelocity.class, S27PacketExplosion.class}),
        TELEPORTS(new Class[]{S08PacketPlayerPosLook.class, S39PacketPlayerAbilities.class, S09PacketHeldItemChange.class}),
        PLAYERS(new Class[]{S13PacketDestroyEntities.class, S14PacketEntity.class, S14PacketEntity.S16PacketEntityLook.class, S14PacketEntity.S15PacketEntityRelMove.class, S14PacketEntity.S17PacketEntityLookMove.class, S18PacketEntityTeleport.class, S20PacketEntityProperties.class, S19PacketEntityHeadLook.class}),
        ACTION(new Class[]{C02PacketUseEntity.class, C0DPacketCloseWindow.class, C0EPacketClickWindow.class, C0CPacketInput.class, C0BPacketEntityAction.class, C08PacketPlayerBlockPlacement.class, C07PacketPlayerDigging.class, C09PacketHeldItemChange.class, C13PacketPlayerAbilities.class, C15PacketClientSettings.class, C16PacketClientStatus.class, C17PacketCustomPayload.class, C18PacketSpectate.class, C19PacketResourcePackStatus.class, C0APacketAnimation.class}),
        MOVEMENT(new Class[]{C03PacketPlayer.class, C03PacketPlayer.C04PacketPlayerPosition.class, C03PacketPlayer.C05PacketPlayerLook.class, C03PacketPlayer.C06PacketPlayerPosLook.class});

        private final Class<?>[] packetClasses;
        private boolean enabled;

        PacketType(Class<?>[] packetClasses) {
            this.packetClasses = packetClasses;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean containsPacket(Class<?> packetClass) {
            for (Class<?> c : packetClasses) {
                if (c.isAssignableFrom(packetClass)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final ConcurrentLinkedQueue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private static long lastResetTime = System.currentTimeMillis();

    private static boolean enabled;
    private static long delayAmount;
    private static boolean post;

    public static void resetTimer() {
        lastResetTime = System.currentTimeMillis();
    }

    public static boolean hasTimeElapsed(long delay) {
        return System.currentTimeMillis() - lastResetTime >= delay;
    }

    public static boolean processOutgoingPacket(Packet<?> packet) {
        if (!LagPacketPolicy.isOutboundConnectionCritical(packet)
                && enabled && shouldHandlePacket(packet)) {
            packets.add(new TimedPacket(packet));
            return true;
        }
        return false;
    }

    public static boolean processIncomingPacket(Packet<?> packet) {
        if (!LagPacketPolicy.isInboundConnectionCritical(packet)
                && enabled && shouldHandlePacket(packet)) {
            packets.add(new TimedPacket(packet));
            return true;
        }
        return false;
    }

    private static boolean shouldHandlePacket(Packet<?> packet) {
        return Arrays.stream(PacketType.values()).anyMatch(type -> type.isEnabled() && type.containsPacket(packet.getClass()));
    }

    public static void dispatch() {
        if (!packets.isEmpty()) {
            boolean wasEnabled = enabled;
            enabled = false;
            packets.forEach(timedPacket -> {
                PacketUtils.sendPacketNoEvent(timedPacket.getPacket());
            });
            enabled = wasEnabled;
            packets.clear();
        }
    }

    public static void disable() {
        enabled = false;
        lastResetTime = System.currentTimeMillis() - BLINK_DELAY;
    }

    public static void onWorldChange() {
        disable();
        dispatch();
    }

    public static void updatePackets(boolean isPreUpdate) {
        if (isPreUpdate && !post) {
            sendPackets();
        } else if (!isPreUpdate && post) {
            sendPackets();
        }
    }

    private static void sendPackets() {
        enabled = !hasTimeElapsed(DEFAULT_TIMER_DELAY) && !(mc.currentScreen instanceof GuiDownloadTerrain);
        if (!enabled) {
            dispatch();
            return;
        }

        enabled = false;
        releaseTimedOutPackets();
        enabled = true;
    }

    private static void releaseTimedOutPackets() {
        long now = System.currentTimeMillis();
        TimedPacket packet;
        while ((packet = packets.peek()) != null) {
            if (packet.getMs() + delayAmount > now) break;
            PacketUtils.sendPacketNoEvent(packet.getPacket());
            packets.poll();
        }
    }

    public static void spoof(int amount, boolean regular, boolean velocity, boolean teleports, boolean players, boolean action) {
        spoof(amount, regular, velocity, teleports, players, action, false);
    }

    public static void spoof(int amount, boolean regular, boolean velocity, boolean teleports, boolean players, boolean action, boolean movement) {
        spoof(amount, regular, velocity, teleports, players, action, movement, true);
    }

    public static void spoof(int amount, boolean regular, boolean velocity, boolean teleports, boolean players, boolean action, boolean movement, boolean post) {
        resetTimer();
        PacketType.REGULAR.setEnabled(regular);
        PacketType.VELOCITY.setEnabled(velocity);
        PacketType.TELEPORTS.setEnabled(teleports);
        PacketType.PLAYERS.setEnabled(players);
        PacketType.ACTION.setEnabled(action);
        PacketType.MOVEMENT.setEnabled(movement);
        LagUtils.post = post;
        LagUtils.delayAmount = amount;
    }
}
