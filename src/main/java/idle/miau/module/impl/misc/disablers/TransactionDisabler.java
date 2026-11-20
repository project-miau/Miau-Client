package idle.miau.module.impl.misc.disablers;

import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.utility.PacketUtils;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TransactionDisabler extends DisablerMode {
    private final Deque<C0FPacketConfirmTransaction> pendingTransactions = new ArrayDeque<>();

    public TransactionDisabler(DisablerModule owner) {
        super("DelayedTransaction", owner);
    }

    @Override
    public void onEnable() {
        clearPendingTransactions();
    }

    @Override
    public void onDisable() {
        C0FPacketConfirmTransaction transaction;
        while ((transaction = pollPendingTransaction()) != null) {
            PacketUtils.sendPacketNoEvent(transaction);
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (!owner.isEnabled() || !(event.getPacket() instanceof C0FPacketConfirmTransaction)) {
            return;
        }
        C0FPacketConfirmTransaction transaction = (C0FPacketConfirmTransaction) event.getPacket();
        synchronized (pendingTransactions) {
            pendingTransactions.addLast(transaction);
        }
        event.setCanceled(true);
    }

    @Override
    public void onWorldUnload(WorldEvent.Unload event) {
        clearPendingTransactions();
    }

    private void clearPendingTransactions() {
        synchronized (pendingTransactions) {
            pendingTransactions.clear();
        }
    }

    private C0FPacketConfirmTransaction pollPendingTransaction() {
        synchronized (pendingTransactions) {
            return pendingTransactions.pollFirst();
        }
    }
}
