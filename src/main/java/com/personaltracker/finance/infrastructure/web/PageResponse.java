package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

/** Página de resultados: {@code page} empieza en 0 (ver la paginación en docs/api/conventions.md). */
@Schema(requiredProperties = {"items", "page", "size", "totalItems", "totalPages"})
record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(
                result.items().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages());
    }
}
