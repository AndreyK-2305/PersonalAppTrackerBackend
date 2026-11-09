package com.personaltracker.capture.domain.parsing;

import static com.personaltracker.capture.domain.parsing.Extraction.AMOUNT;
import static com.personaltracker.capture.domain.parsing.Extraction.COP;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.CaptureInstitution;
import com.personaltracker.capture.domain.model.CaptureSource;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ProductHint;
import com.personaltracker.capture.domain.model.ProductType;
import com.personaltracker.capture.domain.model.RawNotification;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Notificaciones de la app de Rappi. El tipo de aviso está en el título ("RappiCard | Compra
 * exitosa") y el detalle en el texto, que no trae fecha: se usa la hora de recepción.
 */
public final class RappiPushParser implements NotificationParser {

    public static final String CARD_PURCHASE = "RAPPI_PUSH_CARD_PURCHASE";

    private static final Pattern CARD_PURCHASE_TITLE =
            Pattern.compile("^RappiCard ?\\| ?Compra exitosa$", Pattern.CASE_INSENSITIVE);

    private static final Template CARD_PURCHASE_BODY = Template.movement(CARD_PURCHASE,
            "Tu compra en (?<merchant>.+?) por \\$ ?" + AMOUNT + " fue exitosa\\.",
            m -> Extraction.of(CaptureSource.RAPPI_PUSH, CARD_PURCHASE,MovementDirection.OUTFLOW, CaptureChannel.CARD)
                    .amount(m, AmountFormat.SPANISH, COP)
                    .product(ProductHint.withoutDigits(CaptureInstitution.RAPPI, ProductType.CREDIT_CARD))
                    .counterparty(m.group("merchant"))
                    .build());

    @Override
    public Optional<ParseOutcome> parse(RawNotification notification) {
        if (!NotificationText.simplify(notification.app()).contains("rappi")) {
            return Optional.empty();
        }
        String title = NotificationText.collapseSpaces(notification.title());
        if (CARD_PURCHASE_TITLE.matcher(title).matches()) {
            String body = NotificationText.collapseSpaces(notification.body());
            return Optional.of(CARD_PURCHASE_BODY.apply(body).orElseGet(ParseOutcome.Unrecognized::new));
        }
        return Optional.of(new ParseOutcome.Unrecognized());
    }
}
