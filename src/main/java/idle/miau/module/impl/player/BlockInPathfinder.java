package idle.miau.module.impl.player;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class BlockInPathfinder {
    private BlockInPathfinder() {
    }

    static List<BlockPos> findPath(BlockPos destination, List<BlockPos> starts, Set<BlockPos> open) {
        if (!open.contains(destination) || starts.isEmpty()) return null;
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator
                .comparingInt((Node node) -> node.score)
                .thenComparingInt(node -> node.heuristic)
                .thenComparingInt(node -> node.position.getY())
                .thenComparingInt(node -> node.position.getX())
                .thenComparingInt(node -> node.position.getZ()));
        Map<BlockPos, Integer> costs = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        for (BlockPos start : starts) {
            if (!open.contains(start) || costs.containsKey(start)) continue;
            costs.put(start, 0);
            parents.put(start, null);
            queue.add(new Node(start, 0, distance(start, destination)));
        }
        int limit = Math.min(8000, Math.max(500, open.size() * 2));
        int visited = 0;
        while (!queue.isEmpty() && visited < limit) {
            Node node = queue.remove();
            if (node.cost != costs.get(node.position)) continue;
            visited++;
            if (node.position.equals(destination)) {
                List<BlockPos> route = new ArrayList<>();
                for (BlockPos position = destination; position != null; position = parents.get(position)) {
                    route.add(position);
                }
                Collections.reverse(route);
                return route;
            }
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos next = node.position.offset(facing);
                if (!open.contains(next)) continue;
                int cost = node.cost + (facing.getAxis() == EnumFacing.Axis.Y ? 2 : 1);
                Integer previous = costs.get(next);
                if (previous != null && previous <= cost) continue;
                costs.put(next, cost);
                parents.put(next, node.position);
                queue.add(new Node(next, cost, distance(next, destination)));
            }
        }
        return null;
    }

    private static int distance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ())
                + 2 * Math.abs(first.getY() - second.getY());
    }

    private static final class Node {
        private final BlockPos position;
        private final int cost;
        private final int heuristic;
        private final int score;

        private Node(BlockPos position, int cost, int heuristic) {
            this.position = position;
            this.cost = cost;
            this.heuristic = heuristic;
            this.score = cost + heuristic;
        }
    }
}
