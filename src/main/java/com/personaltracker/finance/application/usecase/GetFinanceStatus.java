package com.personaltracker.finance.application.usecase;

import com.personaltracker.finance.application.dto.FinanceStatus;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indica si el usuario ya completó la bienvenida de Finanzas, es decir, si tiene el producto "Otro". */
@Service
public class GetFinanceStatus {

    private final AccountRepository accounts;

    public GetFinanceStatus(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public FinanceStatus execute(UserId owner) {
        return new FinanceStatus(accounts.findOtherAccount(owner).isPresent());
    }
}
