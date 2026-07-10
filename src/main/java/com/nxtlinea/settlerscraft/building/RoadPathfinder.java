package com.nxtlinea.settlerscraft.building;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class RoadPathfinder {

    private static final int MAX_NODES = 6000;

    private record Node(int x, int z, int g, int h, Node parent) {
        int f() {
            return g + h;
        }
    }

    public static List<BlockPos> findPath(World world, BlockPos start, BlockPos end) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Set<Long> visited = new HashSet<>();

        int sx = start.getX(), sz = start.getZ();
        int ex = end.getX(), ez = end.getZ();

        open.add(new Node(sx, sz, 0, heuristic(sx, sz, ex, ez), null));

        Node goalNode = null;
        int processed = 0;

        while (!open.isEmpty() && processed < MAX_NODES) {
            Node current = open.poll();
            long key = key(current.x(), current.z());
            if (visited.contains(key)) {
                continue;
            }
            visited.add(key);
            processed++;

            if (current.x() == ex && current.z() == ez) {
                goalNode = current;
                break;
            }

            int currentY = surfaceY(world, current.x(), current.z());
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

            for (int[] dir : dirs) {
                int nx = current.x() + dir[0];
                int nz = current.z() + dir[1];
                long nk = key(nx, nz);
                if (visited.contains(nk)) {
                    continue;
                }

                int ny = surfaceY(world, nx, nz);
                int heightDiff = Math.abs(ny - currentY);
                if (heightDiff > 1) {
                    continue;
                }

                int stepCost = 1 + heightDiff * 2;
                open.add(new Node(nx, nz, current.g() + stepCost, heuristic(nx, nz, ex, ez), current));
            }
        }

        if (goalNode == null) {
            return List.of();
        }

        List<BlockPos> path = new ArrayList<>();
        Node cursor = goalNode;
        while (cursor != null) {
            int y = surfaceY(world, cursor.x(), cursor.z());
            path.add(0, new BlockPos(cursor.x(), y, cursor.z()));
            cursor = cursor.parent();
        }
        return path;
    }

    private static int heuristic(int x, int z, int ex, int ez) {
        return Math.abs(x - ex) + Math.abs(z - ez);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int surfaceY(World world, int x, int z) {
        return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    public static List<BlockPos> widen(World world, List<BlockPos> centerPath) {
        java.util.Map<Long, BlockPos> result = new java.util.LinkedHashMap<>();

        for (int i = 0; i < centerPath.size(); i++) {
            BlockPos current = centerPath.get(i);
            BlockPos reference = (i + 1 < centerPath.size()) ? centerPath.get(i + 1) : centerPath.get(i - 1);

            int dx = reference.getX() - current.getX();
            int dz = reference.getZ() - current.getZ();

            int perpX = dz != 0 ? 1 : 0;
            int perpZ = dx != 0 ? 1 : 0;

            addColumn(result, world, current.getX(), current.getZ());
            addColumn(result, world, current.getX() + perpX, current.getZ() + perpZ);
            addColumn(result, world, current.getX() - perpX, current.getZ() - perpZ);
        }

        return new ArrayList<>(result.values());
    }

    private static void addColumn(java.util.Map<Long, BlockPos> result, World world, int x, int z) {
        long key = key(x, z);
        if (!result.containsKey(key)) {
            result.put(key, new BlockPos(x, surfaceY(world, x, z), z));
        }
    }
}