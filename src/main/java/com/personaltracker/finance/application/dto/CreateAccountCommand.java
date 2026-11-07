package com.personaltracker.finance.application.dto;

import com.personaltracker.shared.domain.UserId;

public record CreateAccountCommand(UserId owner, AccountDetails details) {}
