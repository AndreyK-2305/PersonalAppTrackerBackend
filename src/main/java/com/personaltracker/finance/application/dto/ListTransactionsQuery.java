package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;

/**
 * Filtros del listado de movimientos; un filtro nulo no se aplica. Filtrar por una categoría principal
 * incluye sus subcategorías. {@code uncategorized} deja solo los ingresos y gastos sin categoría.
 */
public record ListTransactionsQuery(
        UserId owner,
        LocalDate from,
        LocalDate to,
        AccountId account,
        CategoryId category,
        TransactionType type,
        String text,
        TransactionSort sort,
        PageQuery page,
        boolean uncategorized) {

    public ListTransactionsQuery(
            UserId owner,
            LocalDate from,
            LocalDate to,
            AccountId account,
            CategoryId category,
            TransactionType type,
            String text,
            TransactionSort sort,
            PageQuery page) {
        this(owner, from, to, account, category, type, text, sort, page, false);
    }
}
