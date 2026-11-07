package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.usecase.GetPeriodSummary;
import com.personaltracker.finance.domain.model.PeriodKind;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resumen por periodo para el dashboard (ADR-0013). El dashboard lo combina con
 * {@code GET /api/v1/finance/accounts} (saldos y patrimonio) y {@code GET /api/v1/finance/transactions}
 * (últimos movimientos).
 */
@RestController
@RequestMapping("/api/v1/finance/summary")
class SummaryController {

    private final GetPeriodSummary getPeriodSummary;

    SummaryController(GetPeriodSummary getPeriodSummary) {
        this.getPeriodSummary = getPeriodSummary;
    }

    /**
     * {@code date} elige el periodo que la contiene; si se omite, se usa hoy en la zona horaria del
     * perfil. Las semanas van de lunes a domingo.
     */
    @GetMapping
    SummaryResponse get(
            @CurrentUser UserId owner,
            @RequestParam(defaultValue = "MONTH") PeriodKind period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return SummaryResponse.from(getPeriodSummary.execute(owner, period, date));
    }
}
