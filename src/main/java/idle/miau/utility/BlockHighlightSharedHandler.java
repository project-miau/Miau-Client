package idle.miau.utility;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.module.ModuleManager;
import net.minecraft.client.Minecraft;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;

public final class BlockHighlightSharedHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    @Subscribe(priority = Priority.LOWEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.isCanceled()) {
            return;
        }
        Runnable update = () -> {
            if (!e.isCanceled()) {
                SharedBlockHighlightCache.get().handleReceivePacket(e);
            }
        };
        if (mc.isCallingFromMinecraftThread()) {
            update.run();
        } else {
            mc.addScheduledTask(update);
        }
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (!cache.anyConsumerActive()) {
            return;
        }
        int budget = 0;
        if (ModuleManager.bedESP != null) {
            budget = Math.max(budget, ModuleManager.bedESP.getScanSpeedBudget());
        }
        if (budget > 0) {
            cache.tickScan(budget);
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        SharedBlockHighlightCache.get().clear();
    }

    @Subscribe
    public void onEntityJoinWorld(EntityJoinWorldEvent e) {
        if (e.entity != mc.thePlayer) {
            return;
        }
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (!cache.anyConsumerActive()) {
            return;
        }
        cache.clear();
        cache.enqueueLoadedChunks();
    }
}
