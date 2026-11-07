package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.ReportEffect;
import com.personaltracker.shared.domain.Money;
import java.time.LocalDate;

/**
 * Suma de los ingresos o de los gastos de un día para un producto y una categoría ({@code category}
 * es nula para los movimientos sin categoría). {@code effect} nunca es {@code NEUTRAL}.
 */
public record DailyTotal(LocalDate date, AccountId account, CategoryId category, ReportEffect effect, Money total) {}
