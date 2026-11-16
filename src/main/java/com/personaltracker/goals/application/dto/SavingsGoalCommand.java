package com.personaltracker.goals.application.dto;

import com.personaltracker.goals.domain.model.SavingsPlan;
import com.personaltracker.shared.domain.Money;

/** Datos para crear o editar una meta de ahorro. */
public record SavingsGoalCommand(String name, Money target, SavingsPlan plan) {}
