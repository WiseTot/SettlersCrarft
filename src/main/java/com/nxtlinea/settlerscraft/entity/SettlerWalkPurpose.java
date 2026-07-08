package com.nxtlinea.settlerscraft.entity;

/**
 * Причина, по которой житель сейчас идёт (используется в состоянии WALKING),
 * чтобы после прибытия FSM знал, что делать дальше.
 */
public enum SettlerWalkPurpose {
    WANDER,
    TO_STORAGE,
    TO_SITE
}