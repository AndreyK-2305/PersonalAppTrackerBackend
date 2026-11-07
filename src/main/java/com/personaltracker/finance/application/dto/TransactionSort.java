package com.personaltracker.finance.application.dto;

/** Orden de los listados de movimientos. A igual valor, los más recientes primero. */
public enum TransactionSort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC
}
