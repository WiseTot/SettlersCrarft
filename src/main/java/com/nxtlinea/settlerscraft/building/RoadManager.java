package com.nxtlinea.settlerscraft.building;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RoadManager {

    private static final Deque<List<BlockPos>> PENDING_ROADS = new ArrayDeque<>();

    public static void addRoad(List<BlockPos> path) {
        if (!path.isEmpty()) {
            PENDING_ROADS.addLast(path);
        }
    }

    public static List<BlockPos> pollNextRoad() {
        return PENDING_ROADS.pollFirst();
    }
}