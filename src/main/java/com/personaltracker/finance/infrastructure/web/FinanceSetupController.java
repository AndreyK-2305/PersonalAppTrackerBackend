package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.FinanceStatus;
import com.personaltracker.finance.application.dto.SetUpFinanceCommand;
import com.personaltracker.finance.application.usecase.GetFinanceStatus;
import com.personaltracker.finance.application.usecase.SetUpFinance;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bienvenida de Finanzas (ADR-0014). El frontend consulta el estado al entrar y, si falta, ofrece
 * crear el producto "Otro" y el catálogo de categorías sugerido. Repetir el POST no duplica nada.
 */
@RestController
@RequestMapping("/api/v1/finance/setup")
class FinanceSetupController {

    private final GetFinanceStatus getFinanceStatus;
    private final SetUpFinance setUpFinance;

    FinanceSetupController(GetFinanceStatus getFinanceStatus, SetUpFinance setUpFinance) {
        this.getFinanceStatus = getFinanceStatus;
        this.setUpFinance = setUpFinance;
    }

    @GetMapping
    FinanceStatusResponse status(@CurrentUser UserId owner) {
        return FinanceStatusResponse.from(getFinanceStatus.execute(owner));
    }

    @PostMapping
    FinanceStatusResponse setUp(@CurrentUser UserId owner, @Valid @RequestBody SetUpRequest body) {
        return FinanceStatusResponse.from(
                setUpFinance.execute(new SetUpFinanceCommand(owner, body.withSuggestedCategories())));
    }

    record SetUpRequest(@NotNull Boolean withSuggestedCategories) {}

    @Schema(requiredProperties = "setUp")
    record FinanceStatusResponse(boolean setUp) {

        static FinanceStatusResponse from(FinanceStatus status) {
            return new FinanceStatusResponse(status.setUp());
        }
    }
}
