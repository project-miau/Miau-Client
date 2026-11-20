package keystrokesmod.utility;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.module.setting.impl.BlockListSetting;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

import java.util.Map;
import java.util.Set;

public class SharedBlockHighlightCache {
    private SharedBlockHighlightCache() {
    }

    public static SharedBlockHighlightCache get() {
        return Holder.INSTANCE;
    }

    public void attachBlockList(BlockListSetting setting) {
        idle.miau.utility.SharedBlockHighlightCache.get().attachBlockList(setting);
    }

    public void detachBlockList() {
        idle.miau.utility.SharedBlockHighlightCache.get().detachBlockList();
    }

    public void attachBed() {
        idle.miau.utility.SharedBlockHighlightCache.get().attachBed();
    }

    public void detachBed() {
        idle.miau.utility.SharedBlockHighlightCache.get().detachBed();
    }

    public boolean anyConsumerActive() {
        return idle.miau.utility.SharedBlockHighlightCache.get().anyConsumerActive();
    }

    public void clear() {
        idle.miau.utility.SharedBlockHighlightCache.get().clear();
    }

    public void addUpdateListener(idle.miau.utility.SharedBlockHighlightCache.UpdateListener listener) {
        idle.miau.utility.SharedBlockHighlightCache.get().addUpdateListener(listener);
    }

    public void removeUpdateListener(idle.miau.utility.SharedBlockHighlightCache.UpdateListener listener) {
        idle.miau.utility.SharedBlockHighlightCache.get().removeUpdateListener(listener);
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        idle.miau.utility.SharedBlockHighlightCache.get().enqueueChunk(chunkX, chunkZ);
    }

    public void removeChunk(int chunkX, int chunkZ) {
        idle.miau.utility.SharedBlockHighlightCache.get().removeChunk(chunkX, chunkZ);
    }

    public void enqueueLoadedChunks() {
        idle.miau.utility.SharedBlockHighlightCache.get().enqueueLoadedChunks();
    }

    public void tickScan(int maxSections) {
        idle.miau.utility.SharedBlockHighlightCache.get().tickScan(maxSections);
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        idle.miau.utility.SharedBlockHighlightCache.get().onBlockChange(pos, newState);
    }

    public void onBlockListSettingsChanged() {
        idle.miau.utility.SharedBlockHighlightCache.get().onBlockListSettingsChanged();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBlockList() {
        return idle.miau.utility.SharedBlockHighlightCache.get().entriesBlockList();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBedFeet() {
        return idle.miau.utility.SharedBlockHighlightCache.get().entriesBedFeet();
    }

    public int totalBlockList() {
        return idle.miau.utility.SharedBlockHighlightCache.get().totalBlockList();
    }

    public int totalBedFeet() {
        return idle.miau.utility.SharedBlockHighlightCache.get().totalBedFeet();
    }

    public boolean containsBlockList(BlockPos pos) {
        return idle.miau.utility.SharedBlockHighlightCache.get().containsBlockList(pos);
    }

    public void handleReceivePacket(ReceivePacketEvent e) {
        idle.miau.utility.SharedBlockHighlightCache.get().handleReceivePacket(e);
    }

    private static final class Holder {
        private static final SharedBlockHighlightCache INSTANCE = new SharedBlockHighlightCache();
    }
}
