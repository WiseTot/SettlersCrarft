package com.nxtlinea.settlerscraft.building;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Временный простой менеджер очереди "точек застройки".
 * Игрок добавляет точки через SurveyorWandItem, жители забирают их по одной.
 * Пока общий для всего сервера (не привязан к конкретной деревне) — это нормально для теста.
 */
public class ConstructionManager {

    private static final Deque<BlockPos> PENDING_SITES = new ArrayDeque<>();

    public static void addSite(BlockPos pos) {
        PENDING_SITES.addLast(pos.toImmutable());
    }

    /**
     * Забирает следующую точку из очереди (или null, если очередь пуста).
     * Вызывается жителем, когда он свободен и ищет задачу.
     */
    public static BlockPos pollNextSite() {
        return PENDING_SITES.pollFirst();
    }
}