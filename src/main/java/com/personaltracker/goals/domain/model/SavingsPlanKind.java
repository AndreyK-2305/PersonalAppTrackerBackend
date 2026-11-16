package com.personaltracker.goals.domain.model;

/** Tipo de plan de una meta de ahorro; es el valor que viaja en la API y se guarda en la base. */
public enum SavingsPlanKind {
    /** Llegar al objetivo en una fecha: la app calcula el aporte mensual. */
    TARGET_DATE,
    /** Apartar un valor al mes: la app estima la fecha. */
    MONTHLY_AMOUNT
}
