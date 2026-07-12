package com.nxtlinea.settlerscraft.entity;

import com.nxtlinea.settlerscraft.entity.profession.LumberjackBehavior;
import com.nxtlinea.settlerscraft.entity.profession.MinerBehavior;
import com.nxtlinea.settlerscraft.entity.profession.ProfessionBehavior;

public enum Profession {
    NONE(null, null),
    LUMBERJACK(new LumberjackBehavior(), "Lumber"),
    MINER(new MinerBehavior(), "Miner");

    private final ProfessionBehavior behavior;
    private final String displayName;

    Profession(ProfessionBehavior behavior, String displayName) {
        this.behavior = behavior;
        this.displayName = displayName;
    }

    public ProfessionBehavior getBehavior() {
        return behavior;
    }

    public String getDisplayName() {
        return displayName;
    }
}