package idle.miau.module.impl.movement.noslow;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class GrimTest {
    private static final long MAX_TRANSACTION_DELAY = TimeUnit.MILLISECONDS.toNanos(150L);
    private static final int MAX_TRANSACTIONS = 64;
    private final Deque<Packet<?>> transactions = new ArrayDeque<>();
    private final Consumer<Packet<?>> sender;
    private boolean sending;
    private boolean conflictingAction = true;
    private boolean waitingForMovement;
    private long firstTransactionAt;

    public GrimTest(Consumer<Packet<?>> sender) {
        this.sender = sender;
    }

    public boolean handle(Packet<?> packet, boolean usingItem, int slot, long now) {
        if (sending) {
            return false;
        }
        releaseExpired(now);
        if (packet instanceof C0FPacketConfirmTransaction && waitingForMovement) {
            if (transactions.size() >= MAX_TRANSACTIONS) {
                flush();
                return false;
            }
            if (transactions.isEmpty()) {
                firstTransactionAt = now;
            }
            transactions.addLast(packet);
            return true;
        }
        if (!(packet instanceof C03PacketPlayer)) {
            observe(packet);
            return false;
        }

        boolean swap = usingItem && !conflictingAction && slot >= 0 && slot < 9;
        conflictingAction = false;
        waitingForMovement = false;
        if (!swap && transactions.isEmpty()) {
            return false;
        }

        sending = true;
        try {
            if (swap) {
                sender.accept(new C09PacketHeldItemChange((slot + 1) % 9));
            }
            try {
                sender.accept(packet);
                drainTransactions();
            } finally {
                if (swap) {
                    sender.accept(new C09PacketHeldItemChange(slot));
                    waitingForMovement = true;
                }
            }
        } finally {
            sending = false;
        }
        return true;
    }

    public void observe(Packet<?> packet) {
        if (sending) {
            return;
        }
        if (packet instanceof C03PacketPlayer) {
            conflictingAction = false;
        } else if (packet instanceof C02PacketUseEntity
                || packet instanceof C07PacketPlayerDigging
                || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange
                || packet instanceof C0BPacketEntityAction
                || packet instanceof C0EPacketClickWindow
                || packet instanceof C16PacketClientStatus) {
            conflictingAction = true;
        }
    }

    public void releaseExpired(long now) {
        if (!transactions.isEmpty() && now - firstTransactionAt >= MAX_TRANSACTION_DELAY) {
            flush();
        }
    }

    public void flush() {
        if (sending) {
            return;
        }
        sending = true;
        try {
            drainTransactions();
            waitingForMovement = false;
        } finally {
            sending = false;
        }
    }

    private void drainTransactions() {
        while (!transactions.isEmpty()) {
            sender.accept(transactions.removeFirst());
        }
        firstTransactionAt = 0L;
    }

    public void reset() {
        transactions.clear();
        conflictingAction = true;
        waitingForMovement = false;
        firstTransactionAt = 0L;
    }
}
