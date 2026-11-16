package com.personaltracker.goals.infrastructure.web;

import com.personaltracker.goals.application.dto.SavingsGoalCommand;
import com.personaltracker.goals.application.usecase.AdjustSavingsRate;
import com.personaltracker.goals.application.usecase.ManageSavingsGoals;
import com.personaltracker.goals.application.usecase.RecordSavingsContributions;
import com.personaltracker.goals.domain.model.SavingsGoal;
import com.personaltracker.goals.domain.model.SavingsGoalId;
import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.goals.domain.model.SavingsPlanKind;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.infrastructure.web.ApiFormats;
import com.personaltracker.shared.infrastructure.web.GlobalExceptionHandler.FieldViolation;
import com.personaltracker.shared.infrastructure.web.RequestValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Metas de ahorro (ADR-0018). Los montos son cadenas decimales en COP y las tasas, porcentajes EA
 * como cadenas (por ejemplo {@code "10.5"}). Cada respuesta trae la proyección calculada para hoy.
 */
@RestController
@RequestMapping("/api/v1/goals/savings")
class SavingsGoalController {

    private static final String AMOUNT = ApiFormats.AMOUNT;
    private static final String AMOUNT_MESSAGE = ApiFormats.AMOUNT_MESSAGE;
    private static final String PERCENT = ApiFormats.PERCENT;
    private static final String PERCENT_MESSAGE = ApiFormats.PERCENT_MESSAGE;

    private final ManageSavingsGoals manageGoals;
    private final RecordSavingsContributions contributions;
    private final AdjustSavingsRate adjustRate;

    SavingsGoalController(ManageSavingsGoals manageGoals, RecordSavingsContributions contributions,
            AdjustSavingsRate adjustRate) {
        this.manageGoals = manageGoals;
        this.contributions = contributions;
        this.adjustRate = adjustRate;
    }

    @GetMapping
    List<SavingsGoalResponse> list(@CurrentUser UserId owner) {
        return manageGoals.list(owner).stream().map(SavingsGoalResponse::from).toList();
    }

    @GetMapping("/{id}")
    SavingsGoalResponse get(@CurrentUser UserId owner, @PathVariable UUID id) {
        return SavingsGoalResponse.from(manageGoals.get(owner, new SavingsGoalId(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavingsGoalResponse create(@CurrentUser UserId owner, @Valid @RequestBody SavingsGoalRequest body) {
        return SavingsGoalResponse.from(manageGoals.create(owner, body.toCommand()));
    }

    @PutMapping("/{id}")
    SavingsGoalResponse update(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody SavingsGoalRequest body) {
        return SavingsGoalResponse.from(manageGoals.update(owner, new SavingsGoalId(id), body.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UserId owner, @PathVariable UUID id) {
        manageGoals.delete(owner, new SavingsGoalId(id));
    }

    @PostMapping("/{id}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    SavingsGoalResponse addContribution(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody ContributionRequest body) {
        return SavingsGoalResponse.from(
                contributions.add(owner, new SavingsGoalId(id), body.date(), cop(body.amount())));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    SavingsGoalResponse removeContribution(
            @CurrentUser UserId owner, @PathVariable UUID id, @PathVariable UUID contributionId) {
        return SavingsGoalResponse.from(contributions.remove(owner, new SavingsGoalId(id), contributionId));
    }

    /** Tasa EA en porcentaje que rige desde mañana; {@code "0"} apaga la rentabilidad. */
    @PutMapping("/{id}/rate")
    SavingsGoalResponse adjustRate(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody RateRequest body) {
        return SavingsGoalResponse.from(
                adjustRate.adjust(owner, new SavingsGoalId(id), GoalAmounts.rate(body.ratePercent())));
    }

    private static Money cop(String value) {
        return GoalAmounts.cop(value);
    }

    /** {@code plan} decide el campo obligatorio: {@code targetDate} o {@code monthlyAmount}; el otro no aplica. */
    record SavingsGoalRequest(
            @NotBlank @Size(max = SavingsGoal.MAX_NAME_LENGTH) String name,
            @Schema(example = "2500000.00") @NotBlank @Pattern(regexp = AMOUNT, message = AMOUNT_MESSAGE)
                    String targetAmount,
            @NotNull SavingsPlanKind plan,
            LocalDate targetDate,
            @Schema(example = "200000.00") @Pattern(regexp = AMOUNT, message = AMOUNT_MESSAGE) String monthlyAmount) {

        SavingsGoalCommand toCommand() {
            List<FieldViolation> violations = new ArrayList<>();
            boolean hasMonthly = monthlyAmount != null && !monthlyAmount.isBlank();
            switch (plan) {
                case TARGET_DATE -> {
                    if (targetDate == null) {
                        violations.add(new FieldViolation("targetDate", "es obligatorio para este plan"));
                    }
                    if (hasMonthly) {
                        violations.add(new FieldViolation("monthlyAmount", "no aplica a este plan"));
                    }
                }
                case MONTHLY_AMOUNT -> {
                    if (!hasMonthly) {
                        violations.add(new FieldViolation("monthlyAmount", "es obligatorio para este plan"));
                    }
                    if (targetDate != null) {
                        violations.add(new FieldViolation("targetDate", "no aplica a este plan"));
                    }
                }
            }
            if (!violations.isEmpty()) {
                throw new RequestValidationException(violations);
            }
            SavingsPlan savingsPlan = switch (plan) {
                case TARGET_DATE -> new SavingsPlan.ByTargetDate(targetDate);
                case MONTHLY_AMOUNT -> new SavingsPlan.ByMonthlyAmount(cop(monthlyAmount));
            };
            return new SavingsGoalCommand(name, cop(targetAmount), savingsPlan);
        }
    }

    record ContributionRequest(
            @NotNull LocalDate date,
            @Schema(example = "150000.00") @NotBlank @Pattern(regexp = AMOUNT, message = AMOUNT_MESSAGE)
                    String amount) {}

    record RateRequest(
            @Schema(example = "10.5") @NotBlank @Pattern(regexp = PERCENT, message = PERCENT_MESSAGE)
                    String ratePercent) {}
}
