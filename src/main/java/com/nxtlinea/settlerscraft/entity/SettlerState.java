package com.nxtlinea.settlerscraft.entity;

/**
 * Состояния конечного автомата (FSM) поселенца.
 * IDLE     — житель ничего не делает, решает куда идти дальше
 * WALKING  — житель движется к выбранной точке (случайной или к точке застройки)
 * BUILDING — житель на месте, ставит блок
 * WAITING  — техническая пауза после действия, перед следующим решением
 */
public enum SettlerState {
    IDLE,
    WALKING,
    BUILDING,
    WAITING
}