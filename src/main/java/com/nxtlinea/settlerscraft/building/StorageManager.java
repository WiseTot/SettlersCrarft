package com.nxtlinea.settlerscraft.building;

import com.nxtlinea.settlerscraft.block.StorageBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Простой реестр позиций складов (в памяти, не сохраняется между запусками сервера — это нормально для теста).
 */
public class StorageManager {

    private static final Set<BlockPos> STORAGE_POSITIONS = new HashSet<>();

    public static void register(BlockPos pos) {
        STORAGE_POSITIONS.add(pos.toImmutable());
    }

    public static void unregister(BlockPos pos) {
        STORAGE_POSITIONS.remove(pos);
    }

    /**
     * Находит ближайший работающий склад к указанной позиции, или null если складов нет.
     */
    public static BlockPos findNearest(World world, BlockPos from) {
        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BlockPos candidate : STORAGE_POSITIONS) {
            if (!(world.getBlockEntity(candidate) instanceof StorageBlockEntity)) {
                continue; // склад был сломан, но не успел удалиться из реестра
            }

            double distSq = from.getSquaredDistance(candidate);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = candidate;
            }
        }

        return closest;
    }
}