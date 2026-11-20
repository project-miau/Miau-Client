package idle.miau.module.impl.misc;

import idle.miau.event.PostUpdateEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.mcp.event.Subscribe;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C18PacketSpectate;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public final class Troll extends Module {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final SliderSetting packetsPerTick;
    private boolean sprintState;
    private float spoofedPitch = 120.0F;

    public Troll() {
        super("Troll", category.other);
        registerSetting(packetsPerTick = new SliderSetting(
                "Packets / tick", 3.0D, 1.0D, 10.0D, 1.0D));
    }

    @Subscribe
    public void onPostUpdate(PostUpdateEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        int burst = Math.max(1, (int) packetsPerTick.getInput());
        for (int i = 0; i < burst; i++) {
            sendGrimBurst();
            sendIntaveBurst();
        }
    }

    private void sendGrimBurst() {
        int slot = mc.thePlayer.inventory.currentItem;
        send(new C09PacketHeldItemChange(slot));
        send(new C09PacketHeldItemChange(slot));

        C0BPacketEntityAction.Action action = sprintState
                ? C0BPacketEntityAction.Action.START_SPRINTING
                : C0BPacketEntityAction.Action.STOP_SPRINTING;
        send(new C0BPacketEntityAction(mc.thePlayer, action));
        send(new C0BPacketEntityAction(mc.thePlayer, action));
        sprintState = !sprintState;

        send(new C0FPacketConfirmTransaction(
                0, (short) mc.thePlayer.ticksExisted, false));

        C13PacketPlayerAbilities abilities = new C13PacketPlayerAbilities();
        abilities.setFlying(true);
        send(abilities);

        send(new C18PacketSpectate(mc.thePlayer.getUniqueID()));
    }

    private void sendIntaveBurst() {
        spoofedPitch = -spoofedPitch;
        send(new C03PacketPlayer.C05PacketPlayerLook(
                mc.thePlayer.rotationYaw, spoofedPitch, mc.thePlayer.onGround));

        int slot = mc.thePlayer.inventory.currentItem;
        send(new C09PacketHeldItemChange(slot));
        send(new C09PacketHeldItemChange(slot));

        send(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.UP));
    }

    private void send(Packet<?> packet) {
        PacketUtils.sendPacketNoEvent(packet);
    }
}
