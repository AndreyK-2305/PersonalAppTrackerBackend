package com.personaltracker.capture.domain.parsing;

import static com.personaltracker.capture.domain.parsing.Extraction.AMOUNT;
import static com.personaltracker.capture.domain.parsing.Extraction.COP;
import static com.personaltracker.capture.domain.parsing.Extraction.DATE;
import static com.personaltracker.capture.domain.parsing.Extraction.LAST_FOUR;
import static com.personaltracker.capture.domain.parsing.Extraction.TIME;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.CaptureInstitution;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ParsedMovement;
import com.personaltracker.capture.domain.model.ProductHint;
import com.personaltracker.capture.domain.model.ProductType;
import com.personaltracker.capture.domain.model.RawNotification;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * SMS de Bancolombia (remitente 85540). La automatización de Atajos filtra por remitente; aquí se
 * exige además que venga de la app de mensajes y que empiece por "Bancolombia:". Las plantillas
 * están en docs/capture/formatos-de-notificaciones.md.
 */
public final class BancolombiaSmsParser implements NotificationParser {

    public static final String CARD_PURCHASE = "BANCOLOMBIA_SMS_CARD_PURCHASE";
    public static final String CARD_PURCHASE_ASSOCIATED = "BANCOLOMBIA_SMS_CARD_PURCHASE_ASSOCIATED";
    public static final String ACCOUNT_TRANSFER = "BANCOLOMBIA_SMS_ACCOUNT_TRANSFER";
    public static final String KEY_TRANSFER = "BANCOLOMBIA_SMS_KEY_TRANSFER";
    public static final String QR_PAYMENT = "BANCOLOMBIA_SMS_QR_PAYMENT";
    public static final String TRANSFER_RECEIVED = "BANCOLOMBIA_SMS_TRANSFER_RECEIVED";
    public static final String PAYMENT_RECEIVED = "BANCOLOMBIA_SMS_PAYMENT_RECEIVED";
    public static final String CASH_DEPOSIT = "BANCOLOMBIA_SMS_CASH_DEPOSIT";
    public static final String ENROLLMENT = "BANCOLOMBIA_SMS_ENROLLMENT";

    private static final Set<String> MESSAGE_APPS = Set.of("mensajes", "messages");
    private static final String PREFIX = "bancolombia:";
    private static final CaptureInstitution BANK = CaptureInstitution.BANCOLOMBIA;
    private static final String CURRENCY = "(?<currency>COP|USD) ?";
    private static final String AT = " a las ";

    private static final List<Template> TEMPLATES = List.of(
            Template.movement(CARD_PURCHASE,
                    "Bancolombia: Compraste " + CURRENCY + AMOUNT + " en (?<merchant>.+?) con tu T\\.Cred \\*"
                            + LAST_FOUR + ", el " + DATE + AT + TIME + "\\.",
                    m -> cardPurchase(CARD_PURCHASE, m)),
            Template.movement(CARD_PURCHASE_ASSOCIATED,
                    "Bancolombia: Compraste " + CURRENCY + AMOUNT + " en (?<merchant>.+?), el " + DATE + AT + TIME
                            + "\\. Esta compra esta asociada a T\\.Cred \\*" + LAST_FOUR + "\\.",
                    m -> cardPurchase(CARD_PURCHASE_ASSOCIATED, m)),
            Template.movement(ACCOUNT_TRANSFER,
                    "Bancolombia: Transferiste \\$" + AMOUNT + " desde tu cuenta \\*" + LAST_FOUR
                            + " a la cuenta \\*(?<dest>[0-9]+) el " + DATE + AT + TIME + "\\.",
                    m -> fromAccount(ACCOUNT_TRANSFER, MovementDirection.OUTFLOW, CaptureChannel.BANK_TRANSFER, m)
                            .counterparty("Cuenta *" + lastDigits(m.group("dest")))
                            .build()),
            Template.movement(KEY_TRANSFER,
                    "Bancolombia: [^,]+, transferiste \\$" + AMOUNT + " a la llave (?<key>\\S+) desde tu cuenta \\*"
                            + LAST_FOUR + " a (?<recipient>.+?) el " + DATE + AT + TIME + "\\.",
                    m -> fromAccount(KEY_TRANSFER, MovementDirection.OUTFLOW, CaptureChannel.BANK_TRANSFER, m)
                            .counterparty(m.group("recipient"))
                            .destinationKey(m.group("key"))
                            .recipientName(m.group("recipient"))
                            .build()),
            Template.movement(QR_PAYMENT,
                    "Bancolombia: .+? pagaste \\$" + AMOUNT + " por codigo QR desde tu cuenta \\*" + LAST_FOUR
                            + " a la llave \\S+ el " + DATE + AT + TIME + "\\.",
                    m -> fromAccount(QR_PAYMENT, MovementDirection.OUTFLOW, CaptureChannel.BANK_TRANSFER, m)
                            .counterparty("Pago QR")
                            .build()),
            Template.movement(TRANSFER_RECEIVED,
                    "Bancolombia: [^,]+, recibiste una transferencia de (?<sender>.+?) por \\$" + AMOUNT
                            + " en tu cuenta \\*" + LAST_FOUR + " conectada a la llave \\S+ el " + DATE + AT + TIME
                            + "\\.",
                    m -> fromAccount(TRANSFER_RECEIVED, MovementDirection.INFLOW, CaptureChannel.BANK_TRANSFER, m)
                            .counterparty(m.group("sender"))
                            .build()),
            // En este mensaje la fecha y la hora vienen invertidas: "el 07:48 a las 29/10/2026".
            Template.movement(PAYMENT_RECEIVED,
                    "Bancolombia: Recibiste un pago por \\$" + AMOUNT + " de (?<payer>.+?) a tu cuenta"
                            + " (?:AHORROS|CORRIENTE), el " + TIME + AT + DATE + "\\.",
                    m -> Extraction.of(PAYMENT_RECEIVED, MovementDirection.INFLOW, CaptureChannel.BANK_TRANSFER)
                            .amount(m, AmountFormat.ENGLISH, COP)
                            .occurredAt(m)
                            .product(ProductHint.withoutDigits(BANK, ProductType.DEPOSIT))
                            .counterparty(m.group("payer"))
                            .build()),
            Template.movement(CASH_DEPOSIT,
                    "Bancolombia: Recibiste una consignacion por \\$" + AMOUNT
                            + " desde el corresponsal (?<agent>.+?) en [^,]+, el " + DATE + " " + TIME + "\\.",
                    m -> Extraction.of(CASH_DEPOSIT, MovementDirection.INFLOW, CaptureChannel.DEPOSIT)
                            .amount(m, AmountFormat.ENGLISH, COP)
                            .occurredAt(m)
                            .product(ProductHint.withoutDigits(BANK, ProductType.DEPOSIT))
                            .counterparty(m.group("agent"))
                            .build()),
            Template.ignored(ENROLLMENT, "Bancolombia: Listo\\. (?:Te )?inscribiste"));

    @Override
    public Optional<ParseOutcome> parse(RawNotification notification) {
        String body = NotificationText.collapseSpaces(notification.body());
        if (!MESSAGE_APPS.contains(NotificationText.simplify(notification.app()))
                || !NotificationText.simplify(body).startsWith(PREFIX)) {
            return Optional.empty();
        }
        for (Template template : TEMPLATES) {
            Optional<ParseOutcome> outcome = template.apply(body);
            if (outcome.isPresent()) {
                return outcome;
            }
        }
        return Optional.of(new ParseOutcome.Unrecognized());
    }

    /** Compras con tarjeta de crédito; el monto puede venir en COP o en USD. */
    private static Optional<ParsedMovement> cardPurchase(
            String templateId, Matcher m) {
        Currency currency = Currency.getInstance(m.group("currency").toUpperCase(Locale.ROOT));
        return Extraction.of(templateId, MovementDirection.OUTFLOW, CaptureChannel.CARD)
                .amount(m, AmountFormat.SPANISH, currency)
                .occurredAt(m)
                .product(ProductHint.of(BANK, ProductType.CREDIT_CARD, m.group("last")))
                .counterparty(m.group("merchant"))
                .build();
    }

    /** Movimientos de la cuenta de depósito identificada por sus últimos 4 dígitos. */
    private static Extraction fromAccount(
            String templateId, MovementDirection direction, CaptureChannel channel, Matcher m) {
        return Extraction.of(templateId, direction, channel)
                .amount(m, AmountFormat.ENGLISH, COP)
                .occurredAt(m)
                .product(ProductHint.of(BANK, ProductType.DEPOSIT, m.group("last")));
    }

    private static String lastDigits(String number) {
        return number.length() <= 4 ? number : number.substring(number.length() - 4);
    }
}
