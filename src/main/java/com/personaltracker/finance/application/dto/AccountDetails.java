package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.shared.domain.Money;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Datos editables de un producto, uno por cada tipo que el usuario puede crear. El producto "Otro" no
 * tiene datos editables. {@code institution} y {@code lastFourDigits} llegan como texto y el caso de
 * uso los valida; los últimos dígitos son opcionales.
 */
public sealed interface AccountDetails {

    record Cash(String name, Money openingBalance) implements AccountDetails {

        public Cash {
            Objects.requireNonNull(openingBalance, "openingBalance");
        }
    }

    /** {@code kind} es {@code SAVINGS}, {@code CHECKING} o {@code DIGITAL_WALLET}. */
    record Deposit(AccountKind kind, String name, String institution, String lastFourDigits, Money openingBalance)
            implements AccountDetails {

        public Deposit {
            if (!EnumSet.of(AccountKind.SAVINGS, AccountKind.CHECKING, AccountKind.DIGITAL_WALLET).contains(kind)) {
                throw new IllegalArgumentException("No es un tipo de cuenta de depósito: " + kind);
            }
            Objects.requireNonNull(openingBalance, "openingBalance");
        }
    }

    record CreditCard(String name, String institution, String lastFourDigits, Money creditLimit, Money initialDebt)
            implements AccountDetails {

        public CreditCard {
            Objects.requireNonNull(creditLimit, "creditLimit");
            Objects.requireNonNull(initialDebt, "initialDebt");
        }
    }
}
