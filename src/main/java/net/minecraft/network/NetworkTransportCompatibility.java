package net.minecraft.network;

import io.netty.channel.epoll.Epoll;
import io.netty.util.internal.PlatformDependent;

final class NetworkTransportCompatibility
{
    private NetworkTransportCompatibility()
    {
    }

    static boolean shouldUseEpoll(boolean nativeTransportRequested)
    {
        return shouldUseEpoll(
                nativeTransportRequested,
                Epoll.isAvailable(),
                PlatformDependent.directBufferPreferred());
    }

    static boolean shouldUseEpoll(
            boolean nativeTransportRequested,
            boolean epollAvailable,
            boolean directBufferAddressAccessAvailable)
    {
        return nativeTransportRequested
                && epollAvailable
                && directBufferAddressAccessAvailable;
    }
}
