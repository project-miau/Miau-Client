package idle.miau.utility;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import idle.miau.event.NoEventPacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;

public final class PlayerStateTracker implements IMinecraftInstance {
    public static boolean attacking;
    public static boolean digging;
    public static boolean placing;
    public static boolean swapping;
    public static boolean swinging;

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        handlePacket(event.getPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onNoEventPacket(NoEventPacketEvent event) {
        handlePacket(event.getPacket());
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (event.entity == mc.thePlayer) {
            reset();
        }
    }

    private static void handlePacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            attacking = true;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            digging = true;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            placing = true;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            swapping = true;
        }
        if (packet instanceof C0APacketAnimation) {
            swinging = true;
        }
        if (packet instanceof C03PacketPlayer) {
            reset();
        }
    }

    public static void reset() {
        attacking = false;
        digging = false;
        placing = false;
        swapping = false;
        swinging = false;
    }
}
