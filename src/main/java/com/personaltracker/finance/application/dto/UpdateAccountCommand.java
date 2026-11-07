package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.shared.domain.UserId;

/** Reemplaza todos los datos editables del producto. El tipo de producto no cambia. */
public record UpdateAccountCommand(UserId owner, AccountId account, AccountDetails details) {}
