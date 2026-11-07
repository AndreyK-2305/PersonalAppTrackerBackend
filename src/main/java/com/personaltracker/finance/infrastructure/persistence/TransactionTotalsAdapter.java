package com.personaltracker.finance.infrastructure.persistence;

import com.personaltracker.finance.application.dto.DailyTotal;
import com.personaltracker.finance.application.port.out.TransactionTotals;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.DateRange;
import com.personaltracker.finance.domain.model.ReportEffect;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sumas sobre los movimientos calculadas en SQL (ADR-0013). Cada consulta reproduce una regla del
 * dominio, y {@code TransactionTotalsConsistencyTests} comprueba que den lo mismo:
 * <ul>
 *   <li>{@code DAILY_TOTALS}, {@code Transaction.reportEffect()}: solo cuentan ingresos y gastos; el
 *       producto es el destino de un ingreso o el origen de un gasto.
 *   <li>{@code BALANCE_CHANGES}, {@code Transaction.balanceEffects()}: el dinero entra al destino y
 *       sale del origen. Un ajuste usa el destino si aumenta el saldo y el origen si lo disminuye.
 * </ul>
 */
@Component
class TransactionTotalsAdapter implements TransactionTotals {

    private static final String DAILY_TOTALS = """
            select transaction_date,
                   coalesce(destination_account_id, source_account_id) as account_id,
                   category_id,
                   type,
                   currency,
                   sum(amount) as total
            from finance.transactions
            where user_id = :userId
              and type in ('INCOME', 'EXPENSE')
              and transaction_date between :start and :end
            group by transaction_date, coalesce(destination_account_id, source_account_id), category_id, type,
                     currency
            """;

    private static final String BALANCE_CHANGES = """
            select account_id, currency, sum(change) as change
            from (
                select destination_account_id as account_id, currency, amount as change
                from finance.transactions
                where user_id = :userId and destination_account_id is not null
                union all
                select source_account_id as account_id, currency, -amount as change
                from finance.transactions
                where user_id = :userId and source_account_id is not null
            ) effects
            group by account_id, currency
            """;

    private final JdbcClient jdbc;

    TransactionTotalsAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DailyTotal> dailyTotals(UserId owner, DateRange range) {
        return jdbc.sql(DAILY_TOTALS)
                .param("userId", owner.value())
                .param("start", range.start())
                .param("end", range.end())
                .query((row, number) -> new DailyTotal(
                        row.getObject("transaction_date", LocalDate.class),
                        new AccountId(row.getObject("account_id", UUID.class)),
                        categoryId(row.getObject("category_id", UUID.class)),
                        effectOf(TransactionType.valueOf(row.getString("type"))),
                        new Money(row.getBigDecimal("total"), Currency.getInstance(row.getString("currency")))))
                .list();
    }

    @Override
    public Map<AccountId, Money> balanceChanges(UserId owner) {
        Map<AccountId, Money> changes = new HashMap<>();
        jdbc.sql(BALANCE_CHANGES)
                .param("userId", owner.value())
                .query(row -> {
                    changes.put(new AccountId(row.getObject("account_id", UUID.class)),
                            new Money(row.getBigDecimal("change"), Currency.getInstance(row.getString("currency"))));
                });
        return changes;
    }

    private static ReportEffect effectOf(TransactionType type) {
        return switch (type) {
            case INCOME -> ReportEffect.INCOME;
            case EXPENSE -> ReportEffect.EXPENSE;
            case TRANSFER, ADJUSTMENT -> throw new IllegalStateException("La consulta excluye los movimientos neutros");
        };
    }

    private static CategoryId categoryId(UUID value) {
        return value == null ? null : new CategoryId(value);
    }
}
