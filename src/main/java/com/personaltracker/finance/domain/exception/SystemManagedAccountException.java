package com.personaltracker.finance.domain.exception;

import com.personaltracker.shared.domain.DomainException;

/** Se intentó editar, archivar o borrar el producto «Otro» (ADR-0014). */
public class SystemManagedAccountException extends DomainException {

    public SystemManagedAccountException() {
        super("finance.account.system_managed", "El producto «Otro» lo administra el sistema y no se puede modificar.");
    }
}
