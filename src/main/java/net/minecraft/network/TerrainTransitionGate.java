package net.minecraft.network;

import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;

final class TerrainTransitionGate {
    private volatile boolean active;

    boolean beginIfBoundary(Packet<?> packet) {
        if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
            active = true;
            return true;
        }
        return false;
    }

    boolean shouldBypassHooks() {
        return active;
    }

    void complete() {
        active = false;
    }

    void reset() {
        active = false;
    }
}
