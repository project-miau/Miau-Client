package idle.miau.module.impl.combat;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.helper.RotationHelper;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.ModuleManager;
import idle.miau.utility.Utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.util.ArrayList;
import java.util.List;

public final class KillAuraLifecycle {
    private static final List<KillAura> pendingDisable = new ArrayList<>();
    static boolean wasRotating;

    @Subscribe(priority = Priority.LOWEST)
    public void onPostMotion(PostMotionEvent event) {
        wasRotating = RotationHelper.get().isActive();
    }

    static void queueDisable(KillAura aura) {
        if (!pendingDisable.contains(aura)) {
            pendingDisable.add(aura);
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        for (KillAura disabled : pendingDisable) {
            disabled.afterDisable();
        }
        pendingDisable.clear();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        KillAuraPackets.track(event.getPacket());
    }

    public static boolean finishSend(Packet<?> packet) {
        KillAuraPackets.trackPost(packet);
        KillAura aura = ModuleManager.killAura;
        return !(packet instanceof C09PacketHeldItemChange
                && aura != null
                && aura.isEnabled()
                && !aura.allowHeldItemPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        KillAuraPackets.trackReceived(event.getPacket());
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (event.entity == Minecraft.getMinecraft().thePlayer) {
            reset();
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onWorldUnload(WorldEvent.Unload event) {
        reset();
    }

    public static void reset() {
        KillAura.clearWorldState();
        wasRotating = false;
        KillAuraPackets.track(new net.minecraft.network.play.client.C03PacketPlayer());
        KillAuraPackets.trackPost(new net.minecraft.network.play.client.C03PacketPlayer());
        KillAuraPackets.resetLastUse();
        KillAuraPackets.serverSlot = KillAuraPackets.previousServerSlot = 0;
        KillAuraPackets.slotPost = KillAuraPackets.previousSlotPost = 0;
        pendingDisable.clear();
    }
}
