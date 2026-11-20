package idle.miau.utility;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.management.LagPacketPolicy;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C18PacketSpectate;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SelectivePacketBlink {
    private static final long REFRESH_TIMEOUT = 100L;

    private final Deque<QueuedPacket> packets = new ArrayDeque<>();
    private boolean active;
    private boolean captureOutbound;
    private long expiresAt;
    private long refreshedAt;

    public synchronized void refresh(long duration, boolean outbound) {
        long now = System.currentTimeMillis();
        if (active && (now > expiresAt || now - refreshedAt > REFRESH_TIMEOUT)) {
            flushLocked();
        }
        active = true;
        captureOutbound = outbound;
        expiresAt = now + duration;
        refreshedAt = now;
    }

    public synchronized void capture(SendPacketEvent event) {
        maintainLocked();
        Packet<?> packet = event.getPacket();
        if (event.isCanceled() || !active
                || LagPacketPolicy.isOutboundConnectionCritical(packet)
                || !matchesTransactionGroup(packet)
                && !(captureOutbound && matchesOutboundGroup(packet))) {
            return;
        }
        packets.addLast(new QueuedPacket(packet, true));
        event.setCanceled(true);
    }

    public synchronized void capture(ReceivePacketEvent event) {
        maintainLocked();
        if (event.isCanceled() || !active
                || !(event.getPacket() instanceof S1CPacketEntityMetadata)) {
            return;
        }
        packets.addLast(new QueuedPacket(event.getPacket(), false));
        event.setCanceled(true);
    }

    public synchronized void flush() {
        flushLocked();
    }

    public synchronized void clear() {
        packets.clear();
        active = false;
        captureOutbound = false;
        expiresAt = 0L;
        refreshedAt = 0L;
    }

    private void maintainLocked() {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > expiresAt || now - refreshedAt > REFRESH_TIMEOUT) {
            flushLocked();
        }
    }

    private void flushLocked() {
        active = false;
        captureOutbound = false;
        while (!packets.isEmpty()) {
            QueuedPacket queued = packets.removeFirst();
            if (queued.outbound) {
                PacketUtils.sendPacketNoEvent(queued.packet);
            } else {
                PacketUtils.scheduleReceivePacketNoEvent(queued.packet);
            }
        }
        expiresAt = 0L;
        refreshedAt = 0L;
    }

    private static boolean matchesTransactionGroup(Packet<?> packet) {
        return packet instanceof C0FPacketConfirmTransaction;
    }

    private static boolean matchesOutboundGroup(Packet<?> packet) {
        return packet instanceof C02PacketUseEntity
                || packet instanceof C03PacketPlayer
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C0CPacketInput
                || packet instanceof C0DPacketCloseWindow
                || packet instanceof C0EPacketClickWindow
                || packet instanceof C13PacketPlayerAbilities
                || packet instanceof C15PacketClientSettings
                || packet instanceof C16PacketClientStatus
                || packet instanceof C17PacketCustomPayload
                || packet instanceof C18PacketSpectate
                || packet instanceof C19PacketResourcePackStatus;
    }

    private static final class QueuedPacket {
        private final Packet<?> packet;
        private final boolean outbound;

        private QueuedPacket(Packet<?> packet, boolean outbound) {
            this.packet = packet;
            this.outbound = outbound;
        }
    }
}
