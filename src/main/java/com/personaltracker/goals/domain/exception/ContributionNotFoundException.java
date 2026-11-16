package com.personaltracker.goals.domain.exception;

import com.personaltracker.shared.domain.NotFoundException;

/** El abono no existe, es de otra meta o pertenece a otro usuario. */
public class ContributionNotFoundException extends NotFoundException {

    public ContributionNotFoundException() {
        super("goals.contribution.not_found", "El abono no existe.");
    }
}
