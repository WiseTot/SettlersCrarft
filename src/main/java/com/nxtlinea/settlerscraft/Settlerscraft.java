package com.nxtlinea.settlerscraft;

import com.nxtlinea.settlerscraft.block.ModBlockEntities;
import com.nxtlinea.settlerscraft.block.ModBlocks;
import com.nxtlinea.settlerscraft.building.ModBlueprints;
import com.nxtlinea.settlerscraft.entity.ModEntities;
import com.nxtlinea.settlerscraft.entity.SettlerEntity;
import com.nxtlinea.settlerscraft.item.ModItems;
import com.nxtlinea.settlerscraft.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Settlerscraft implements ModInitializer {
    public static final String MOD_ID = "settlerscraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SettlersCraft");

        ModEntities.register();
        ModItems.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModScreenHandlers.register();
        FabricDefaultAttributeRegistry.register(ModEntities.SETTLER, SettlerEntity.createSettlerAttributes());

        try {
            ModBlueprints.register();
            LOGGER.info("Loaded blueprint 'starter_house' with " + ModBlueprints.STARTER_HOUSE.getBlockCount() + " blocks");
        } catch (Exception e) {
            LOGGER.warn("Не удалось загрузить blueprint 'starter_house.nbt'. Убедись, что файл лежит в src/main/resources/data/settlerscraft/structures/starter_house.nbt", e);
        }
    }
}