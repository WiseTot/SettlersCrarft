package com.nxtlinea.settlerscraft.block;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block STORAGE_BLOCK = Registry.register(
            Registries.BLOCK,
            new Identifier(Settlerscraft.MOD_ID, "storage_block"),
            new StorageBlock(FabricBlockSettings.copyOf(Blocks.CHEST))
    );

    public static final Item STORAGE_BLOCK_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(Settlerscraft.MOD_ID, "storage_block"),
            new BlockItem(STORAGE_BLOCK, new Item.Settings())
    );

    public static void register() {
        Settlerscraft.LOGGER.info("Registering blocks for " + Settlerscraft.MOD_ID);
    }
}