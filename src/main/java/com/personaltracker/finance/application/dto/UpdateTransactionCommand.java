package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.shared.domain.UserId;

/** Reemplaza todos los datos del movimiento. El borrador debe ser del mismo tipo (ADR-0015). */
public record UpdateTransactionCommand(UserId owner, TransactionId transaction, TransactionDraft draft) {}
