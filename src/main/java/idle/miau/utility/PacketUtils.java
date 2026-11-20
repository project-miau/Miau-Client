package idle.miau.utility;

import idle.miau.Miau;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static net.minecraft.util.EnumFacing.DOWN;

public class PacketUtils implements IMinecraftInstance {
    private static final Set<Packet<?>> skipSendEvent = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );
    private static final Set<Packet<?>> skipReceiveEvent = Collections.newSetFromMap(
            Collections.synchronizedMap(new IdentityHashMap<Packet<?>, Boolean>())
    );

    public static boolean consumeSendEventSkip(Packet<?> packet) {
        return skipSendEvent.remove(packet);
    }

    public static boolean consumeReceiveEventSkip(Packet<?> packet) {
        return skipReceiveEvent.remove(packet);
    }

    public static void clearEventSkips() {
        skipSendEvent.clear();
        skipReceiveEvent.clear();
    }

    public static void sendPacket(Packet<?> packet) {
        if (packet != null && mc.getNetHandler() != null) {
            mc.getNetHandler().getNetworkManager().sendPacket(packet);
        }
    }

    public static void sendPacketNoEvent(Packet packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S") || Miau.mc == null || Miau.mc.thePlayer == null) {
            return;
        }
        skipSendEvent.add(packet);
        try {
            Miau.mc.thePlayer.sendQueue.addToSendQueue(packet);
        }
        catch (RuntimeException exception) {
            skipSendEvent.remove(packet);
            throw exception;
        }
    }

    public static void receivePacketNoEvent(Packet packet) {
        if (packet == null || Miau.mc == null || Miau.mc.getNetHandler() == null) {
            return;
        }
        try {
            packet.processPacket(Miau.mc.getNetHandler());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void scheduleReceivePacketNoEvent(final Packet<?> packet) {
        if (packet == null || Miau.mc == null) {
            return;
        }

        skipReceiveEvent.add(packet);
        Miau.mc.addScheduledTask(() -> {
            try {
                receivePacketNoEvent(packet);
            } finally {
                skipReceiveEvent.remove(packet);
            }
        });
    }

    public static void sendReleasePacket() {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, DOWN));
    }
}
