package com.nxtlinea.settlerscraft.entity;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<SettlerEntity> SETTLER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Settlerscraft.MOD_ID, "settler"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, SettlerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build()
    );

    public static void register() {
        Settlerscraft.LOGGER.info("Registering entities for " + Settlerscraft.MOD_ID);
    }
}