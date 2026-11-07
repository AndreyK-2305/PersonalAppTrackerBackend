package com.personaltracker.finance.application.port.out;

import com.personaltracker.finance.application.dto.DailyTotal;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.util.List;
import java.util.Map;

/**
 * Sumas sobre los movimientos, calculadas en la base de datos por rendimiento. Deben coincidir con
 * {@code Transaction.reportEffect()} y {@code Transaction.balanceEffects()} (ADR-0013).
 */
public interface TransactionTotals {

    /** Ingresos y gastos del rango, agrupados por día, producto y categoría. Excluye lo neutro. */
    List<DailyTotal> dailyTotals(UserId owner, DateRange range);

    /** Cambio acumulado del saldo de cada producto con movimientos; el saldo es el inicial más este cambio. */
    Map<AccountId, Money> balanceChanges(UserId owner);
}
