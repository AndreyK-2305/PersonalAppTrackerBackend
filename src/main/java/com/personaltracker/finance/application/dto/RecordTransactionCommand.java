package com.personaltracker.finance.application.dto;

import com.personaltracker.shared.domain.UserId;

public record RecordTransactionCommand(UserId owner, TransactionDraft draft) {}
