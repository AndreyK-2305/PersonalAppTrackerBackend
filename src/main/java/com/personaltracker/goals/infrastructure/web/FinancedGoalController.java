package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.FinancedGoalCommand;
import com.personaltracker.goals.application.usecase.ManageFinancedGoals;
import com.personaltracker.goals.application.usecase.SimulateFinancing;
import com.personaltracker.goals.domain.model.FinancedGoal;
import com.personaltracker.goals.domain.model.FinancedGoalId;
import com.personaltracker.goals.domain.model.FinancedTerms;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.infrastructure.web.ApiFormats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Metas financiadas (ADR-0018): simulador y plan de pagos. Los montos son cadenas decimales en COP y
 * la tasa, un porcentaje EA como cadena. El listado trae el resumen de cada plan; la tabla de
 * amortización completa viene en el detalle y en la simulación.
 */
@RestController
@RequestMapping("/api/v1/goals/financed")
class FinancedGoalController {

    private final ManageFinancedGoals manageGoals;
    private final SimulateFinancing simulateFinancing;

    FinancedGoalController(ManageFinancedGoals manageGoals, SimulateFinancing simulateFinancing) {
        this.manageGoals = manageGoals;
        this.simulateFinancing = simulateFinancing;
    }

    @GetMapping
    List<FinancedGoalResponse> list(@CurrentUser UserId owner) {
        return manageGoals.list(owner).stream().map(FinancedGoalResponse::from).toList();
    }

    @GetMapping("/{id}")
    FinancedGoalDetailResponse get(@CurrentUser UserId owner, @PathVariable UUID id) {
        return FinancedGoalDetailResponse.from(manageGoals.get(owner, new FinancedGoalId(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FinancedGoalDetailResponse create(@CurrentUser UserId owner, @Valid @RequestBody FinancedGoalRequest body) {
        return FinancedGoalDetailResponse.from(manageGoals.create(owner, body.toCommand()));
    }

    @PutMapping("/{id}")
    FinancedGoalDetailResponse update(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody FinancedGoalRequest body) {
        return FinancedGoalDetailResponse.from(manageGoals.update(owner, new FinancedGoalId(id), body.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UserId owner, @PathVariable UUID id) {
        manageGoals.delete(owner, new FinancedGoalId(id));
    }

    /** Calcula el plan de pagos de unas condiciones sin guardar nada: la vista previa del formulario. */
    @PostMapping("/simulations")
    SimulationResponse simulate(@CurrentUser UserId owner, @Valid @RequestBody TermsRequest body) {
        return SimulationResponse.from(simulateFinancing.simulate(owner, body.toTerms()));
    }

    /**
     * Condiciones de la financiación. {@code downPayment} y {@code actualInstallment} son opcionales:
     * sin cuota inicial se financia todo el valor, y sin cuota real se usa la calculada.
     */
    record TermsRequest(
            @Schema(example = "60000000.00") @NotBlank
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String assetValue,
            @Schema(example = "10000000.00")
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String downPayment,
            @Schema(example = "18.5") @NotBlank
                    @Pattern(regexp = ApiFormats.PERCENT, message = ApiFormats.PERCENT_MESSAGE) String ratePercent,
            @NotNull @Min(1) @Max(FinancedTerms.MAX_INSTALLMENTS) Integer installments,
            @NotNull LocalDate firstDueDate,
            @Schema(example = "1450000.00")
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String actualInstallment) {

        FinancedTerms toTerms() {
            return new FinancedTerms(GoalAmounts.cop(assetValue), GoalAmounts.copOrZero(downPayment),
                    GoalAmounts.rate(ratePercent), installments, firstDueDate, GoalAmounts.copOrNull(actualInstallment));
        }
    }

    /** Los mismos campos de {@link TermsRequest} más el nombre de la meta. */
    record FinancedGoalRequest(
            @NotBlank @Size(max = FinancedGoal.MAX_NAME_LENGTH) String name,
            @Schema(example = "60000000.00") @NotBlank
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String assetValue,
            @Schema(example = "10000000.00")
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String downPayment,
            @Schema(example = "18.5") @NotBlank
                    @Pattern(regexp = ApiFormats.PERCENT, message = ApiFormats.PERCENT_MESSAGE) String ratePercent,
            @NotNull @Min(1) @Max(FinancedTerms.MAX_INSTALLMENTS) Integer installments,
            @NotNull LocalDate firstDueDate,
            @Schema(example = "1450000.00")
                    @Pattern(regexp = ApiFormats.AMOUNT, message = ApiFormats.AMOUNT_MESSAGE) String actualInstallment) {

        FinancedGoalCommand toCommand() {
            return new FinancedGoalCommand(name,
                    new TermsRequest(assetValue, downPayment, ratePercent, installments, firstDueDate, actualInstallment)
                            .toTerms());
        }
    }
}
