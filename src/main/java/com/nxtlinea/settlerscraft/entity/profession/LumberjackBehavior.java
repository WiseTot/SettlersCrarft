package com.nxtlinea.settlerscraft.entity.profession;

import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LumberjackBehavior implements ProfessionBehavior {

    private static final int TREE_SEARCH_RADIUS = 12;
    private static final int MAX_TRUNK_HEIGHT = 10;
    private static final int LEAVES_CHECK_RADIUS = 4;
    private static final int WORK_STEP_DELAY_TICKS = 10;
    private static final int NO_TREE_ANYWHERE_WAIT_TICKS = 60;
    private static final double RETURN_TO_ZONE_DISTANCE_SQ = 225.0; // ~15 блоков

    private final Map<UUID, PendingPlant> pendingPlants = new HashMap<>();
    private final Map<UUID, BlockPos> lastTreeZone = new HashMap<>();

    private record PendingPlant(BlockPos pos, Item saplingItem) {}

    @Override
    public boolean tryStartWork(SettlerEntity settler) {
        List<BlockPos> queue = settler.getWorkQueue();

        if (queue != null && !queue.isEmpty()) {
            settler.startWalkingToWorkSite(queue.get(0));
            return true;
        }

        if (queue != null) {
            settler.setWorkQueue(null);
        }

        if (!settler.getCarriedItems().isEmpty()) {
            settler.returnCarriedItemsToStorage();
            return true;
        }

        BlockPos tree = findNearestTree(settler);

        if (tree != null) {
            BlockState baseState = settler.getWorld().getBlockState(tree);
            Item saplingItem = getSaplingFor(baseState.getBlock());

            if (saplingItem != null) {
                pendingPlants.put(settler.getUuid(), new PendingPlant(tree, saplingItem));
            }

            lastTreeZone.put(settler.getUuid(), tree);

            List<BlockPos> column = buildTrunkColumn(settler, tree);
            settler.setWorkQueue(column);
            settler.startWalkingToWorkSite(column.get(0));
            return true;
        }

        // Рядом дерева нет. Если помним лесную зону и сейчас от неё далеко — сходим обратно туда
        BlockPos zone = lastTreeZone.get(settler.getUuid());
        if (zone != null && settler.getBlockPos().getSquaredDistance(zone) > RETURN_TO_ZONE_DISTANCE_SQ) {
            settler.startWalkingToWorkSite(zone); // workQueue уже null — значит это просто переход, не рубка
            return true;
        }

        // Мы либо уже в этой зоне и там пусто, либо зоны не знаем вовсе — забываем её и бродим
        lastTreeZone.remove(settler.getUuid());
        return false;
    }

    @Override
    public void performWorkAction(SettlerEntity settler) {
        List<BlockPos> queue = settler.getWorkQueue();

        if (queue == null) {
            // Пришли не рубить, а просто вернулись в лесную зону — пробуем найти дерево уже отсюда
            if (!tryStartWork(settler)) {
                settler.waitFor(NO_TREE_ANYWHERE_WAIT_TICKS);
            }
            return;
        }

        if (!queue.isEmpty()) {
            BlockPos pos = queue.get(0);
            World world = settler.getWorld();
            BlockState state = world.getBlockState(pos);

            if (state.isIn(BlockTags.LOGS)) {
                Item logItem = state.getBlock().asItem();

                settler.lookAtAndSwing(pos);
                settler.equipItem(logItem);
                world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                settler.addCarriedItem(logItem, 1);
            }

            queue.remove(0);

            if (queue.isEmpty()) {
                plantSaplingIfPending(settler);
            }
        }

        settler.waitFor(WORK_STEP_DELAY_TICKS);
    }

    private void plantSaplingIfPending(SettlerEntity settler) {
        PendingPlant pending = pendingPlants.remove(settler.getUuid());
        if (pending == null) {
            return;
        }

        World world = settler.getWorld();
        BlockPos plantPos = pending.pos();

        boolean spotIsClear = world.getBlockState(plantPos).isAir();
        boolean groundIsSoil = world.getBlockState(plantPos.down()).isIn(BlockTags.DIRT);

        if (spotIsClear && groundIsSoil) {
            Block saplingBlock = Block.getBlockFromItem(pending.saplingItem());
            world.setBlockState(plantPos, saplingBlock.getDefaultState());
        }
    }

    private Item getSaplingFor(Block logBlock) {
        if (logBlock == Blocks.OAK_LOG) return Items.OAK_SAPLING;
        if (logBlock == Blocks.BIRCH_LOG) return Items.BIRCH_SAPLING;
        if (logBlock == Blocks.SPRUCE_LOG) return Items.SPRUCE_SAPLING;
        if (logBlock == Blocks.JUNGLE_LOG) return Items.JUNGLE_SAPLING;
        if (logBlock == Blocks.ACACIA_LOG) return Items.ACACIA_SAPLING;
        if (logBlock == Blocks.DARK_OAK_LOG) return Items.DARK_OAK_SAPLING;
        if (logBlock == Blocks.CHERRY_LOG) return Items.CHERRY_SAPLING;
        return null;
    }

    private BlockPos findNearestTree(SettlerEntity settler) {
        BlockPos origin = settler.getBlockPos();
        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                origin.add(-TREE_SEARCH_RADIUS, -4, -TREE_SEARCH_RADIUS),
                origin.add(TREE_SEARCH_RADIUS, 6, TREE_SEARCH_RADIUS))) {

            BlockState state = settler.getWorld().getBlockState(pos);
            if (!state.isIn(BlockTags.LOGS)) {
                continue;
            }

            BlockState below = settler.getWorld().getBlockState(pos.down());
            if (below.isIn(BlockTags.LOGS)) {
                continue; // это не основание ствола
            }

            if (!hasNearbyLeaves(settler.getWorld(), pos.toImmutable())) {
                continue; // рядом нет листвы — скорее всего это не дерево, а постройка
            }

            double distSq = origin.getSquaredDistance(pos);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = pos.toImmutable();
            }
        }

        return closest;
    }

    private boolean hasNearbyLeaves(World world, BlockPos base) {
        for (BlockPos pos : BlockPos.iterate(
                base.add(-LEAVES_CHECK_RADIUS, 1, -LEAVES_CHECK_RADIUS),
                base.add(LEAVES_CHECK_RADIUS, MAX_TRUNK_HEIGHT + 2, LEAVES_CHECK_RADIUS))) {

            if (world.getBlockState(pos).isIn(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> buildTrunkColumn(SettlerEntity settler, BlockPos base) {
        List<BlockPos> column = new ArrayList<>();
        BlockPos cursor = base;

        for (int i = 0; i < MAX_TRUNK_HEIGHT; i++) {
            BlockState state = settler.getWorld().getBlockState(cursor);
            if (!state.isIn(BlockTags.LOGS)) {
                break;
            }
            column.add(cursor.toImmutable());
            cursor = cursor.up();
        }

        return column;
    }
}