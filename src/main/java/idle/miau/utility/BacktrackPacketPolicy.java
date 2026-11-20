package idle.miau.utility;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S40PacketDisconnect;

public final class BacktrackPacketPolicy {
    private static final String[] IMMEDIATE_SOUNDS = {
            "game.player.hurt",
            "game.player.die"
    };
    private static final Class<?>[] SESSION_BOUNDARY_PACKETS = {
            S01PacketJoinGame.class,
            S07PacketRespawn.class,
            S40PacketDisconnect.class
    };

    private BacktrackPacketPolicy() {}

    public static boolean isImmediateSound(String soundName) {
        if (soundName == null) {
            return false;
        }
        for (String allowed : IMMEDIATE_SOUNDS) {
            if (soundName.contains(allowed)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSessionBoundaryPacket(Packet<?> packet) {
        return packet != null && isSessionBoundaryPacket(packet.getClass());
    }

    static boolean isSessionBoundaryPacket(Class<?> packetClass) {
        return matches(packetClass, SESSION_BOUNDARY_PACKETS);
    }

    public static boolean isTimelineResetPacket(Packet<?> packet) {
        return packet instanceof S08PacketPlayerPosLook;
    }

    private static boolean matches(Class<?> packetClass, Class<?>[] allowedClasses) {
        if (packetClass == null) {
            return false;
        }
        for (Class<?> allowedClass : allowedClasses) {
            if (allowedClass.isAssignableFrom(packetClass)) {
                return true;
            }
        }
        return false;
    }
}
