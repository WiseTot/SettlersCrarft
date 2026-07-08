package com.nxtlinea.settlerscraft.block;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static final BlockEntityType<StorageBlockEntity> STORAGE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Settlerscraft.MOD_ID, "storage_block"),
            FabricBlockEntityTypeBuilder.create(StorageBlockEntity::new, ModBlocks.STORAGE_BLOCK).build(null)
    );

    public static void register() {
        Settlerscraft.LOGGER.info("Registering block entities for " + Settlerscraft.MOD_ID);
    }
}