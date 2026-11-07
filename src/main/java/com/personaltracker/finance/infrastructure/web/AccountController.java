package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.CreateAccountCommand;
import com.personaltracker.finance.application.dto.UpdateAccountCommand;
import com.personaltracker.finance.application.usecase.ArchiveAccount;
import com.personaltracker.finance.application.usecase.CreateAccount;
import com.personaltracker.finance.application.usecase.DeleteAccount;
import com.personaltracker.finance.application.usecase.ListAccounts;
import com.personaltracker.finance.application.usecase.ReactivateAccount;
import com.personaltracker.finance.application.usecase.UpdateAccount;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Productos del usuario (ADR-0014). Un producto ajeno responde 404, igual que uno inexistente. Un
 * producto con movimientos no se borra (409): se archiva.
 */
@RestController
@RequestMapping("/api/v1/finance/accounts")
class AccountController {

    private final ListAccounts listAccounts;
    private final CreateAccount createAccount;
    private final UpdateAccount updateAccount;
    private final ArchiveAccount archiveAccount;
    private final ReactivateAccount reactivateAccount;
    private final DeleteAccount deleteAccount;

    AccountController(
            ListAccounts listAccounts,
            CreateAccount createAccount,
            UpdateAccount updateAccount,
            ArchiveAccount archiveAccount,
            ReactivateAccount reactivateAccount,
            DeleteAccount deleteAccount) {
        this.listAccounts = listAccounts;
        this.createAccount = createAccount;
        this.updateAccount = updateAccount;
        this.archiveAccount = archiveAccount;
        this.reactivateAccount = reactivateAccount;
        this.deleteAccount = deleteAccount;
    }

    @GetMapping
    AccountListResponse list(@CurrentUser UserId owner) {
        return AccountListResponse.from(listAccounts.execute(owner));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse create(@CurrentUser UserId owner, @Valid @RequestBody AccountRequest body) {
        return AccountResponse.from(createAccount.execute(new CreateAccountCommand(owner, body.toDetails())));
    }

    /** Reemplaza los datos editables; el tipo solo puede cambiar entre cuentas de depósito. */
    @PutMapping("/{id}")
    AccountResponse update(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody AccountRequest body) {
        return AccountResponse.from(
                updateAccount.execute(new UpdateAccountCommand(owner, new AccountId(id), body.toDetails())));
    }

    @PostMapping("/{id}/archive")
    AccountResponse archive(@CurrentUser UserId owner, @PathVariable UUID id) {
        return AccountResponse.from(archiveAccount.execute(owner, new AccountId(id)));
    }

    @PostMapping("/{id}/reactivate")
    AccountResponse reactivate(@CurrentUser UserId owner, @PathVariable UUID id) {
        return AccountResponse.from(reactivateAccount.execute(owner, new AccountId(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UserId owner, @PathVariable UUID id) {
        deleteAccount.execute(owner, new AccountId(id));
    }
}
