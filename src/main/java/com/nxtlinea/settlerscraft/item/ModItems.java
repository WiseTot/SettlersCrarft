package com.nxtlinea.settlerscraft.item;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item SURVEYOR_WAND = Registry.register(
            Registries.ITEM,
            new Identifier(Settlerscraft.MOD_ID, "surveyor_wand"),
            new SurveyorWandItem(new Item.Settings().maxCount(1))
    );

    public static void register() {
        Settlerscraft.LOGGER.info("Registering items for " + Settlerscraft.MOD_ID);
    }
}