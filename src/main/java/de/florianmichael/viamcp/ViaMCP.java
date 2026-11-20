/*
 * ViaMCP/ViaLoadingBase integration, based on the GPL-3.0 ViaMCP project.
 * https://github.com/ViaVersionMCP/ViaMCP
 */
package de.florianmichael.viamcp;

import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.ProtocolManager;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.gui.AsyncVersionSlider;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ViaMCP {
    public static final int NATIVE_VERSION = 47;

    /** Public compatibility handle retained from the upstream ViaMCP API. */
    public static volatile ViaMCP INSTANCE;
    private final AtomicBoolean transactionFixInstalled = new AtomicBoolean();
    private AsyncVersionSlider asyncVersionSlider;

    private ViaMCP() {
        ViaLoadingBase.ViaLoadingBaseBuilder.create()
                .runDirectory(new File("ViaMCP"))
                .nativeVersion(NATIVE_VERSION)
                .build();
        installTransactionCompatibility();
    }

    public static synchronized ViaMCP create() {
        if (INSTANCE == null) {
            INSTANCE = new ViaMCP();
        }
        return INSTANCE;
    }

    public static ViaMCP getInstance() {
        return INSTANCE;
    }

    public static boolean isReady() {
        return INSTANCE != null && ViaLoadingBase.getInstance() != null;
    }

    public static boolean isTargetVersionAtLeast(ProtocolVersion version) {
        ViaLoadingBase base = ViaLoadingBase.getInstance();
        return base != null && base.getTargetVersion() != null
                && base.getTargetVersion().newerThanOrEqualTo(version);
    }

    public static boolean isTargetVersionNewerThanNative() {
        ViaLoadingBase base = ViaLoadingBase.getInstance();
        return base != null && base.getTargetVersion() != null
                && base.getTargetVersion().getVersion() != NATIVE_VERSION;
    }

    /** Creates the upstream-compatible top-left protocol slider. */
    public void initAsyncSlider() {
        initAsyncSlider(5, 5, 110, 20);
    }

    public void initAsyncSlider(int x, int y, int width, int height) {
        asyncVersionSlider = new AsyncVersionSlider(-1, x, y, Math.max(width, 110), height);
    }

    public AsyncVersionSlider getAsyncVersionSlider() {
        return asyncVersionSlider;
    }

    private void installTransactionCompatibility() {
        ProtocolManager protocols = Via.getManager().getProtocolManager();
        Runnable installer = () -> {
            if (!transactionFixInstalled.compareAndSet(false, true)) {
                return;
            }
            Protocol1_17To1_16_4 protocol = protocols.getProtocol(Protocol1_17To1_16_4.class);
            if (protocol == null) {
                transactionFixInstalled.set(false);
                ViaLoadingBase.LOGGER.warning("ViaBackwards 1.17 transaction protocol is unavailable");
                return;
            }
            // ViaBackwards registers PING with a null mapped type because its default
            // handler cancels the packet. Replacing only that handler would therefore
            // leak the modern packet id into the remaining protocol path. Keep the
            // four-byte payload, but restore the packet identities explicitly.
            protocol.replaceClientbound(ClientboundPackets1_17.PING,
                    wrapper -> wrapper.setPacketType(ClientboundPackets1_16_2.CONTAINER_ACK));
            protocol.replaceServerbound(ServerboundPackets1_16_2.CONTAINER_ACK,
                    wrapper -> wrapper.setPacketType(ServerboundPackets1_17.PONG));
            ViaLoadingBase.LOGGER.info("Installed the ViaMCP 1.17+ transaction bridge");
        };

        protocols.completeMappingDataLoading(Protocol1_17To1_16_4.class);
        installer.run();
    }
}
