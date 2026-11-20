package idle.miau.management;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S46PacketSetCompressionLevel;

public final class LagPacketPolicy {
    private LagPacketPolicy() {
    }

    public static boolean followsMovementTimeline(Packet<?> packet) {
        return packet instanceof C02PacketUseEntity
                || packet instanceof C03PacketPlayer
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0APacketAnimation
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C0CPacketInput
                || packet instanceof C0FPacketConfirmTransaction
                || packet instanceof C13PacketPlayerAbilities;
    }

    public static boolean isConnectionCritical(Packet<?> packet) {
        return isOutboundConnectionCritical(packet)
                || isInboundConnectionCritical(packet);
    }

    public static boolean isOutboundConnectionCritical(Packet<?> packet) {
        return packet instanceof C00PacketKeepAlive;
    }

    public static boolean isInboundConnectionCritical(Packet<?> packet) {
        return packet instanceof S00PacketKeepAlive
                || packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S40PacketDisconnect
                || packet instanceof S46PacketSetCompressionLevel;
    }

    public static boolean requiresImmediateInboundHandling(Packet<?> packet) {
        return packet instanceof S00PacketKeepAlive
                || packet instanceof S40PacketDisconnect
                || packet instanceof S46PacketSetCompressionLevel;
    }

    public static boolean bypassesPlayPacketHooks(Packet<?> packet) {
        return isInboundConnectionCritical(packet)
                && !(packet instanceof S08PacketPlayerPosLook);
    }
}
