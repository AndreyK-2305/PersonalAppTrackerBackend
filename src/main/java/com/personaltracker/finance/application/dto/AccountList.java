package com.personaltracker.finance.application.dto;

import com.personaltracker.shared.domain.Money;
import java.util.List;

/**
 * Productos del usuario y su patrimonio: la suma de los saldos de los productos que llevan saldo,
 * incluidos los archivados (ADR-0013).
 */
public record AccountList(List<AccountView> accounts, Money netWorth) {

    public AccountList {
        accounts = List.copyOf(accounts);
    }
}
