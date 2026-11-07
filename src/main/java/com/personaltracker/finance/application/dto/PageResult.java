package com.personaltracker.finance.application.dto;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados y el total de elementos que cumplen el filtro. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalItems + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems);
    }
}
