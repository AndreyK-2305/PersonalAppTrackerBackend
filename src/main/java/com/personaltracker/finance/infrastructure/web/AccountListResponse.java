package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.AccountList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Productos ordenados (activos, archivados y "Otro" al final) y patrimonio en {@code currency}. */
@Schema(requiredProperties = {"accounts", "netWorth", "currency"})
record AccountListResponse(List<AccountResponse> accounts, String netWorth, String currency) {

    static AccountListResponse from(AccountList list) {
        return new AccountListResponse(
                list.accounts().stream().map(AccountResponse::from).toList(),
                Amounts.format(list.netWorth()),
                list.netWorth().currency().getCurrencyCode());
    }
}
