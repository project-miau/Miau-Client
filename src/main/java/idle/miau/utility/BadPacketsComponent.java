package idle.miau.utility;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import idle.miau.event.NoEventPacketEvent;
import idle.miau.event.PostUpdateEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;

public final class BadPacketsComponent {
    private static boolean slot;
    private static boolean attack;
    private static boolean swing;
    private static boolean block;
    private static boolean inventory;
    private static boolean savedSlot;
    private static boolean savedAttack;
    private static boolean savedSwing;
    private static boolean savedBlock;
    private static boolean savedInventory;

    public static boolean bad(
            boolean slotCheck,
            boolean attackCheck,
            boolean swingCheck,
            boolean blockCheck,
            boolean inventoryCheck) {
        return savedSlot && slotCheck
                || savedAttack && attackCheck
                || savedSwing && swingCheck
                || savedBlock && blockCheck
                || savedInventory && inventoryCheck;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        handlePacket(event.getPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onNoEventPacket(NoEventPacketEvent event) {
        handlePacket(event.getPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent event) {
        savedSlot = slot;
        savedAttack = attack;
        savedSwing = swing;
        savedBlock = block;
        savedInventory = inventory;
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPostUpdate(PostUpdateEvent event) {
        reset();
    }

    private static void handlePacket(Packet<?> packet) {
        if (packet instanceof C09PacketHeldItemChange) {
            slot = true;
        } else if (packet instanceof C0APacketAnimation) {
            swing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            attack = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C07PacketPlayerDigging) {
            block = true;
        } else if (packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow
                || packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus()
                == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            inventory = true;
        }
    }

    public static void reset() {
        slot = false;
        attack = false;
        swing = false;
        block = false;
        inventory = false;
        savedSlot = false;
        savedAttack = false;
        savedSwing = false;
        savedBlock = false;
        savedInventory = false;
    }
}
