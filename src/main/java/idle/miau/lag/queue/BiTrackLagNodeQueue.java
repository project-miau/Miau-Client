package idle.miau.lag.queue;

import idle.miau.lag.api.EnumLagDirection;
import idle.miau.lag.api.LagRequest;
import idle.miau.lag.handler.AbstractFastTrackProvider;
import idle.miau.lag.queue.node.api.AbstractLagNode;
import idle.miau.lag.queue.node.impl.AddRequestLagNode;
import idle.miau.lag.queue.node.impl.PacketLagNode;
import net.minecraft.network.Packet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public final class BiTrackLagNodeQueue {

    private final @NotNull TrackState incomingState;
    private final @NotNull TrackState outgoingState;

    public BiTrackLagNodeQueue(
            final @NotNull AbstractFastTrackProvider fastTrackProvider
    ) {
        incomingState = new TrackState(new ArrayDeque<>(), fastTrackProvider);
        outgoingState = new TrackState(new ArrayDeque<>(), fastTrackProvider);
    }

    public void clear() {
        incomingState.clear();
        outgoingState.clear();
    }

    public boolean tick(final @Nullable Packet<?> packet, final @Nullable EnumLagDirection direction) {
        if ((packet == null) != (direction == null)) {
            throw new NullPointerException();
        }

        if (direction == null) {
            incomingState.tick(null, null);
            outgoingState.tick(null, null);
            return false;
        } else switch (direction) {
            case INBOUND: {
                return incomingState.tick(packet, direction);
            }
            case OUTBOUND: {
                return outgoingState.tick(packet, direction);
            }
            default: {
                return false;
            }
        }
    }

    public void requestLag(final @NotNull LagRequest request) {
        for (final @NotNull EnumLagDirection direction : request.getDirections()) {
            switch (direction) {
                case OUTBOUND: {
                    outgoingState.addRequest(request);
                }
                break;
                case INBOUND: {
                    incomingState.addRequest(request);
                }
                break;
            }
        }
    }

    public int getQueuedPacketCount(final @NotNull EnumLagDirection direction) {
        switch (direction) {
            case OUTBOUND:
                return outgoingState.getQueuedPacketCount();
            case INBOUND:
                return incomingState.getQueuedPacketCount();
            default:
                return 0;
        }
    }

    public void releaseExpiredPackets(
            final @NotNull EnumLagDirection direction,
            final @NotNull LagRequest owner,
            long maxAgeMs
    ) {
        switch (direction) {
            case OUTBOUND:
                outgoingState.releaseExpiredPackets(owner, maxAgeMs);
                break;
            case INBOUND:
                incomingState.releaseExpiredPackets(owner, maxAgeMs);
                break;
        }
    }

    public int pollRequest(final @NotNull EnumLagDirection direction, final @NotNull LagRequest request) {
        switch (direction) {
            case OUTBOUND:
                return outgoingState.pollRequest(request);
            case INBOUND:
                return incomingState.pollRequest(request);
            default:
                return 0;
        }
    }

    public boolean releaseRequestIfDrained(final @NotNull EnumLagDirection direction,
                                           final @NotNull LagRequest request) {
        switch (direction) {
            case OUTBOUND:
                return outgoingState.releaseRequestIfDrained(request);
            case INBOUND:
                return incomingState.releaseRequestIfDrained(request);
            default:
                return true;
        }
    }

    public void release(final @NotNull LagRequest owner) {
        for (final @NotNull EnumLagDirection direction : owner.getDirections()) {
            switch (direction) {
                case OUTBOUND:
                    outgoingState.release(owner);
                    break;
                case INBOUND:
                    incomingState.release(owner);
                    break;
            }
        }
    }

    @FunctionalInterface
    public interface PacketReleasePredicate {
        boolean shouldHold(@NotNull PacketLagNode node, long queueAgeMs);
    }

    private static final class TrackState {

        private final @NotNull Deque<AbstractLagNode> track;
        private final @NotNull AbstractFastTrackProvider fastTrackProvider;

        private @Nullable LagRequest currentlyAwaiting = null;

        private TrackState(
                final @NotNull Deque<AbstractLagNode> track,
                final @NotNull AbstractFastTrackProvider fastTrackProvider
        ) {
            this.track = track;
            this.fastTrackProvider = fastTrackProvider;
        }

        private synchronized void addRequest(final @NotNull LagRequest request) {
            track.add(new AddRequestLagNode(request));
        }

        private synchronized boolean tick(final @Nullable Packet<?> packet, final @Nullable EnumLagDirection direction) {
            if (track.isEmpty() && (currentlyAwaiting == null || currentlyAwaiting.getTimeout().isTimedOut())) {
                currentlyAwaiting = null;
                return false;
            }

            PacketLagNode incoming = null;
            if (packet != null) {
                incoming = new PacketLagNode(packet, direction);
                track.add(incoming);
            }

            @Nullable LagRequest awaiting = currentlyAwaiting;

            try {
                while (awaiting == null || awaiting.getTimeout().isTimedOut()) {
                    final @Nullable AbstractLagNode popped = pop();

                    if (popped == null) {
                        awaiting = null;
                        break;
                    }

                    if (popped instanceof PacketLagNode) {
                        ((PacketLagNode) popped).goThrough(fastTrackProvider);
                    } else if (popped instanceof AddRequestLagNode) {
                        awaiting = ((AddRequestLagNode) popped).getRequest();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            currentlyAwaiting = awaiting;

            if (currentlyAwaiting != null && currentlyAwaiting.getPacketPolicy() != null) {
                PacketReleasePredicate policy = currentlyAwaiting.getPacketPolicy();
                Boolean initialHold = incoming != null && track.contains(incoming)
                        ? policy.shouldHold(incoming, 0L) : null;
                if (incoming != null && track.peekFirst() == incoming
                        && Boolean.FALSE.equals(initialHold)) {
                    track.removeFirst();
                    incoming.goThrough(fastTrackProvider);
                }
                if (currentlyAwaiting.shouldPollPolicyOnIncomingPacket()) {
                    pollPolicyHead(currentlyAwaiting);
                }
            }

            return true;
        }

        private synchronized int pollRequest(final @NotNull LagRequest request) {
            if (request.getTimeout().isTimedOut()) {
                return 0;
            }
            if (currentlyAwaiting == null || currentlyAwaiting.getTimeout().isTimedOut()) {
                advanceQueue();
            }
            if (currentlyAwaiting != request) {
                return 0;
            }
            return pollPolicyHead(request);
        }

        private synchronized boolean releaseRequestIfDrained(final @NotNull LagRequest request) {
            if (currentlyAwaiting != request) {
                Iterator<AbstractLagNode> iterator = track.iterator();
                while (iterator.hasNext()) {
                    AbstractLagNode node = iterator.next();
                    if (node instanceof AddRequestLagNode
                            && ((AddRequestLagNode) node).getRequest() == request) {
                        iterator.remove();
                    }
                }
                request.getTimeout().forceTimeOut();
                return true;
            }

            for (AbstractLagNode node : track) {
                if (node instanceof AddRequestLagNode) {
                    break;
                }
                if (node instanceof PacketLagNode) {
                    return false;
                }
            }

            request.getTimeout().forceTimeOut();
            currentlyAwaiting = null;
            Iterator<AbstractLagNode> iterator = track.iterator();
            while (iterator.hasNext()) {
                AbstractLagNode node = iterator.next();
                if (node instanceof AddRequestLagNode
                        && ((AddRequestLagNode) node).getRequest() == request) {
                    iterator.remove();
                }
            }
            advanceQueue();
            return true;
        }

        private synchronized void advanceQueue() {
            @Nullable LagRequest awaiting = currentlyAwaiting;
            try {
                while (awaiting == null || awaiting.getTimeout().isTimedOut()) {
                    final @Nullable AbstractLagNode popped = pop();
                    if (popped == null) {
                        awaiting = null;
                        break;
                    }
                    if (popped instanceof PacketLagNode) {
                        ((PacketLagNode) popped).goThrough(fastTrackProvider);
                    } else if (popped instanceof AddRequestLagNode) {
                        awaiting = ((AddRequestLagNode) popped).getRequest();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            currentlyAwaiting = awaiting;
        }

        private int pollPolicyHead(final @NotNull LagRequest request) {
            PacketReleasePredicate policy = request.getPacketPolicy();
            if (policy == null) {
                return 0;
            }
            int released = 0;
            long now = System.currentTimeMillis();
            while (!track.isEmpty()) {
                AbstractLagNode head = track.peekFirst();
                if (head instanceof AddRequestLagNode) {
                    LagRequest next = ((AddRequestLagNode) head).getRequest();
                    if (next == request || next.getTimeout().isTimedOut()) {
                        track.removeFirst();
                        continue;
                    }
                    currentlyAwaiting = next;
                    break;
                }
                if (!(head instanceof PacketLagNode)) {
                    track.removeFirst();
                    continue;
                }
                PacketLagNode queuedPacket = (PacketLagNode) head;
                if (policy.shouldHold(queuedPacket, now - queuedPacket.getQueuedAtMs())) {
                    break;
                }
                track.removeFirst();
                queuedPacket.goThrough(fastTrackProvider);
                released++;
            }
            return released;
        }

        private synchronized int getQueuedPacketCount() {
            int count = 0;
            for (AbstractLagNode node : track) {
                if (node instanceof PacketLagNode) {
                    count++;
                }
            }
            return count;
        }

        private synchronized void releaseExpiredPackets(final @NotNull LagRequest owner, long maxAgeMs) {
            if (currentlyAwaiting != owner) {
                return;
            }

            long cutoff = System.currentTimeMillis() - maxAgeMs;
            List<PacketLagNode> toRelease = new ArrayList<>();
            Iterator<AbstractLagNode> iterator = track.iterator();
            while (iterator.hasNext()) {
                AbstractLagNode node = iterator.next();
                if (node instanceof AddRequestLagNode) {
                    break;
                }
                if (node instanceof PacketLagNode) {
                    PacketLagNode packet = (PacketLagNode) node;
                    if (packet.getQueuedAtMs() <= cutoff) {
                        iterator.remove();
                        toRelease.add(packet);
                    }
                }
            }
            for (PacketLagNode packet : toRelease) {
                packet.goThrough(fastTrackProvider);
            }
        }

        private synchronized void release(final @NotNull LagRequest owner) {
            List<PacketLagNode> toRelease = new ArrayList<>();
            Iterator<AbstractLagNode> iterator = track.iterator();
            boolean ownerActive = currentlyAwaiting == owner;
            boolean ownerQueued = false;
            while (iterator.hasNext()) {
                AbstractLagNode node = iterator.next();
                if (node instanceof AddRequestLagNode) {
                    AddRequestLagNode requestNode = (AddRequestLagNode) node;
                    if (ownerActive) {
                        break;
                    }
                    if (requestNode.getRequest() == owner) {
                        iterator.remove();
                        ownerQueued = true;
                        continue;
                    }
                    if (ownerQueued) {
                        break;
                    }
                }
                if (ownerActive && node instanceof PacketLagNode) {
                    iterator.remove();
                    toRelease.add((PacketLagNode) node);
                }
                else if (ownerQueued && node instanceof PacketLagNode) {
                    iterator.remove();
                    toRelease.add((PacketLagNode) node);
                }
            }
            if (ownerActive) {
                currentlyAwaiting = null;
            }
            for (PacketLagNode packet : toRelease) {
                packet.goThrough(fastTrackProvider);
            }
        }

        private synchronized void clear() {
            track.clear();
            currentlyAwaiting = null;
        }

        private @Nullable AbstractLagNode pop() {
            return track.pollFirst();
        }

    }

}
