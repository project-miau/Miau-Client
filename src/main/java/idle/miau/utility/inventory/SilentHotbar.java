package idle.miau.utility.inventory;

import idle.miau.event.PreSlotScrollEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.SlotUpdateEvent;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S09PacketHeldItemChange;

public final class SilentHotbar {
    public static final SilentHotbar INSTANCE = new SilentHotbar();

    private final Minecraft mc = Minecraft.getMinecraft();
    private State state;
    private int ticksSinceLastUpdate;

    private SilentHotbar() {
    }

    public synchronized void selectSlotSilently(
            Object requester, int slot, Integer ticksUntilReset,
            boolean immediate, boolean render, boolean resetManually) {
        selectSlotSilently(
                requester, slot, null, ticksUntilReset,
                immediate, render, resetManually);
    }

    public synchronized void selectSlotSilently(
            Object requester, int slot, Integer renderedSlot, Integer ticksUntilReset,
            boolean immediate, boolean render, boolean resetManually) {
        if (slot < 0 || slot >= InventoryPlayer.getHotbarSize() || mc.thePlayer == null) {
            return;
        }
        if (renderedSlot != null
                && (renderedSlot < 0 || renderedSlot >= InventoryPlayer.getHotbarSize())) {
            renderedSlot = null;
        }
        state = new State(
                slot, renderedSlot, requester, ticksUntilReset, render, resetManually);
        ticksSinceLastUpdate = 0;
        if (immediate) {
            syncCurrentItem();
        }
    }

    public synchronized void resetSlot(Object requester, boolean immediate) {
        if (state == null || requester != null && state.requester != requester) {
            return;
        }
        state = null;
        ticksSinceLastUpdate = 0;
        if (immediate) {
            syncCurrentItem();
        }
    }

    public synchronized boolean isSlotModified(Object requester) {
        return state != null && state.requester == requester;
    }

    public synchronized boolean isModified() {
        return state != null;
    }

    public synchronized int getCurrentSlot() {
        if (state != null) {
            return state.enforcedSlot;
        }
        return (mc == null || mc.thePlayer == null) ? 0 : mc.thePlayer.inventory.currentItem;
    }

    public synchronized int getCurrentSlot(InventoryPlayer inventory) {
        if (inventory != null && mc != null && mc.thePlayer != null && inventory.player == mc.thePlayer && state != null) {
            return state.enforcedSlot;
        }
        return inventory == null ? 0 : inventory.currentItem;
    }

    public synchronized int renderSlot(boolean renderEnforced) {
        if (mc == null || mc.thePlayer == null) {
            return 0;
        }
        if (state == null || !renderEnforced && !state.render) {
            return state != null && state.renderedSlot != null
                    ? state.renderedSlot
                    : mc.thePlayer.inventory.currentItem;
        }
        return state.enforcedSlot;
    }

    public synchronized ItemStack getRenderedStack(InventoryPlayer inventory) {
        if (inventory == null) {
            return null;
        }
        int slot = (mc != null && inventory.player == mc.thePlayer) ? renderSlot(false) : inventory.currentItem;
        return slot >= 0 && slot < inventory.mainInventory.length
                ? inventory.getStackInSlot(slot)
                : null;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        synchronized (this) {
            if (state == null) {
                return;
            }
            if (state.resetTicks != null && ticksSinceLastUpdate >= state.resetTicks) {
                resetSlot(state.requester, false);
                return;
            }
            ticksSinceLastUpdate++;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSlotScroll(PreSlotScrollEvent event) {
        resetForManualChange();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSlotUpdate(SlotUpdateEvent event) {
        resetForManualChange();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof C09PacketHeldItemChange)) {
            return;
        }
        synchronized (this) {
            C09PacketHeldItemChange packet = (C09PacketHeldItemChange) event.getPacket();
            if (state != null && state.resetManually && packet.getSlotId() != state.enforcedSlot) {
                resetSlot(null, false);
            }
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S09PacketHeldItemChange) {
            resetSlot(null, false);
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetSlot(null, false);
    }

    private synchronized void resetForManualChange() {
        if (state != null && state.resetManually) {
            resetSlot(null, false);
        }
    }

    private void syncCurrentItem() {
        if (mc.thePlayer != null && mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    private static final class State {
        private final int enforcedSlot;
        private final Integer renderedSlot;
        private final Object requester;
        private final Integer resetTicks;
        private final boolean render;
        private final boolean resetManually;

        private State(
                int enforcedSlot, Integer renderedSlot, Object requester, Integer resetTicks,
                boolean render, boolean resetManually) {
            this.enforcedSlot = enforcedSlot;
            this.renderedSlot = renderedSlot;
            this.requester = requester;
            this.resetTicks = resetTicks;
            this.render = render;
            this.resetManually = resetManually;
        }
    }
}
