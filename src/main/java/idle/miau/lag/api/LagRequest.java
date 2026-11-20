package idle.miau.lag.api;

import idle.miau.lag.timeout.AbstractTimeout;
import idle.miau.lag.queue.BiTrackLagNodeQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class LagRequest {

    private final @NotNull Set<EnumLagDirection> directions;
    private final @NotNull AbstractTimeout timeout;
    private final @Nullable BiTrackLagNodeQueue.PacketReleasePredicate packetPolicy;
    private final boolean pollPolicyOnIncomingPacket;

    public LagRequest(
            final @NotNull Set<EnumLagDirection> directions,
            final @NotNull AbstractTimeout timeout
    ) {
        this(directions, timeout, null, true);
    }

    public LagRequest(
            final @NotNull Set<EnumLagDirection> directions,
            final @NotNull AbstractTimeout timeout,
            final @Nullable BiTrackLagNodeQueue.PacketReleasePredicate packetPolicy
    ) {
        this(directions, timeout, packetPolicy, true);
    }

    public LagRequest(
            final @NotNull Set<EnumLagDirection> directions,
            final @NotNull AbstractTimeout timeout,
            final @Nullable BiTrackLagNodeQueue.PacketReleasePredicate packetPolicy,
            boolean pollPolicyOnIncomingPacket
    ) {
        this.directions = directions;
        this.timeout = timeout;
        this.packetPolicy = packetPolicy;
        this.pollPolicyOnIncomingPacket = pollPolicyOnIncomingPacket;
    }

    public @NotNull Set<EnumLagDirection> getDirections() {
        return directions;
    }

    public @NotNull AbstractTimeout getTimeout() {
        return timeout;
    }

    public @Nullable BiTrackLagNodeQueue.PacketReleasePredicate getPacketPolicy() {
        return packetPolicy;
    }

    public boolean shouldPollPolicyOnIncomingPacket() {
        return pollPolicyOnIncomingPacket;
    }

}
