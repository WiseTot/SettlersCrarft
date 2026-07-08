package com.nxtlinea.settlerscraft.screen;

import com.nxtlinea.settlerscraft.Settlerscraft;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {

    public static ScreenHandlerType<GenericContainerScreenHandler> STORAGE_SCREEN_HANDLER;

    public static void register() {
        STORAGE_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(Settlerscraft.MOD_ID, "storage"),
                new ScreenHandlerType<>((syncId, playerInventory) -> new GenericContainerScreenHandler(
                        STORAGE_SCREEN_HANDLER, syncId, playerInventory, new SimpleInventory(27), 3
                ))
        );

        Settlerscraft.LOGGER.info("Registering screen handlers for " + Settlerscraft.MOD_ID);
    }
}