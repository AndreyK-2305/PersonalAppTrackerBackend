package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.Set;

/**
 * Criterios de búsqueda para el puerto de persistencia; un criterio nulo no filtra. {@code categories}
 * ya incluye las subcategorías de la categoría pedida. {@code text} busca en la descripción y el comercio.
 */
public record TransactionSearch(
        LocalDate from,
        LocalDate to,
        AccountId account,
        Set<CategoryId> categories,
        TransactionType type,
        String text) {

    public TransactionSearch {
        categories = categories == null ? null : Set.copyOf(categories);
    }
}
