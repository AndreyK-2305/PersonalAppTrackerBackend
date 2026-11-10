package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.Set;

/**
 * Criterios de búsqueda para el puerto de persistencia; un criterio nulo no filtra. {@code categories}
 * ya incluye las subcategorías de la categoría pedida. {@code text} busca en la descripción y el comercio.
 * {@code uncategorized} deja solo los ingresos y gastos sin categoría ("Por clasificar", ADR-0017).
 */
public record TransactionSearch(
        LocalDate from,
        LocalDate to,
        AccountId account,
        Set<CategoryId> categories,
        TransactionType type,
        String text,
        boolean uncategorized) {

    public TransactionSearch {
        categories = categories == null ? null : Set.copyOf(categories);
    }

    public TransactionSearch(
            LocalDate from, LocalDate to, AccountId account, Set<CategoryId> categories, TransactionType type,
            String text) {
        this(from, to, account, categories, type, text, false);
    }
}
