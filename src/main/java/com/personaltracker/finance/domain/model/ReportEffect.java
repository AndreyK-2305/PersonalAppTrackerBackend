package com.personaltracker.finance.domain.model;

/** Cómo cuenta un movimiento en los reportes de ingresos y gastos. */
public enum ReportEffect {
    INCOME,
    EXPENSE,
    /** Transferencias y ajustes: mueven saldos, pero no son ingreso ni gasto. */
    NEUTRAL
}
