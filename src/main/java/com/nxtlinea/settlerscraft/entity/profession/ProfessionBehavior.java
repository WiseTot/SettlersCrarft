package com.nxtlinea.settlerscraft.entity.profession;

import com.nxtlinea.settlerscraft.entity.SettlerEntity;

public interface ProfessionBehavior {

    boolean tryStartWork(SettlerEntity settler);

    void performWorkAction(SettlerEntity settler);
}