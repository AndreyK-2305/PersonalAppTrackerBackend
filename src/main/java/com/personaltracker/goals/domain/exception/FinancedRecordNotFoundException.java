package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** El registro no existe, es de otra meta o pertenece a otro usuario. */
public class FinancedRecordNotFoundException extends NotFoundException {

    public FinancedRecordNotFoundException() {
        super("goals.financed_record.not_found", "El registro no existe.");
    }
}
