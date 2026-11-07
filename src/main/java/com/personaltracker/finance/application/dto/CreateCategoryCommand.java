package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.CategoryKind;
import com.personaltracker.shared.domain.UserId;

/** {@code parent} es nulo para crear una categoría principal; si no, debe ser del mismo tipo. */
public record CreateCategoryCommand(UserId owner, CategoryKind kind, String name, CategoryId parent) {}
