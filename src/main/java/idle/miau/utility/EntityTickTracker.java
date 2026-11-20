package idle.miau.utility;

import idle.miau.event.AttackEvent;
import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class EntityTickTracker implements IMinecraftInstance {
    public static final EntityTickTracker INSTANCE = new EntityTickTracker();

    public static int ticksSinceVelocity = 1000;
    public static int ticksSinceTeleport = 1000;
    public static int ticksSinceAttack = 1000;
    public static int airTicks;
    public static int groundTicks;

    public static boolean badPacketsBlock;
    public static boolean badPacketsInventory;

    private EntityTickTracker() {
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof S12PacketEntityVelocity
                && mc.thePlayer != null
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
            ticksSinceVelocity = 0;
        } else if (packet instanceof S08PacketPlayerPosLook) {
            ticksSinceTeleport = 0;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK) {
            ticksSinceAttack = 0;
        } else if (packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C07PacketPlayerDigging) {
            badPacketsBlock = true;
        } else if (packet instanceof C0EPacketClickWindow
                || packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus()
                == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            badPacketsInventory = true;
        } else if (packet instanceof C03PacketPlayer) {
            badPacketsBlock = false;
            badPacketsInventory = false;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        ticksSinceAttack = 0;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreAttack(PreAttackEvent event) {
        ticksSinceAttack = 0;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.thePlayer == null) {
            return;
        }
        ticksSinceVelocity++;
        ticksSinceTeleport++;
        ticksSinceAttack++;
        if (mc.thePlayer.onGround) {
            groundTicks++;
            airTicks = 0;
        } else {
            airTicks++;
            groundTicks = 0;
        }
    }

    @Subscribe
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.entity == mc.thePlayer) {
            reset();
        }
    }

    public static boolean isBadPackets() {
        return badPacketsBlock || badPacketsInventory;
    }

    public static void reset() {
        ticksSinceVelocity = 1000;
        ticksSinceTeleport = 1000;
        ticksSinceAttack = 1000;
        airTicks = 0;
        groundTicks = 0;
        badPacketsBlock = false;
        badPacketsInventory = false;
    }
}
