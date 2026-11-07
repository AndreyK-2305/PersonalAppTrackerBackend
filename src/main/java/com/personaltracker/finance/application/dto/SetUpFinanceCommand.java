package com.personaltracker.finance.application.dto;

import com.personaltracker.shared.domain.UserId;

/** Bienvenida de Finanzas: {@code withSuggestedCategories} indica si el usuario acepta el catálogo sugerido. */
public record SetUpFinanceCommand(UserId owner, boolean withSuggestedCategories) {}
