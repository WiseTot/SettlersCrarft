package com.nxtlinea.settlerscraft.entity;

/**
 * Состояния конечного автомата (FSM) поселенца.
 * IDLE      — житель ничего не делает, решает куда идти дальше
 * WALKING   — житель движется к выбранной точке (случайной, к складу или к точке застройки)
 * GATHERING — житель на складе, забирает нужные материалы
 * BUILDING  — житель на стройке, ставит блок
 * WAITING   — техническая пауза после действия, перед следующим решением
 */
public enum SettlerState {
    IDLE,
    WALKING,
    GATHERING,
    BUILDING,
    WAITING
}