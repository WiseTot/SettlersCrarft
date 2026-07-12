package com.nxtlinea.settlerscraft.entity.profession;

import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MinerBehavior implements ProfessionBehavior {

    private static final int RETURN_THRESHOLD = 64;
    private static final int MAX_TUNNEL_LENGTH = 40;
    private static final int WORK_STEP_DELAY_TICKS = 10;
    private static final int BLOCKED_RETRY_TICKS = 100;
    private static final float MINING_SPEED_MULTIPLIER = 4.0F; // примерно как каменная кирка у игрока

    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private enum DigPhase { FORWARD, DOWN }

    private final Map<UUID, MinerState> states = new HashMap<>();

    private static class MinerState {
        BlockPos cursor;
        Direction direction;
        DigPhase phase = DigPhase.FORWARD;
        int lengthDug = 0;
    }

    @Override
    public boolean tryStartWork(SettlerEntity settler) {
        List<BlockPos> queue = settler.getWorkQueue();

        if (queue != null && !queue.isEmpty()) {
            settler.startWalkingToCloseWorkSite(queue.get(0));
            return true;
        }
        if (queue != null) {
            settler.setWorkQueue(null);
        }

        int totalCarried = settler.getCarriedItems().values().stream().mapToInt(Integer::intValue).sum();
        if (totalCarried >= RETURN_THRESHOLD) {
            settler.returnCarriedItemsToStorage();
            return true;
        }

        MinerState minerState = states.get(settler.getUuid());

        if (minerState == null || minerState.lengthDug >= MAX_TUNNEL_LENGTH) {
            minerState = startNewTunnel(settler);
            states.put(settler.getUuid(), minerState);
        }

        List<BlockPos> batch = nextStep(settler.getWorld(), minerState);

        if (batch.isEmpty()) {
            states.remove(settler.getUuid());
            settler.waitFor(BLOCKED_RETRY_TICKS);
            return true;
        }

        settler.setWorkQueue(batch);
        settler.startWalkingToCloseWorkSite(batch.get(0));
        return true;
    }

    @Override
    public void performWorkAction(SettlerEntity settler) {
        List<BlockPos> queue = settler.getWorkQueue();

        if (queue == null || queue.isEmpty()) {
            settler.waitFor(WORK_STEP_DELAY_TICKS);
            return;
        }

        BlockPos pos = queue.get(0);
        World world = settler.getWorld();
        BlockState state = world.getBlockState(pos);

        if (!isMineable(state)) {
            queue.remove(0);
            return;
        }

        settler.equipItem(Items.STONE_PICKAXE);
        boolean broken = settler.tickBreaking(pos, MINING_SPEED_MULTIPLIER);

        if (broken) {
            Item drop = getDropFor(state);
            world.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            settler.addCarriedItem(drop, 1);
            queue.remove(0);
        }
    }

    private MinerState startNewTunnel(SettlerEntity settler) {
        MinerState state = new MinerState();
        state.cursor = settler.getBlockPos();
        state.direction = DIRECTIONS[settler.getRandom().nextInt(DIRECTIONS.length)];
        state.lengthDug = 0;
        return state;
    }

    /**
     * Копает поочерёдно: шаг вперёд (по горизонтали, с запасом по высоте для прохода),
     * затем шаг вниз (по вертикали) — как ступеньки лестницы. Каждый отдельный шаг —
     * только по одной оси, поэтому житель всегда может физически до него дойти
     * (в отличие от прежнего диагонального шага, который создавал непроходимые "провисания").
     */
    private List<BlockPos> nextStep(World world, MinerState state) {
        while (state.lengthDug < MAX_TUNNEL_LENGTH) {
            if (state.phase == DigPhase.FORWARD) {
                BlockPos target = state.cursor.offset(state.direction);
                BlockPos head = target.up();
                BlockPos headExtra = head.up();
                BlockPos floor = target.down();

                BlockState targetState = world.getBlockState(target);
                BlockState headState = world.getBlockState(head);
                BlockState headExtraState = world.getBlockState(headExtra);
                BlockState floorState = world.getBlockState(floor);

                if (isDangerous(targetState) || isDangerous(headState) || isDangerous(headExtraState) || isDangerous(floorState)) {
                    return List.of();
                }

                List<BlockPos> result = new ArrayList<>();
                if (isMineable(targetState)) result.add(target.toImmutable());
                if (isMineable(headState)) result.add(head.toImmutable());
                if (isMineable(headExtraState)) result.add(headExtra.toImmutable());

                state.cursor = target;
                state.phase = DigPhase.DOWN;
                state.lengthDug++;

                if (!result.isEmpty()) {
                    return result;
                }
            } else {
                BlockPos target = state.cursor.down();
                BlockState targetState = world.getBlockState(target);

                if (isDangerous(targetState)) {
                    return List.of();
                }

                List<BlockPos> result = new ArrayList<>();
                if (isMineable(targetState)) result.add(target.toImmutable());

                state.cursor = target;
                state.phase = DigPhase.FORWARD;
                state.lengthDug++;

                if (!result.isEmpty()) {
                    return result;
                }
            }
            // оба варианта уже пусты — пробуем следующую фазу на этой же итерации цикла
        }

        return List.of();
    }

    private boolean isDangerous(BlockState state) {
        return state.getFluidState().getFluid() != Fluids.EMPTY
                || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.WATER);
    }

    private boolean isMineable(BlockState state) {
        if (state.isAir()) return false;
        if (state.isOf(Blocks.BEDROCK)) return false;
        return !state.isIn(BlockTags.WITHER_IMMUNE);
    }

    private Item getDropFor(BlockState state) {
        var block = state.getBlock();
        if (block == Blocks.STONE) return Items.COBBLESTONE;
        if (block == Blocks.DEEPSLATE) return Items.COBBLED_DEEPSLATE;
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Items.COAL;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.RAW_IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return Items.RAW_GOLD;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.RAW_COPPER;
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Items.LAPIS_LAZULI;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        return block.asItem();
    }
}