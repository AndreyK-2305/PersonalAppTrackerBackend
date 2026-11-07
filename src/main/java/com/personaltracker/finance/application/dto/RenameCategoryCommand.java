package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.shared.domain.UserId;

public record RenameCategoryCommand(UserId owner, CategoryId category, String name) {}
