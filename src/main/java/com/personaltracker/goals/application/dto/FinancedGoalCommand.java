package com.personaltracker.goals.application.dto;

import com.personaltracker.goals.domain.model.FinancedTerms;

/** Datos para crear o editar una meta financiada. */
public record FinancedGoalCommand(String name, FinancedTerms terms) {}
