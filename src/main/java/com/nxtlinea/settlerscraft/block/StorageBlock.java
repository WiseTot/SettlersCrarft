package com.nxtlinea.settlerscraft.block;

import com.nxtlinea.settlerscraft.building.StorageManager;
import com.nxtlinea.settlerscraft.screen.ModScreenHandlers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StorageBlock extends Block implements BlockEntityProvider {

    public StorageBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StorageBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            StorageManager.register(pos);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof StorageBlockEntity storage) {
                ItemScatterer.spawn(world, pos, storage.getItemsForScatter());
            }
            StorageManager.unregister(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof StorageBlockEntity storage)) {
            return ActionResult.PASS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInventory, p) -> new GenericContainerScreenHandler(
                            ModScreenHandlers.STORAGE_SCREEN_HANDLER, syncId, playerInventory, storage, 3
                    ),
                    Text.translatable("container.settlerscraft.storage")
            ));
        }

        return ActionResult.CONSUME;
    }
}