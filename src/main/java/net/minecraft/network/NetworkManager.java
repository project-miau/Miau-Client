package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.vialoadingbase.netty.event.CompressionReorderEvent;
import de.florianmichael.viamcp.MCPVLBPipeline;
import de.florianmichael.viamcp.ViaMCP;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ITickable;
import net.minecraft.util.LazyLoadBase;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import idle.miau.event.DispatchPacketEvent;
import idle.miau.event.NoEventPacketEvent;
import idle.miau.module.impl.combat.KillAuraLifecycle;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.Miau;
import idle.miau.management.LagPacketPolicy;
import idle.miau.module.ModuleManager;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.accessor.IAccessorNetworkManager;
import idle.miau.mcp.callback.CallbackInfo;
import idle.miau.mcp.network.ClientPacketThreadDispatcher;
import idle.miau.module.impl.combat.TimerRange;
import idle.miau.utility.BlinkUtils;
import idle.miau.utility.PacketUtils;

public class NetworkManager extends SimpleChannelInboundHandler<Packet> implements IAccessorNetworkManager {
   private static final Logger logger = LogManager.getLogger();
   public static final Marker logMarkerNetwork = MarkerManager.getMarker("NETWORK");
   public static final Marker logMarkerPackets = MarkerManager.getMarker("NETWORK_PACKETS", logMarkerNetwork);
   public static final AttributeKey<EnumConnectionState> attrKeyConnectionState = AttributeKey.valueOf("protocol");
   public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyLoadBase<NioEventLoopGroup>() {
      protected NioEventLoopGroup load() {
         return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
      }
   };
   public static final LazyLoadBase<EpollEventLoopGroup> CLIENT_EPOLL_EVENTLOOP = new LazyLoadBase<EpollEventLoopGroup>() {
      protected EpollEventLoopGroup load() {
         return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
      }
   };
   public static final LazyLoadBase<LocalEventLoopGroup> CLIENT_LOCAL_EVENTLOOP = new LazyLoadBase<LocalEventLoopGroup>() {
      protected LocalEventLoopGroup load() {
         return new LocalEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
      }
   };
   private final EnumPacketDirection direction;
   private final Queue<NetworkManager.InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues.newConcurrentLinkedQueue();
   private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
   private Channel channel;
   private SocketAddress socketAddress;
   private INetHandler packetListener;
   private IChatComponent terminationReason;
   private boolean isEncrypted;
   private boolean disconnected;
   private final TerrainTransitionGate mimeow$terrainTransitionGate = new TerrainTransitionGate();

   public NetworkManager(EnumPacketDirection packetDirection) {
      this.direction = packetDirection;
   }

   public void channelActive(ChannelHandlerContext p_channelActive_1_) throws Exception {
      super.channelActive(p_channelActive_1_);
      this.channel = p_channelActive_1_.channel();
      this.socketAddress = this.channel.remoteAddress();

      try {
         this.setConnectionState(EnumConnectionState.HANDSHAKING);
      } catch (Throwable var3) {
         logger.fatal(var3);
      }

   }

   public void setConnectionState(EnumConnectionState newState) {
      this.channel.attr(attrKeyConnectionState).set(newState);
      this.channel.read();
      this.channel.config().setAutoRead(true);
      logger.debug("Enabled auto read");
   }

   public void channelInactive(ChannelHandlerContext p_channelInactive_1_) throws Exception {
      this.mimeow$terrainTransitionGate.reset();
      this.closeChannel(new ChatComponentTranslation("disconnect.endOfStream", new Object[0]));
   }

   public void exceptionCaught(ChannelHandlerContext p_exceptionCaught_1_, Throwable p_exceptionCaught_2_) throws Exception {
      ChatComponentTranslation chatcomponenttranslation;
      if(p_exceptionCaught_2_ instanceof TimeoutException) {
         chatcomponenttranslation = new ChatComponentTranslation("disconnect.timeout", new Object[0]);
      } else {
         chatcomponenttranslation = new ChatComponentTranslation("disconnect.genericReason", new Object[]{"Internal Exception: " + p_exceptionCaught_2_});
      }

      this.closeChannel(chatcomponenttranslation);
   }

   protected void channelRead0(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_) throws Exception {
      Minecraft minecraft = Minecraft.getMinecraft();
      if(this.packetListener instanceof NetHandlerPlayClient
              && ClientPacketThreadDispatcher.enqueueIfNeeded(minecraft,
              p_channelRead0_2_,
              () -> this.mimeow$processIncomingPacket(p_channelRead0_1_, p_channelRead0_2_))) {
         return;
      }
      this.mimeow$processIncomingPacket(p_channelRead0_1_, p_channelRead0_2_);
   }

   private void mimeow$processIncomingPacket(ChannelHandlerContext context, Packet packet) {
      CallbackInfo callbackInfo4 = new CallbackInfo("channelRead0", true);
      this.mimeow$receivePacket(context, packet, callbackInfo4);
      if(!callbackInfo4.isCancelled()) {
         if(this.channel.isOpen()) {
            try {
               packet.processPacket(this.packetListener);
            } catch (ThreadQuickExitException var5) {
               ;
            }
         }

      }
   }

   public void setNetHandler(INetHandler handler) {
      Validate.notNull(handler, "packetListener", new Object[0]);
      logger.debug("Set listener of {} to {}", new Object[]{this, handler});
      this.mimeow$terrainTransitionGate.reset();
      this.packetListener = handler;
   }

   public void sendPacket(Packet packetIn) {
      CallbackInfo callbackInfo3 = new CallbackInfo("sendPacket", true);
      this.mimeow$sendPacket(packetIn, callbackInfo3);
      if(!callbackInfo3.isCancelled()) {
         if(this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packetIn, (GenericFutureListener[])null);
         } else {
            this.readWriteLock.writeLock().lock();

            try {
               this.outboundPacketsQueue.add(new NetworkManager.InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[])null));
            } finally {
               this.readWriteLock.writeLock().unlock();
            }
         }

      }
   }

   public void sendPacket(Packet packetIn, GenericFutureListener<? extends Future<? super Void>> listener, GenericFutureListener<? extends Future<? super Void>>... listeners) {
      if(this.isChannelOpen()) {
         this.flushOutboundQueue();
         this.dispatchPacket(packetIn, (GenericFutureListener[])((GenericFutureListener[])ArrayUtils.add(listeners, 0, listener)));
      } else {
         this.readWriteLock.writeLock().lock();

         try {
            this.outboundPacketsQueue.add(new NetworkManager.InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[])((GenericFutureListener[])ArrayUtils.add(listeners, 0, listener))));
         } finally {
            this.readWriteLock.writeLock().unlock();
         }
      }

   }

   private void dispatchPacket(final Packet inPacket, final GenericFutureListener<? extends Future<? super Void>>[] futureListeners) {
      CallbackInfo callbackInfo = new CallbackInfo("dispatchPacket", false);
      this.mimeow$dispatchPacket(inPacket, futureListeners, callbackInfo);
      if(callbackInfo.isCancelled()) {
         return;
      }
      final EnumConnectionState enumconnectionstate = EnumConnectionState.getFromPacket(inPacket);
      final EnumConnectionState enumconnectionstate1 = (EnumConnectionState)this.channel.attr(attrKeyConnectionState).get();
      if(enumconnectionstate1 != enumconnectionstate) {
         logger.debug("Disabled auto read");
         this.channel.config().setAutoRead(false);
      }

      if(this.channel.eventLoop().inEventLoop()) {
         if(enumconnectionstate != enumconnectionstate1) {
            this.setConnectionState(enumconnectionstate);
         }

         ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);
         if(futureListeners != null) {
            channelfuture.addListeners(futureListeners);
         }

         channelfuture.addListener((ChannelFutureListener)this::handleWriteCompletion);
      } else {
         this.channel.eventLoop().execute(new Runnable() {
            public void run() {
               if(enumconnectionstate != enumconnectionstate1) {
                  NetworkManager.this.setConnectionState(enumconnectionstate);
               }

               ChannelFuture channelfuture1 = NetworkManager.this.channel.writeAndFlush(inPacket);
               if(futureListeners != null) {
                  channelfuture1.addListeners(futureListeners);
               }

               channelfuture1.addListener((ChannelFutureListener)NetworkManager.this::handleWriteCompletion);
            }
         });
      }

   }

   private void handleWriteCompletion(ChannelFuture future) throws Exception {
      if(!future.isSuccess()) {
         Throwable cause = future.cause();
         if(!(cause instanceof ClosedChannelException) || future.channel().isOpen()) {
            this.exceptionCaught(null, cause);
         }
      }
   }

   private void flushOutboundQueue() {
      if(this.channel != null && this.channel.isOpen()) {
         this.readWriteLock.readLock().lock();

         try {
            while(!this.outboundPacketsQueue.isEmpty()) {
               NetworkManager.InboundHandlerTuplePacketListener networkmanager$inboundhandlertuplepacketlistener = (NetworkManager.InboundHandlerTuplePacketListener)this.outboundPacketsQueue.poll();
               this.dispatchPacket(networkmanager$inboundhandlertuplepacketlistener.packet, networkmanager$inboundhandlertuplepacketlistener.futureListeners);
            }
         } finally {
            this.readWriteLock.readLock().unlock();
         }
      }

   }

   public void processReceivedPackets() {
      this.flushOutboundQueue();
      if(this.packetListener instanceof ITickable) {
         ((ITickable)this.packetListener).update();
      }

      this.channel.flush();
   }

   public SocketAddress getRemoteAddress() {
      return this.socketAddress;
   }

   public void closeChannel(IChatComponent message) {
      if(this.terminationReason == null) {
         this.terminationReason = message;
      }

      if(this.packetListener instanceof NetHandlerPlayClient) {
         this.mimeow$clearLagQueues();
      }

      if(this.channel != null && this.channel.isOpen()) {
         this.channel.close().awaitUninterruptibly();
      }

   }

   public boolean isLocalChannel() {
      return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
   }

   public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort, boolean useNativeTransport) {
      final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
      Class<? extends SocketChannel> oclass;
      LazyLoadBase<? extends EventLoopGroup> lazyloadbase;
      if(NetworkTransportCompatibility.shouldUseEpoll(useNativeTransport)) {
         oclass = EpollSocketChannel.class;
         lazyloadbase = CLIENT_EPOLL_EVENTLOOP;
      } else {
         oclass = NioSocketChannel.class;
         lazyloadbase = CLIENT_NIO_EVENTLOOP;
      }

      ((Bootstrap)((Bootstrap)((Bootstrap)(new Bootstrap()).group((EventLoopGroup)lazyloadbase.getValue())).handler(new ChannelInitializer<Channel>() {
         protected void initChannel(Channel p_initChannel_1_) throws Exception {
            try {
               p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, Boolean.valueOf(true));
            } catch (ChannelException var3) {
               ;
            }

            p_initChannel_1_.pipeline().addLast("timeout", new ReadTimeoutHandler(30)).addLast("splitter", new MessageDeserializer2()).addLast("decoder", new MessageDeserializer(EnumPacketDirection.CLIENTBOUND)).addLast("prepender", new MessageSerializer2()).addLast("encoder", new MessageSerializer(EnumPacketDirection.SERVERBOUND)).addLast(idle.miau.utility.packet.PacketPipeline.NAME, new idle.miau.utility.packet.PacketPipeline()).addLast("packet_handler", networkmanager);
            if (p_initChannel_1_ instanceof SocketChannel && ViaMCP.isReady()
                    && ViaLoadingBase.getInstance().getTargetVersion().getVersion() != ViaMCP.NATIVE_VERSION) {
               UserConnection user = new UserConnectionImpl(p_initChannel_1_, true);
               new ProtocolPipelineImpl(user);
               p_initChannel_1_.pipeline().addLast(new MCPVLBPipeline(user));
            }
         }
      })).channel(oclass)).connect(address, serverPort).syncUninterruptibly();
      return networkmanager;
   }

   public static NetworkManager provideLocalClient(SocketAddress address) {
      final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
      ((Bootstrap)((Bootstrap)((Bootstrap)(new Bootstrap()).group((EventLoopGroup)CLIENT_LOCAL_EVENTLOOP.getValue())).handler(new ChannelInitializer<Channel>() {
         protected void initChannel(Channel p_initChannel_1_) throws Exception {
            p_initChannel_1_.pipeline().addLast(idle.miau.utility.packet.PacketPipeline.NAME, new idle.miau.utility.packet.PacketPipeline()).addLast("packet_handler", networkmanager);
         }
      })).channel(LocalChannel.class)).connect(address).syncUninterruptibly();
      return networkmanager;
   }

   public void enableEncryption(SecretKey key) {
      this.isEncrypted = true;
      this.channel.pipeline().addBefore("splitter", "decrypt", new NettyEncryptingDecoder(CryptManager.createNetCipherInstance(2, key)));
      this.channel.pipeline().addBefore("prepender", "encrypt", new NettyEncryptingEncoder(CryptManager.createNetCipherInstance(1, key)));
   }

   public boolean getIsencrypted() {
      return this.isEncrypted;
   }

   public boolean isChannelOpen() {
      return this.channel != null && this.channel.isOpen();
   }

   public boolean hasNoChannel() {
      return this.channel == null;
   }

   public INetHandler getNetHandler() {
      return this.packetListener;
   }

   public IChatComponent getExitMessage() {
      return this.terminationReason;
   }

   public void disableAutoRead() {
      this.channel.config().setAutoRead(false);
   }

   public void setCompressionTreshold(int treshold) {
      if(treshold >= 0) {
         if(this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
            ((NettyCompressionDecoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
         } else {
            this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(treshold));
         }

         if(this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
            ((NettyCompressionEncoder)this.channel.pipeline().get("compress")).setCompressionTreshold(treshold);
         } else {
            this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(treshold));
         }
      } else {
         if(this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
            this.channel.pipeline().remove("decompress");
         }

         if(this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
            this.channel.pipeline().remove("compress");
         }
      }

      this.channel.pipeline().fireUserEventTriggered(new CompressionReorderEvent());

   }

   public void checkDisconnected() {
      if(this.channel != null && !this.channel.isOpen()) {
         if(!this.disconnected) {
            this.disconnected = true;
            if(this.getExitMessage() != null) {
               this.getNetHandler().onDisconnect(this.getExitMessage());
            } else if(this.getNetHandler() != null) {
               this.getNetHandler().onDisconnect(new ChatComponentText("Disconnected"));
            }
         } else {
            logger.warn("handleDisconnection() called twice");
         }
      }

   }

   public void mimeow$sendPacket(Packet p_sendPacket_1_, CallbackInfo ci) {
      boolean skipEvent = PacketUtils.consumeSendEventSkip(p_sendPacket_1_);
      if(LagPacketPolicy.isOutboundConnectionCritical(p_sendPacket_1_)
              || TimerRange.isOwnedPacket(p_sendPacket_1_)) {
         return;
      }
      if(!this.mimeow$isPlaySessionReady()) {
         return;
      }
      if(skipEvent) {
         McpRuntime.EVENTS.post(new NoEventPacketEvent(p_sendPacket_1_));
         if(TimerRange.shouldSilenceOutboundPacket(p_sendPacket_1_)) {
            ci.cancel();
            return;
         }
         boolean lagManagerFlushing = Miau.lagManager != null && Miau.lagManager.isFlushing();
         boolean lagRangeFlushing = Miau.lagRangePacketController != null
                 && Miau.lagRangePacketController.isFlushing();
         if(!lagManagerFlushing && !lagRangeFlushing
                 && ModuleManager.realLag != null
                 && ModuleManager.realLag.handleNoEventPacket(p_sendPacket_1_)) {
            ci.cancel();
            return;
         }
         if(!lagManagerFlushing && !lagRangeFlushing
                 && Miau.lagRangePacketController != null
                 && Miau.lagRangePacketController.handleNoEventPacket(p_sendPacket_1_)) {
            ci.cancel();
         } else if(!lagManagerFlushing && !lagRangeFlushing
                 && Miau.lagManager != null
                 && Miau.lagManager.handlePacket(p_sendPacket_1_)) {
            ci.cancel();
         }
      } else {
         SendPacketEvent sendPacketEvent = new SendPacketEvent(p_sendPacket_1_);
         McpRuntime.EVENTS.post(sendPacketEvent);
         if(sendPacketEvent.isCanceled() || TimerRange.shouldSilenceOutboundPacket(p_sendPacket_1_)) {
            ci.cancel();
         } else if(BlinkUtils.pushPacket(p_sendPacket_1_)) {
            ci.cancel();
         } else if(Miau.lagManager != null && !Miau.lagManager.isFlushing()
                 && Miau.lagManager.handlePacket(p_sendPacket_1_)) {
            ci.cancel();
         }

      }
      if(!ci.isCancelled() && !KillAuraLifecycle.finishSend(p_sendPacket_1_)) {
         ci.cancel();
      }
   }

   public void mimeow$dispatchPacket(Packet inPacket, GenericFutureListener[] futureListeners, CallbackInfo ci) {
      if(TimerRange.shouldSilenceOutboundPacket(inPacket)) {
         ci.cancel();
         return;
      }
      TimerRange.onPacketDispatched(inPacket);
      if(!this.mimeow$isPlaySessionReady()) {
         return;
      }
      DispatchPacketEvent dispatchPacketEvent = new DispatchPacketEvent(inPacket);
      McpRuntime.EVENTS.post(dispatchPacketEvent);
   }

   public void mimeow$receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
      boolean skipEvent = PacketUtils.consumeReceiveEventSkip(p_channelRead0_2_);
      boolean startsTerrainTransition = this.mimeow$terrainTransitionGate.beginIfBoundary(p_channelRead0_2_);
      if(startsTerrainTransition || p_channelRead0_2_ instanceof S40PacketDisconnect) {
         this.mimeow$clearLagQueues();
      } else if(p_channelRead0_2_ instanceof S08PacketPlayerPosLook) {
         this.mimeow$handleLagServerCorrection();
      }
      if(LagPacketPolicy.bypassesPlayPacketHooks(p_channelRead0_2_)) {
         return;
      }
      if(this.mimeow$terrainTransitionGate.shouldBypassHooks() && !startsTerrainTransition) {
         return;
      }
      if(!this.mimeow$isPlaySessionReady()) {
         return;
      }

      if(!skipEvent) {
         ReceivePacketEvent receivePacketEvent = new ReceivePacketEvent(p_channelRead0_2_);
         McpRuntime.EVENTS.post(receivePacketEvent);
         boolean mustReachVanilla = startsTerrainTransition
                 || p_channelRead0_2_ instanceof S07PacketRespawn
                 || p_channelRead0_2_ instanceof S08PacketPlayerPosLook
                 || LagPacketPolicy.isConnectionCritical(p_channelRead0_2_);
         if(receivePacketEvent.isCanceled()
                 && (!mustReachVanilla || receivePacketEvent.isProtectedCancellationAllowed())
                 || TimerRange.shouldBufferInboundPacket(p_channelRead0_2_)) {
            ci.cancel();
         }

      }
   }

   public void mimeow$completeTerrainTransition() {
      this.mimeow$terrainTransitionGate.complete();
   }

   private boolean mimeow$isPlaySessionReady() {
      if(!(this.packetListener instanceof NetHandlerPlayClient)) {
         return false;
      }
      Minecraft minecraft = Minecraft.getMinecraft();
      NetHandlerPlayClient playHandler = (NetHandlerPlayClient)this.packetListener;
      return minecraft != null
              && minecraft.theWorld != null
              && minecraft.thePlayer != null
              && minecraft.getNetHandler() == playHandler
              && playHandler.isDoneLoadingTerrain();
   }

   private void mimeow$clearLagQueues() {
      if(ModuleManager.timerRange != null) {
         ModuleManager.timerRange.clearConnection();
      }
      if(ModuleManager.noSlow != null) {
         ModuleManager.noSlow.clearGrimTest();
      }
      if(ModuleManager.realLag != null) {
         ModuleManager.realLag.clearConnection();
      }
      if(Miau.lagManager != null) {
         Miau.lagManager.clearConnection();
      }
      if(Miau.lagRangePacketController != null) {
         Miau.lagRangePacketController.resetAll(false);
      }
   }

   private void mimeow$handleLagServerCorrection() {
      if(ModuleManager.timerRange != null) {
         ModuleManager.timerRange.handleServerCorrection();
      }
      if(Miau.lagManager != null) {
         Miau.lagManager.clearConnection();
      }
      if(Miau.lagRangePacketController != null) {
         Miau.lagRangePacketController.handleServerCorrection();
      }
      if(ModuleManager.realLag != null) {
         ModuleManager.realLag.handleServerCorrection();
      }
   }

   public INetHandler getPacketListener() {
      return this.packetListener;
   }

   static class InboundHandlerTuplePacketListener {
      private final Packet packet;
      private final GenericFutureListener<? extends Future<? super Void>>[] futureListeners;

      public InboundHandlerTuplePacketListener(Packet inPacket, GenericFutureListener<? extends Future<? super Void>>... inFutureListeners) {
         this.packet = inPacket;
         this.futureListeners = inFutureListeners;
      }
   }
}
