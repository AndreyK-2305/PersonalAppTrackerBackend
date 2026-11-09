package com.personaltracker.capture.domain.parsing;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.ParsedMovement;
import com.personaltracker.capture.domain.model.ProductHint;
import com.personaltracker.shared.domain.InvalidMoneyAmountException;
import com.personaltracker.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Construye un {@link ParsedMovement} a partir de los grupos de una plantilla. Cualquier grupo
 * inválido (monto con otro formato, monto cero o fecha imposible) produce vacío.
 */
final class Extraction {

    static final String AMOUNT = "(?<amount>[0-9][0-9.,]*)";
    static final String DATE = "(?<date>" + BankDateTime.DATE + ")";
    static final String TIME = "(?<time>" + BankDateTime.TIME + ")";
    static final String LAST_FOUR = "(?<last>[0-9]{4})";

    static final Currency COP = Currency.getInstance("COP");

    private final String templateId;
    private final MovementDirection direction;
    private final CaptureChannel channel;
    private Optional<Money> amount = Optional.empty();
    private Optional<LocalDateTime> occurredAt = Optional.empty();
    private boolean invalid;
    private ProductHint product;
    private String counterparty = "";
    private Optional<String> destinationKey = Optional.empty();
    private Optional<String> recipientName = Optional.empty();

    private Extraction(String templateId, MovementDirection direction, CaptureChannel channel) {
        this.templateId = templateId;
        this.direction = direction;
        this.channel = channel;
    }

    static Extraction of(String templateId, MovementDirection direction, CaptureChannel channel) {
        return new Extraction(templateId, direction, channel);
    }

    Extraction amount(Matcher matcher, AmountFormat format, Currency currency) {
        amount = format.parse(matcher.group("amount")).flatMap(number -> toMoney(number, currency));
        invalid |= amount.isEmpty();
        return this;
    }

    /** Fecha y hora de los grupos {@code date} y {@code time}. */
    Extraction occurredAt(Matcher matcher) {
        occurredAt = BankDateTime.parse(matcher.group("date"), matcher.group("time"));
        invalid |= occurredAt.isEmpty();
        return this;
    }

    Extraction product(ProductHint hint) {
        product = hint;
        return this;
    }

    Extraction counterparty(String value) {
        counterparty = NotificationText.collapseSpaces(value);
        return this;
    }

    Extraction destinationKey(String value) {
        destinationKey = Optional.of(value);
        return this;
    }

    Extraction recipientName(String value) {
        recipientName = Optional.of(NotificationText.collapseSpaces(value));
        return this;
    }

    Optional<ParsedMovement> build() {
        if (invalid || amount.isEmpty() || product == null) {
            return Optional.empty();
        }
        return Optional.of(new ParsedMovement(templateId, direction, product, amount.get(), occurredAt,
                counterparty, channel, destinationKey, recipientName));
    }

    private static Optional<Money> toMoney(BigDecimal number, Currency currency) {
        if (number.signum() <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Money(number, currency));
        } catch (InvalidMoneyAmountException tooLarge) {
            return Optional.empty();
        }
    }
}
