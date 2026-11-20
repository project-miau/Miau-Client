package net.minecraft.client.renderer.chunk;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class VisGraph {
    private static final int SECTION_SIZE = 4096;
    private static final int[] BOUNDARY_INDICES = new int[1352];
    private static final ThreadLocal<int[]> FLOOD_QUEUE = ThreadLocal.withInitial(() -> new int[SECTION_SIZE]);
    private final BitSet occupied = new BitSet(SECTION_SIZE);
    private int emptyCount = SECTION_SIZE;

    public void func_178606_a(BlockPos pos) {
        occupied.set(getIndex(pos));
        emptyCount--;
    }

    private static int getIndex(BlockPos pos) {
        return pos.getX() & 15 | (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4;
    }

    public SetVisibility computeVisibility() {
        SetVisibility visibility = new SetVisibility();
        if (SECTION_SIZE - emptyCount < 256) {
            visibility.setAllVisible(true);
        } else if (emptyCount != 0) {
            int[] queue = FLOOD_QUEUE.get();
            for (int index : BOUNDARY_INDICES) {
                if (!occupied.get(index)) {
                    visibility.setManyVisible(flood(index, queue));
                }
            }
        }
        return visibility;
    }

    public Set<EnumFacing> func_178609_b(BlockPos pos) {
        int mask = flood(getIndex(pos), FLOOD_QUEUE.get());
        Set<EnumFacing> faces = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing face : EnumFacing.VALUES) {
            if ((mask & 1 << face.ordinal()) != 0) {
                faces.add(face);
            }
        }
        return faces;
    }

    private int flood(int start, int[] queue) {
        int head = 0;
        int tail = 1;
        int faces = 0;
        queue[0] = start;
        occupied.set(start);
        while (head < tail) {
            int index = queue[head++];
            int x = index & 15;
            int y = index >> 8;
            int z = index >> 4 & 15;
            if (x == 0) {
                faces |= 1 << EnumFacing.WEST.ordinal();
            } else {
                tail = enqueue(index - 1, queue, tail);
            }
            if (x == 15) {
                faces |= 1 << EnumFacing.EAST.ordinal();
            } else {
                tail = enqueue(index + 1, queue, tail);
            }
            if (y == 0) {
                faces |= 1 << EnumFacing.DOWN.ordinal();
            } else {
                tail = enqueue(index - 256, queue, tail);
            }
            if (y == 15) {
                faces |= 1 << EnumFacing.UP.ordinal();
            } else {
                tail = enqueue(index + 256, queue, tail);
            }
            if (z == 0) {
                faces |= 1 << EnumFacing.NORTH.ordinal();
            } else {
                tail = enqueue(index - 16, queue, tail);
            }
            if (z == 15) {
                faces |= 1 << EnumFacing.SOUTH.ordinal();
            } else {
                tail = enqueue(index + 16, queue, tail);
            }
        }
        return faces;
    }

    private int enqueue(int index, int[] queue, int tail) {
        if (!occupied.get(index)) {
            occupied.set(index);
            queue[tail++] = index;
        }
        return tail;
    }

    static {
        int count = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
                        BOUNDARY_INDICES[count++] = x | y << 8 | z << 4;
                    }
                }
            }
        }
    }
}
