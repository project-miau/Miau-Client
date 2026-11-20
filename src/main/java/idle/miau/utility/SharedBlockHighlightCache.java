package idle.miau.utility;

import idle.miau.event.ReceivePacketEvent;
import idle.miau.module.setting.impl.BlockListSetting;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedBlockHighlightCache {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final SharedBlockHighlightCache INSTANCE = new SharedBlockHighlightCache();

    private final Map<Long, Set<BlockPos>> blockListByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Set<BlockPos>> bedFootByChunk = new ConcurrentHashMap<>();
    private final Set<UpdateListener> updateListeners = ConcurrentHashMap.newKeySet();
    private final Deque<ChunkScanTask> scanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();

    private BlockListHighlightMatcher blockListMatcher;
    private boolean bedAttached;
    private int totalBlockList;
    private int totalBedFeet;

    private static final BedFootHighlightMatcher BED_MATCHER = new BedFootHighlightMatcher();

    private SharedBlockHighlightCache() {
    }

    private static final class ChunkScanTask {
        private final int chunkX;
        private final int chunkZ;
        private final long chunkKey;
        private int nextSection;

        private ChunkScanTask(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkKey = key(chunkX, chunkZ);
        }
    }

    public interface UpdateListener {
        void onBlockChanged(BlockPos pos, IBlockState newState);

        void onChunkQueued(int chunkX, int chunkZ);

        void onChunkRemoved(int chunkX, int chunkZ);

        void onCacheCleared();
    }

    public static SharedBlockHighlightCache get() {
        return INSTANCE;
    }

    public void attachBlockList(BlockListSetting setting) {
        this.blockListMatcher = new BlockListHighlightMatcher(setting);
    }

    public void detachBlockList() {
        this.blockListMatcher = null;
        clearBlockListLayer();
        if (!anyConsumerActive()) {
            clearScanQueue();
        }
    }

    public void attachBed() {
        this.bedAttached = true;
    }

    public void detachBed() {
        this.bedAttached = false;
        clearBedLayer();
        if (!anyConsumerActive()) {
            clearScanQueue();
        }
    }

    private boolean isBlockListActive() {
        return blockListMatcher != null && blockListMatcher.isActive();
    }

    private boolean isBedActive() {
        return bedAttached;
    }

    public boolean anyConsumerActive() {
        return isBlockListActive() || isBedActive();
    }

    public void clear() {
        clearBlockListLayer();
        clearBedLayer();
        clearScanQueue();
        for (UpdateListener listener : updateListeners) {
            listener.onCacheCleared();
        }
    }

    public void addUpdateListener(UpdateListener listener) {
        if (listener != null) {
            updateListeners.add(listener);
        }
    }

    public void removeUpdateListener(UpdateListener listener) {
        if (listener != null) {
            updateListeners.remove(listener);
        }
    }

    public void enqueueChunk(int chunkX, int chunkZ) {
        if (!anyConsumerActive()) {
            return;
        }
        long chunkKey = key(chunkX, chunkZ);
        if (!queuedChunks.add(chunkKey)) {
            return;
        }
        clearBlockListChunk(chunkKey);
        clearBedChunk(chunkKey);
        scanQueue.addLast(new ChunkScanTask(chunkX, chunkZ));
        for (UpdateListener listener : updateListeners) {
            listener.onChunkQueued(chunkX, chunkZ);
        }
    }

    public void removeChunk(int chunkX, int chunkZ) {
        long k = key(chunkX, chunkZ);
        queuedChunks.remove(k);
        scanQueue.removeIf(task -> task.chunkKey == k);
        clearBlockListChunk(k);
        clearBedChunk(k);
        for (UpdateListener listener : updateListeners) {
            listener.onChunkRemoved(chunkX, chunkZ);
        }
    }

    public void enqueueLoadedChunks() {
        if (!anyConsumerActive()) {
            return;
        }
        clearScanQueue();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        int rd = mc.gameSettings.renderDistanceChunks;
        int pcx = mc.thePlayer.chunkCoordX;
        int pcz = mc.thePlayer.chunkCoordZ;
        for (int cx = pcx - rd; cx <= pcx + rd; cx++) {
            for (int cz = pcz - rd; cz <= pcz + rd; cz++) {
                Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    enqueueChunk(cx, cz);
                }
            }
        }
    }

    public void tickScan(int maxSections) {
        if (mc.theWorld == null || !anyConsumerActive() || maxSections <= 0) {
            return;
        }
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        int remaining = maxSections;
        while (remaining > 0 && !scanQueue.isEmpty()) {
            ChunkScanTask task = scanQueue.peekFirst();
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(task.chunkX, task.chunkZ);
            if (chunk == null || chunk instanceof EmptyChunk) {
                finishTask(task);
                continue;
            }

            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            while (task.nextSection < sections.length && sections[task.nextSection] == null) {
                task.nextSection++;
            }
            if (task.nextSection >= sections.length) {
                finishTask(task);
                continue;
            }

            scanSection(chunk, sections[task.nextSection], task.nextSection);
            task.nextSection++;
            remaining--;
            while (task.nextSection < sections.length && sections[task.nextSection] == null) {
                task.nextSection++;
            }
            if (task.nextSection >= sections.length) {
                finishTask(task);
            }
        }
    }

    public void onBlockChange(BlockPos pos, IBlockState newState) {
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        BlockPos immutablePos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());

        if (isBlockListActive()) {
            if (blockListMatcher.matchesBlock(newState) && blockListMatcher.shouldIndexAt(pos, newState)) {
                if (addMatch(blockListByChunk, ck, immutablePos)) {
                    totalBlockList++;
                }
            } else if (removeMatch(blockListByChunk, ck, pos)) {
                totalBlockList--;
            }
        }

        if (isBedActive()) {
            if (BED_MATCHER.matchesBlock(newState) && BED_MATCHER.shouldIndexAt(pos, newState)) {
                if (addMatch(bedFootByChunk, ck, immutablePos)) {
                    totalBedFeet++;
                }
            } else if (removeMatch(bedFootByChunk, ck, pos)) {
                totalBedFeet--;
            }
        }

        for (UpdateListener listener : updateListeners) {
            listener.onBlockChanged(immutablePos, newState);
        }
    }

    public void onBlockListSettingsChanged() {
        if (blockListMatcher != null) {
            blockListMatcher.beginScanPass();
        }
        rescanBlockListLayer();
    }

    private void rescanBlockListLayer() {
        clearBlockListLayer();
        clearScanQueue();
        if (!anyConsumerActive()) {
            return;
        }
        enqueueLoadedChunks();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBlockList() {
        return blockListByChunk.entrySet();
    }

    public Iterable<Map.Entry<Long, Set<BlockPos>>> entriesBedFeet() {
        return bedFootByChunk.entrySet();
    }

    public int totalBlockList() {
        return totalBlockList;
    }

    public int totalBedFeet() {
        return totalBedFeet;
    }

    public boolean containsBlockList(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long ck = key(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = blockListByChunk.get(ck);
        return set != null && set.contains(pos);
    }

    public void handleReceivePacket(ReceivePacketEvent e) {
        if (!anyConsumerActive()) {
            return;
        }
        if (e.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange pkt = (S23PacketBlockChange) e.getPacket();
            onBlockChange(pkt.getBlockPosition(), pkt.getBlockState());
        } else if (e.getPacket() instanceof S22PacketMultiBlockChange) {
            S22PacketMultiBlockChange pkt = (S22PacketMultiBlockChange) e.getPacket();
            for (S22PacketMultiBlockChange.BlockUpdateData data : pkt.getChangedBlocks()) {
                onBlockChange(data.getPos(), data.getBlockState());
            }
        } else if (e.getPacket() instanceof S21PacketChunkData) {
            S21PacketChunkData pkt = (S21PacketChunkData) e.getPacket();
            if (pkt.getExtractedSize() == 0) {
                removeChunk(pkt.getChunkX(), pkt.getChunkZ());
            } else {
                enqueueChunk(pkt.getChunkX(), pkt.getChunkZ());
            }
        } else if (e.getPacket() instanceof S26PacketMapChunkBulk) {
            S26PacketMapChunkBulk pkt = (S26PacketMapChunkBulk) e.getPacket();
            for (int i = 0; i < pkt.getChunkCount(); i++) {
                enqueueChunk(pkt.getChunkX(i), pkt.getChunkZ(i));
            }
        }
    }

    private void scanSection(Chunk chunk, ExtendedBlockStorage section, int sectionIndex) {
        long ck = key(chunk.xPosition, chunk.zPosition);
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;
        int baseY = sectionIndex << 4;
        boolean scanBlockList = isBlockListActive();
        boolean scanBeds = isBedActive();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    IBlockState state = section.get(x, y, z);
                    if (state == null) {
                        continue;
                    }
                    boolean blockCandidate = scanBlockList && blockListMatcher.matchesBlock(state);
                    boolean bedCandidate = scanBeds && BED_MATCHER.matchesBlock(state);
                    if (!blockCandidate && !bedCandidate) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                    if (blockCandidate && blockListMatcher.shouldIndexAt(pos, state)
                        && addMatch(blockListByChunk, ck, pos)) {
                        totalBlockList++;
                    }
                    if (bedCandidate && BED_MATCHER.shouldIndexAt(pos, state)
                        && addMatch(bedFootByChunk, ck, pos)) {
                        totalBedFeet++;
                    }
                }
            }
        }
    }

    private void finishTask(ChunkScanTask task) {
        if (scanQueue.peekFirst() == task) {
            scanQueue.pollFirst();
        } else {
            scanQueue.remove(task);
        }
        queuedChunks.remove(task.chunkKey);
    }

    private void clearScanQueue() {
        scanQueue.clear();
        queuedChunks.clear();
    }

    private void clearBlockListLayer() {
        blockListByChunk.clear();
        totalBlockList = 0;
    }

    private void clearBedLayer() {
        bedFootByChunk.clear();
        totalBedFeet = 0;
    }

    private void clearBlockListChunk(long chunkKey) {
        Set<BlockPos> removed = blockListByChunk.remove(chunkKey);
        if (removed != null) {
            totalBlockList = Math.max(0, totalBlockList - removed.size());
        }
    }

    private void clearBedChunk(long chunkKey) {
        Set<BlockPos> removed = bedFootByChunk.remove(chunkKey);
        if (removed != null) {
            totalBedFeet = Math.max(0, totalBedFeet - removed.size());
        }
    }

    private static boolean addMatch(Map<Long, Set<BlockPos>> layer, long chunkKey, BlockPos pos) {
        return layer.computeIfAbsent(chunkKey, ignored -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    private static boolean removeMatch(Map<Long, Set<BlockPos>> layer, long chunkKey, BlockPos pos) {
        Set<BlockPos> positions = layer.get(chunkKey);
        if (positions == null || !positions.remove(pos)) {
            return false;
        }
        if (positions.isEmpty()) {
            layer.remove(chunkKey, positions);
        }
        return true;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
