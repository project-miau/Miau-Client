package idle.miau.performance;

import com.viaversion.viaversion.libs.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.util.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.gen.structure.StructureBoundingBox;

public final class ChunkIndexedTickSet extends TreeSet<NextTickListEntry> {
    private final Long2ObjectOpenHashMap<List<NextTickListEntry>> byChunk = new Long2ObjectOpenHashMap<>();

    private static long key(NextTickListEntry entry) {
        BlockPos pos = entry.position;
        return ChunkCoordIntPair.chunkXZ2Int(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public boolean add(NextTickListEntry entry) {
        if (!super.add(entry)) {
            return false;
        }

        long key = key(entry);
        List<NextTickListEntry> entries = byChunk.get(key);
        if (entries == null) {
            entries = new ArrayList<>(4);
            byChunk.put(key, entries);
        }
        entries.add(entry);
        return true;
    }

    @Override
    public boolean remove(Object object) {
        if (!super.remove(object)) {
            return false;
        }

        unindex((NextTickListEntry) object);
        return true;
    }

    @Override
    public NextTickListEntry pollFirst() {
        NextTickListEntry entry = super.pollFirst();
        if (entry != null) {
            unindex(entry);
        }
        return entry;
    }

    @Override
    public NextTickListEntry pollLast() {
        NextTickListEntry entry = super.pollLast();
        if (entry != null) {
            unindex(entry);
        }
        return entry;
    }

    @Override
    public void clear() {
        super.clear();
        byChunk.clear();
    }

    private void unindex(NextTickListEntry entry) {
        long key = key(entry);
        List<NextTickListEntry> entries = byChunk.get(key);
        if (entries == null) {
            return;
        }

        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) == entry) {
                entries.remove(i);
                break;
            }
        }

        if (entries.isEmpty()) {
            byChunk.remove(key);
        }
    }

    @Override
    public Iterator<NextTickListEntry> iterator() {
        Iterator<NextTickListEntry> delegate = super.iterator();
        return new Iterator<NextTickListEntry>() {
            private NextTickListEntry last;

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public NextTickListEntry next() {
                last = delegate.next();
                return last;
            }

            @Override
            public void remove() {
                delegate.remove();
                unindex(last);
            }
        };
    }

    public Iterator<NextTickListEntry> iterator(StructureBoundingBox bounds) {
        List<NextTickListEntry> entries = getTicks(bounds);
        if (entries.isEmpty()) {
            return Collections.emptyIterator();
        }

        Iterator<NextTickListEntry> delegate = entries.iterator();
        return new Iterator<NextTickListEntry>() {
            private NextTickListEntry last;
            private boolean canRemove;

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public NextTickListEntry next() {
                last = delegate.next();
                canRemove = true;
                return last;
            }

            @Override
            public void remove() {
                if (!canRemove) {
                    throw new IllegalStateException();
                }
                ChunkIndexedTickSet.this.remove(last);
                canRemove = false;
            }
        };
    }

    private List<NextTickListEntry> getTicks(StructureBoundingBox bounds) {
        if (bounds.maxX <= bounds.minX || bounds.maxZ <= bounds.minZ) {
            return Collections.emptyList();
        }

        List<NextTickListEntry> result = new ArrayList<>();
        for (int chunkX = bounds.minX >> 4; chunkX <= bounds.maxX - 1 >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ >> 4; chunkZ <= bounds.maxZ - 1 >> 4; chunkZ++) {
                List<NextTickListEntry> entries = byChunk.get(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
                if (entries == null) {
                    continue;
                }

                for (NextTickListEntry entry : entries) {
                    BlockPos pos = entry.position;
                    if (pos.getX() >= bounds.minX && pos.getX() < bounds.maxX
                            && pos.getZ() >= bounds.minZ && pos.getZ() < bounds.maxZ) {
                        result.add(entry);
                    }
                }
            }
        }
        Collections.sort(result);
        return result;
    }
}
