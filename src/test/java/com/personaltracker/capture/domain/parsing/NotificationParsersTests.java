package com.personaltracker.capture.domain.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.CaptureInstitution;
import com.personaltracker.capture.domain.model.MovementDirection;
import com.personaltracker.capture.domain.model.ParseOutcome;
import com.personaltracker.capture.domain.model.ParsedMovement;
import com.personaltracker.capture.domain.model.ProductHint;
import com.personaltracker.capture.domain.model.ProductType;
import com.personaltracker.capture.domain.model.RawNotification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Textos de docs/capture/formatos-de-notificaciones.md, anonimizados: nombres, llaves, números de
 * destino y últimos 4 dígitos no son los reales.
 */
class NotificationParsersTests {

    private static final Instant RECEIVED = Instant.parse("2026-11-08T14:00:00Z");

    private final NotificationParsers parsers = NotificationParsers.all();

    private static RawNotification sms(String body) {
        return new RawNotification("Mensajes", "85540", body, RECEIVED);
    }

    private ParsedMovement recognized(RawNotification notification) {
        ParseOutcome outcome = parsers.parse(notification);
        assertThat(outcome).isInstanceOf(ParseOutcome.Recognized.class);
        return ((ParseOutcome.Recognized) outcome).movement();
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(2);
    }

    @Nested
    class BancolombiaSms {

        @Test
        void cardPurchase() {
            ParsedMovement movement = recognized(sms("Bancolombia: Compraste COP25.005,00 en EDS LOS LIBERTADORES"
                    + " con tu T.Cred *1234, el 03/11/2026 a las 16:44. Si tienes dudas, encuentranos aqui:"
                    + " 6045109095 o 018000931987. Estamos cerca."));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.CARD_PURCHASE);
            assertThat(movement.direction()).isEqualTo(MovementDirection.OUTFLOW);
            assertThat(movement.amount().amount()).isEqualByComparingTo(amount("25005.00"));
            assertThat(movement.amount().currency().getCurrencyCode()).isEqualTo("COP");
            assertThat(movement.counterparty()).isEqualTo("EDS LOS LIBERTADORES");
            assertThat(movement.product())
                    .isEqualTo(ProductHint.of(CaptureInstitution.BANCOLOMBIA, ProductType.CREDIT_CARD, "1234"));
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 11, 3, 16, 44));
            assertThat(movement.channel()).isEqualTo(CaptureChannel.CARD);
            assertThat(movement.destinationKey()).isEmpty();
        }

        @Test
        void cardPurchaseWithTheCardAtTheEnd() {
            ParsedMovement movement = recognized(sms("Bancolombia: Compraste COP3.900,00 en APPLE.COM/BILL, el"
                    + " 08/11/2026 a las 00:07. Esta compra esta asociada a T.Cred *1234. Si tienes dudas,"
                    + " encuentranos aqui: 018000931987. Siempre contigo."));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.CARD_PURCHASE_ASSOCIATED);
            assertThat(movement.amount().amount()).isEqualByComparingTo("3900");
            assertThat(movement.counterparty()).isEqualTo("APPLE.COM/BILL");
            assertThat(movement.product().lastFourDigits()).contains("1234");
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 11, 8, 0, 7));
        }

        @Test
        void cardPurchaseInDollarsKeepsTheCurrency() {
            ParsedMovement movement = recognized(sms("Bancolombia: Compraste USD20,00 en ANTHROPIC* CLAUDE SU con"
                    + " tu T.Cred *1234, el 17/10/2026 a las 13:26. Si tienes dudas, encuentranos aqui:"
                    + " 6045109095 o 018000931987. Estamos cerca."));

            assertThat(movement.amount().amount()).isEqualByComparingTo("20.00");
            assertThat(movement.amount().currency().getCurrencyCode()).isEqualTo("USD");
            assertThat(movement.counterparty()).isEqualTo("ANTHROPIC* CLAUDE SU");
        }

        @Test
        void transferToAnAccountKeepsOnlyTheLastDigitsOfTheDestination() {
            ParsedMovement movement = recognized(sms("Bancolombia: Transferiste $6,500.00 desde tu cuenta *5678 a"
                    + " la cuenta *3001112233 el 04/11/26 a las 19:28. ¿Dudas? Llamanos al 018000931987."
                    + " Estamos cerca."));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.ACCOUNT_TRANSFER);
            assertThat(movement.amount().amount()).isEqualByComparingTo("6500");
            assertThat(movement.product())
                    .isEqualTo(ProductHint.of(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT, "5678"));
            assertThat(movement.counterparty()).isEqualTo("Cuenta *2233");
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 11, 4, 19, 28));
            assertThat(movement.channel()).isEqualTo(CaptureChannel.BANK_TRANSFER);
        }

        @Test
        void transferToAKeyKeepsTheKeyAndTheRecipient() {
            ParsedMovement movement = recognized(sms("Bancolombia: ANA, transferiste $176,700.00 a la llave"
                    + " @rp1000000001 desde tu cuenta *5678 a Ana Maria Perez Gomez el 05/11/26 a las 13:36."
                    + " Con Bre-b es de una y gratis. Dudas al 018000912345"));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.KEY_TRANSFER);
            assertThat(movement.amount().amount()).isEqualByComparingTo("176700");
            assertThat(movement.destinationKey()).contains("@rp1000000001");
            assertThat(movement.recipientName()).contains("Ana Maria Perez Gomez");
            assertThat(movement.counterparty()).isEqualTo("Ana Maria Perez Gomez");
        }

        @Test
        void transferToAnEmailKey() {
            ParsedMovement movement = recognized(sms("Bancolombia: ANA, transferiste $57,000.00 a la llave"
                    + " ana.perez@example.com desde tu cuenta *5678 a Ana Maria Perez Gomez el 05/11/26 a las"
                    + " 08:59. Con Bre-b es de una y gratis. Dudas al 018000912345"));

            assertThat(movement.destinationKey()).contains("ana.perez@example.com");
        }

        @Test
        void hundredPesosIsNotTenThousand() {
            ParsedMovement movement = recognized(sms("Bancolombia: ANA, transferiste $100.00 a la llave"
                    + " @rp1000000001 desde tu cuenta *5678 a Ana Maria Perez Gomez el 05/11/26 a las 13:38."
                    + " Con Bre-b es de una y gratis. Dudas al 018000912345"));

            assertThat(movement.amount().amount()).isEqualByComparingTo("100.00");
        }

        @Test
        void qrPayment() {
            ParsedMovement movement = recognized(sms("Bancolombia: ANA MARIA PEREZ GOMEZ pagaste $2,000.00 por"
                    + " codigo QR desde tu cuenta *5678 a la llave 0090000001 el 03/11/2026 a las 10:15. Con"
                    + " codigo QR es facil y de una. Dudas al 018000912345"));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.QR_PAYMENT);
            assertThat(movement.amount().amount()).isEqualByComparingTo("2000");
            assertThat(movement.counterparty()).isEqualTo("Pago QR");
            assertThat(movement.destinationKey()).isEmpty();
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 11, 3, 10, 15));
        }

        @Test
        void transferReceived() {
            ParsedMovement movement = recognized(sms("Bancolombia: Ana, recibiste una transferencia de CARLOS"
                    + " ANDRES RUIZ DIAZ por $350,000.00 en tu cuenta *5678 conectada a la llave 1000000001 el"
                    + " 05/11/26 a las 13:11. Con llaves es de una y gratis. Dudas al 018000912345"));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.TRANSFER_RECEIVED);
            assertThat(movement.direction()).isEqualTo(MovementDirection.INFLOW);
            assertThat(movement.amount().amount()).isEqualByComparingTo("350000");
            assertThat(movement.counterparty()).isEqualTo("CARLOS ANDRES RUIZ DIAZ");
            assertThat(movement.product().lastFourDigits()).contains("5678");
        }

        @Test
        void paymentReceivedWithDateAndTimeSwapped() {
            ParsedMovement movement = recognized(sms("Bancolombia: Recibiste un pago por $813,452.00 de"
                    + " EMPRESA EJEMPLO SAS a tu cuenta AHORROS, el 07:48 a las 29/10/2026. ¿Tienes dudas?"
                    + " Encuentranos aqui:018000931987. Estamos cerca"));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.PAYMENT_RECEIVED);
            assertThat(movement.direction()).isEqualTo(MovementDirection.INFLOW);
            assertThat(movement.amount().amount()).isEqualByComparingTo("813452");
            assertThat(movement.counterparty()).isEqualTo("EMPRESA EJEMPLO SAS");
            assertThat(movement.product())
                    .isEqualTo(ProductHint.withoutDigits(CaptureInstitution.BANCOLOMBIA, ProductType.DEPOSIT));
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 10, 29, 7, 48));
        }

        @Test
        void cashDepositIsAnIncomeWithoutDigits() {
            ParsedMovement movement = recognized(sms("Bancolombia: Recibiste una consignacion por $480,000 desde el"
                    + " corresponsal BARRIO CENTRO 1 en CIUDAD, el 05/11/26 09:07. Si tienes dudas,"
                    + " llamanos: 018000931987. A tu lado siempre."));

            assertThat(movement.templateId()).isEqualTo(BancolombiaSmsParser.CASH_DEPOSIT);
            assertThat(movement.direction()).isEqualTo(MovementDirection.INFLOW);
            assertThat(movement.amount().amount()).isEqualByComparingTo("480000");
            assertThat(movement.counterparty()).isEqualTo("BARRIO CENTRO 1");
            assertThat(movement.channel()).isEqualTo(CaptureChannel.DEPOSIT);
            assertThat(movement.product().lastFourDigits()).isEmpty();
            assertThat(movement.occurredAt()).contains(LocalDateTime.of(2026, 11, 5, 9, 7));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "Bancolombia: Listo. Inscribiste tu Clave Dinamica por APP BANCOLOMBIA el 30/10/2026 a las 14:12."
                    + " ¿No fuiste tu? Llamanos al 6045109095. A tu lado en cada paso",
            "Bancolombia: Listo. Te inscribiste al servicio de Clave Dinamica por Bancolombia el 30/10/2026 a"
                    + " las 14:18. A partir de ahora, disfruta mas seguridad al mover tu plata."
        })
        void enrollmentsAreNotMovements(String body) {
            assertThat(parsers.parse(sms(body)))
                    .isEqualTo(new ParseOutcome.NotAMovement(BancolombiaSmsParser.ENROLLMENT));
        }

        @Test
        void lineBreaksAndNonBreakingSpacesAreIgnored() {
            ParsedMovement movement = recognized(sms("Bancolombia: Compraste COP9.780,00 en TIENDAS ARA\ncon tu"
                    + " T.Cred *1234, el 08/11/2026  a las 08:48. Si tienes dudas"));

            assertThat(movement.counterparty()).isEqualTo("TIENDAS ARA");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            // Formato de monto distinto al de la plantilla.
            "Bancolombia: Transferiste $6.500,00 desde tu cuenta *5678 a la cuenta *3001112233 el 04/11/26 a las 19:28.",
            "Bancolombia: Compraste COP25,005.00 en EDS con tu T.Cred *1234, el 03/11/2026 a las 16:44.",
            // Fecha imposible.
            "Bancolombia: Compraste COP25.005,00 en EDS con tu T.Cred *1234, el 31/02/2026 a las 16:44.",
            // Monto cero.
            "Bancolombia: Compraste COP0,00 en EDS con tu T.Cred *1234, el 03/11/2026 a las 16:44.",
            // Mensaje nuevo del banco.
            "Bancolombia: Pagaste tu tarjeta de credito *1234 por $500,000.00 el 03/11/2026.",
        })
        void anythingThatDoesNotMatchExactlyIsUnrecognized(String body) {
            assertThat(parsers.parse(sms(body))).isEqualTo(new ParseOutcome.Unrecognized());
        }

        @Test
        void onlyMessagesFromTheMessagesAppAreBancolombiaSms() {
            RawNotification fromAnotherApp = new RawNotification("WhatsApp", "Alguien",
                    "Bancolombia: Compraste COP25.005,00 en EDS con tu T.Cred *1234, el 03/11/2026 a las 16:44.",
                    RECEIVED);

            assertThat(parsers.parse(fromAnotherApp)).isEqualTo(new ParseOutcome.Unrecognized());
            assertThat(parsers.parse(new RawNotification("Messages", "85540",
                    "Bancolombia: Compraste COP25.005,00 en EDS con tu T.Cred *1234, el 03/11/2026 a las 16:44.",
                    RECEIVED))).isInstanceOf(ParseOutcome.Recognized.class);
        }
    }

    @Nested
    class RappiPush {

        @Test
        void cardPurchaseUsesTheReceptionTime() {
            ParsedMovement movement = recognized(new RawNotification("Rappi", "RappiCard | Compra exitosa",
                    "Tu compra en JHON PASTEL por $ 13.300 fue exitosa. Consulta los detalles de tu compra en tus"
                            + " transacciones.",
                    RECEIVED));

            assertThat(movement.templateId()).isEqualTo(RappiPushParser.CARD_PURCHASE);
            assertThat(movement.direction()).isEqualTo(MovementDirection.OUTFLOW);
            assertThat(movement.amount().amount()).isEqualByComparingTo("13300");
            assertThat(movement.counterparty()).isEqualTo("JHON PASTEL");
            assertThat(movement.product())
                    .isEqualTo(ProductHint.withoutDigits(CaptureInstitution.RAPPI, ProductType.CREDIT_CARD));
            assertThat(movement.occurredAt()).isEqualTo(Optional.empty());
            assertThat(movement.channel()).isEqualTo(CaptureChannel.CARD);
        }

        @Test
        void otherRappiNotificationsAreUnrecognized() {
            assertThat(parsers.parse(new RawNotification("Rappi", "¡Tu pedido va en camino!",
                    "Tu domiciliario ya recogió tu pedido.", RECEIVED)))
                    .isEqualTo(new ParseOutcome.Unrecognized());
        }
    }

    @Test
    void unknownSourcesAreUnrecognized() {
        assertThat(parsers.parse(new RawNotification("Nequi", "Nequi", "Recibiste $10.000", RECEIVED)))
                .isEqualTo(new ParseOutcome.Unrecognized());
    }
}
