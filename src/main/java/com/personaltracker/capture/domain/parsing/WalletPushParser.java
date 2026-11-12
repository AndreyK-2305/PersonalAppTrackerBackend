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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Notificaciones de Apple Wallet al pagar con Apple Pay (W1 en docs/capture/formatos-de-notificaciones.md):
 *
 * <ul>
 *   <li>título: el emisor de la tarjeta, por ejemplo {@code BANCOLOMBIA};
 *   <li>texto: el comercio, opcionalmente la ciudad tras un punto, y el monto sin centavos, por
 *       ejemplo {@code Drogueria Santiago For Ever. Cúcuta, Norte De Santander $ 7.900}.
 * </ul>
 *
 * <p>No trae hora (se usa la de recepción), ni últimos dígitos, ni si la tarjeta es de crédito o
 * débito: por eso la pista de producto es {@link ProductType#PAYMENT_CARD} y el aviso suele
 * emparejarse con el SMS del banco (ADR-0017). Un emisor sin analizador es {@code Unrecognized}.
 */
public final class WalletPushParser implements NotificationParser {

    public static final String PURCHASE = "WALLET_PUSH_PURCHASE";

    private static final Set<String> WALLET_APPS = Set.of("wallet", "cartera");

    // El comercio llega hasta el primer ". " (lo que sigue es la ciudad) o hasta el monto.
    private static final String BODY = "(?<merchant>.+?)(?:\\. [^$]+)? \\$ ?" + AMOUNT + "$";

    @Override
    public Optional<ParseOutcome> parse(RawNotification notification) {
        if (!WALLET_APPS.contains(NotificationText.simplify(notification.app()))) {
            return Optional.empty();
        }
        Optional<CaptureInstitution> issuer = issuer(notification.title());
        if (issuer.isEmpty()) {
            return Optional.of(new ParseOutcome.Unrecognized());
        }
        Template template = Template.movement(PURCHASE, BODY,
                m -> Extraction.of(CaptureSource.APPLE_WALLET, PURCHASE, MovementDirection.OUTFLOW, CaptureChannel.CARD)
                        .amount(m, AmountFormat.SPANISH, COP)
                        .product(ProductHint.withoutDigits(issuer.get(), ProductType.PAYMENT_CARD))
                        .counterparty(m.group("merchant"))
                        .build());
        String body = NotificationText.collapseSpaces(notification.body());
        return Optional.of(template.apply(body).orElseGet(ParseOutcome.Unrecognized::new));
    }

    /** El emisor según el título; se compara sin mayúsculas ni tildes ("BANCOLOMBIA", "RappiCard"). */
    private static Optional<CaptureInstitution> issuer(String title) {
        String simplified = NotificationText.simplify(title);
        return Arrays.stream(CaptureInstitution.values())
                .filter(institution -> simplified.contains(NotificationText.simplify(institution.displayName())))
                .findFirst();
    }
}
