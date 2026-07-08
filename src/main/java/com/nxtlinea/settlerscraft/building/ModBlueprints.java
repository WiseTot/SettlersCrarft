package com.nxtlinea.settlerscraft.building;

public class ModBlueprints {

    public static BlueprintData STARTER_HOUSE;

    public static void register() {
        STARTER_HOUSE = BlueprintData.loadFromResource("data/settlerscraft/structures/starter_house.nbt");
    }
}