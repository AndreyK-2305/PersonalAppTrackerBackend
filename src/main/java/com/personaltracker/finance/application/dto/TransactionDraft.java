package com.personaltracker.finance.application.dto;

import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AdjustmentDirection;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.TransactionDetails;
import com.personaltracker.shared.domain.Money;
import java.util.Objects;

/**
 * Datos de un movimiento por registrar o editar, uno por cada tipo de {@code Transaction}. Las
 * categorías son opcionales.
 */
public sealed interface TransactionDraft {

    Money amount();

    TransactionDetails details();

    record IncomeDraft(AccountId destination, CategoryId category, Money amount, TransactionDetails details)
            implements TransactionDraft {

        public IncomeDraft {
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(details, "details");
        }
    }

    record ExpenseDraft(AccountId source, CategoryId category, Money amount, TransactionDetails details)
            implements TransactionDraft {

        public ExpenseDraft {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(details, "details");
        }
    }

    record TransferDraft(AccountId source, AccountId destination, Money amount, TransactionDetails details)
            implements TransactionDraft {

        public TransferDraft {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(details, "details");
        }
    }

    record AdjustmentDraft(
            AccountId account, AdjustmentDirection direction, Money amount, TransactionDetails details)
            implements TransactionDraft {

        public AdjustmentDraft {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(details, "details");
        }
    }
}
