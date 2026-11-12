package com.personaltracker.capture.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.capture.domain.model.CaptureChannel;
import com.personaltracker.capture.domain.model.CaptureEntry;
import com.personaltracker.capture.domain.model.CaptureEntry.EntryType;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.OwnedProduct;
import com.personaltracker.capture.domain.model.OwnedProductKind;
import com.personaltracker.capture.domain.model.OwnerName;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.capture.domain.parsing.NotificationParsers;
import com.personaltracker.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessNotificationTests {

    private static final Instant NOW = Instant.parse("2026-11-09T15:00:00Z");
    /** 8 de noviembre de 2026, 09:00 en Bogotá. */
    private static final Instant RECEIVED = Instant.parse("2026-11-08T14:00:00Z");

    private final UserId owner = UserId.newId();
    private final UUID creditCard = UUID.randomUUID();
    private final UUID savings = UUID.randomUUID();
    private final UUID rappiCard = UUID.randomUUID();
    private final UUID rappiAccount = UUID.randomUUID();

    private final CaptureFakes.Notifications notifications = new CaptureFakes.Notifications();
    private final CaptureFakes.Products products = new CaptureFakes.Products();
    private final CaptureFakes.Owners owners = new CaptureFakes.Owners();
    private final CaptureFakes.Keys keys = new CaptureFakes.Keys();
    private final CaptureFakes.Rates rates = new CaptureFakes.Rates();
    private final CaptureFakes.RecordingLedger ledger = new CaptureFakes.RecordingLedger();

    private final NotificationParsers parsers = NotificationParsers.all();

    private final ProcessNotification useCase = new ProcessNotification(notifications, parsers,
            products, owners, keys, rates, ledger, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        products.active.put(owner, List.of(
                product(creditCard, OwnedProductKind.CREDIT_CARD, "Bancolombia", "1234"),
                product(savings, OwnedProductKind.DEPOSIT, "Bancolombia", "5678"),
                product(rappiCard, OwnedProductKind.CREDIT_CARD, "RappiCard", null),
                product(rappiAccount, OwnedProductKind.DEPOSIT, "Rappi", "9012"),
                product(UUID.randomUUID(), OwnedProductKind.CASH, null, null)));
        owners.names.put(owner, OwnerName.of("Ana María", "Pérez Gómez"));
        keys.owner = owner;
    }

    private static OwnedProduct product(UUID id, OwnedProductKind kind, String institution, String digits) {
        return new OwnedProduct(id, kind, Optional.ofNullable(institution), Optional.ofNullable(digits));
    }

    private CapturedNotification receive(String app, String title, String body, Instant receivedAt) {
        CapturedNotification notification = CapturedNotification.receive(
                NotificationId.newId(), owner, new RawNotification(app, title, body, receivedAt), receivedAt);
        notifications.save(notification);
        return notification;
    }

    private CapturedNotification process(String app, String title, String body) {
        return processAt(app, title, body, RECEIVED);
    }

    private CapturedNotification processAt(String app, String title, String body, Instant receivedAt) {
        CapturedNotification notification = receive(app, title, body, receivedAt);
        useCase.execute(notification.id());
        return notifications.findById(notification.id()).orElseThrow();
    }

    /**
     * Aviso de Apple Wallet de una tarjeta Bancolombia, como lo entrega el iPhone (sin hora ni dígitos),
     * recibido a la hora local de Bogotá indicada.
     */
    private CapturedNotification wallet(String amount, String merchant, String bogotaTime) {
        return processAt("Wallet", "BANCOLOMBIA", merchant + ". Cúcuta, Norte De Santander\n$ " + amount,
                bogota(bogotaTime));
    }

    private static Instant bogota(String time) {
        return Instant.parse("2026-11-08T" + time + ":00-05:00");
    }

    private CapturedNotification sms(String body) {
        return process("Mensajes", "85540", body);
    }

    private static String cardPurchase(String amount, String merchant, String time) {
        return "Bancolombia: Compraste COP" + amount + " en " + merchant + " con tu T.Cred *1234, el 08/11/2026 a las "
                + time + ". Si tienes dudas, encuentranos aqui: 018000931987. Estamos cerca.";
    }

    private static String keyTransfer(String key, String recipient) {
        return "Bancolombia: ANA, transferiste $176,700.00 a la llave " + key + " desde tu cuenta *5678 a " + recipient
                + " el 08/11/26 a las 13:36. Con Bre-b es de una y gratis. Dudas al 018000912345";
    }

    @Nested
    class Recording {

        @Test
        void aCardPurchaseIsAnExpenseOfTheMatchingCard() {
            CapturedNotification result = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(result.reviewReason()).isEmpty();
            CaptureEntry entry = ledger.last();
            assertThat(entry.type()).isEqualTo(EntryType.EXPENSE);
            assertThat(entry.source()).contains(creditCard);
            assertThat(entry.amount().amount()).isEqualByComparingTo("9780");
            assertThat(entry.merchant()).isEqualTo("TIENDAS ARA");
            assertThat(entry.channel()).isEqualTo(CaptureChannel.CARD);
            assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 11, 8));
            assertThat(entry.estimatedAmount()).isFalse();
            assertThat(result.transactionId()).contains(ledger.recorded.getLast().id());
            assertThat(ledger.recorded.getLast().owner()).isEqualTo(owner);
        }

        @Test
        void theLocalDateOfTheTextIsUsedEvenNearMidnight() {
            // 00:07 en Bogotá es 05:07 UTC del mismo día: la fecha es la local.
            sms("Bancolombia: Compraste COP3.900,00 en APPLE.COM/BILL, el 08/11/2026 a las 00:07. Esta compra"
                    + " esta asociada a T.Cred *1234. Si tienes dudas, encuentranos aqui: 018000931987.");

            assertThat(ledger.last().date()).isEqualTo(LocalDate.of(2026, 11, 8));
        }

        @Test
        void aReceivedTransferIsAnIncomeOfTheMatchingAccount() {
            sms("Bancolombia: Ana, recibiste una transferencia de CARLOS RUIZ por $350,000.00 en tu cuenta *5678"
                    + " conectada a la llave 1000000001 el 05/11/26 a las 13:11. Dudas al 018000912345");

            CaptureEntry entry = ledger.last();
            assertThat(entry.type()).isEqualTo(EntryType.INCOME);
            assertThat(entry.destination()).contains(savings);
            assertThat(entry.source()).isEmpty();
            assertThat(entry.merchant()).isEqualTo("CARLOS RUIZ");
        }

        @Test
        void aCashDepositIsAlwaysAnIncomeOfTheOnlyBancolombiaAccount() {
            CapturedNotification result = sms("Bancolombia: Recibiste una consignacion por $480,000 desde el"
                    + " corresponsal BARRIO CENTRO 1 en CIUDAD, el 05/11/26 09:07. Si tienes dudas, llamanos.");

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(ledger.last().type()).isEqualTo(EntryType.INCOME);
            assertThat(ledger.last().destination()).contains(savings);
            assertThat(ledger.last().channel()).isEqualTo(CaptureChannel.DEPOSIT);
        }

        @Test
        void aRappiCardPurchaseUsesTheReceptionDateAndTheOnlyRappiCard() {
            process("Rappi", "RappiCard | Compra exitosa",
                    "Tu compra en JHON PASTEL por $ 13.300 fue exitosa. Consulta los detalles de tu compra.");

            CaptureEntry entry = ledger.last();
            assertThat(entry.source()).contains(rappiCard);
            assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 11, 8));
        }

        @Test
        void anUnknownCardGoesToOtherWithoutReview() {
            CapturedNotification result = sms("Bancolombia: Compraste COP25.005,00 en EDS con tu T.Cred *4321, el"
                    + " 03/11/2026 a las 16:44. Si tienes dudas.");

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(ledger.last().source()).contains(products.otherProduct(owner));
        }

        @Test
        void twoCardsWithTheSameInstitutionAndDigitsGoToOtherForReview() {
            UUID secondCard = UUID.randomUUID();
            products.active.put(owner, List.of(
                    product(creditCard, OwnedProductKind.CREDIT_CARD, "Bancolombia", "1234"),
                    product(secondCard, OwnedProductKind.CREDIT_CARD, "Bancolombia Visa", "1234")));

            CapturedNotification result = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            assertThat(result.status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(result.reviewReason()).contains(ReviewReason.AMBIGUOUS_PRODUCT);
            assertThat(ledger.last().source()).contains(products.otherProduct(owner));
        }
    }

    @Nested
    class Dollars {

        private final String purchase = "Bancolombia: Compraste USD20,00 en ANTHROPIC* CLAUDE SU con tu T.Cred *1234,"
                + " el 17/10/2026 a las 13:26. Si tienes dudas, encuentranos aqui.";

        @Test
        void areConvertedWithTheExchangeRateOfThePurchaseDateAndMarkedAsEstimated() {
            rates.byDate.put(LocalDate.of(2026, 10, 17), new BigDecimal("3174.67"));

            CapturedNotification result = sms(purchase);

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            CaptureEntry entry = ledger.last();
            assertThat(entry.amount().amount()).isEqualByComparingTo("63493.40");
            assertThat(entry.amount().currency().getCurrencyCode()).isEqualTo("COP");
            assertThat(entry.estimatedAmount()).isTrue();
            assertThat(entry.description()).hasValueSatisfying(note -> assertThat(note)
                    .contains("USD 20.00").contains("3174.67").contains("estimado"));
        }

        @Test
        void roundHalfEven() {
            rates.byDate.put(LocalDate.of(2026, 10, 17), new BigDecimal("3174.66725"));

            sms(purchase);

            // 20 × 3174.66725 = 63493.345 → 63493.34
            assertThat(ledger.last().amount().amount()).isEqualByComparingTo("63493.34");
        }

        @Test
        void withoutAnExchangeRateNothingIsRecordedAndTheNotificationWaitsForReview() {
            CapturedNotification result = sms(purchase);

            assertThat(result.status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(result.reviewReason()).contains(ReviewReason.MISSING_EXCHANGE_RATE);
            assertThat(result.transactionId()).isEmpty();
            assertThat(ledger.recorded).isEmpty();
        }
    }

    @Nested
    class OwnTransfers {

        @Test
        void aRegisteredKeyIsATransferToItsProduct() {
            keys.byKey.put("@rp1000000001", rappiAccount);

            CapturedNotification result = sms(keyTransfer("@rp1000000001", "Ana Maria Perez Gomez"));

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            CaptureEntry entry = ledger.last();
            assertThat(entry.type()).isEqualTo(EntryType.TRANSFER);
            assertThat(entry.source()).contains(savings);
            assertThat(entry.destination()).contains(rappiAccount);
        }

        @Test
        void theOwnNameWithAnUnknownKeyIsAnExpenseProposedAsOwnTransfer() {
            CapturedNotification result = sms(keyTransfer("@rp1000000001", "Ana Perez"));

            assertThat(result.status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(result.reviewReason()).contains(ReviewReason.POSSIBLE_OWN_TRANSFER);
            assertThat(ledger.last().type()).isEqualTo(EntryType.EXPENSE);
            assertThat(result.transactionId()).isPresent();
        }

        @Test
        void someoneElseIsAPlainExpense() {
            CapturedNotification result = sms(keyTransfer("3001112233", "Carlos Andres Ruiz Diaz"));

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(ledger.last().type()).isEqualTo(EntryType.EXPENSE);
            assertThat(ledger.last().merchant()).isEqualTo("Carlos Andres Ruiz Diaz");
        }

        @Test
        void withoutNamesInTheProfileTheNameIsNeverOwn() {
            owners.names.remove(owner);

            CapturedNotification result = sms(keyTransfer("@rp1000000001", "Ana Perez"));

            assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
        }

        @Test
        void aKeyOfTheSameSourceProductIsNotATransfer() {
            keys.byKey.put("@rp1000000001", savings);

            sms(keyTransfer("@rp1000000001", "Carlos Ruiz"));

            assertThat(ledger.last().type()).isEqualTo(EntryType.EXPENSE);
        }

        @Test
        void keysOfOtherUsersAreIgnored() {
            keys.owner = UserId.newId();
            keys.byKey.put("@rp1000000001", rappiAccount);

            sms(keyTransfer("@rp1000000001", "Carlos Ruiz"));

            assertThat(ledger.last().type()).isEqualTo(EntryType.EXPENSE);
        }
    }

    @Nested
    class SamePurchaseFromSeveralSources {

        /** Solo la tarjeta de crédito de Bancolombia: Wallet la identifica sin ayuda del SMS. */
        private void onlyTheBancolombiaCard() {
            products.active.put(owner, List.of(product(creditCard, OwnedProductKind.CREDIT_CARD, "Bancolombia", "1234")));
        }

        @Test
        void walletRepeatingTheSmsIsPairedWithTheProductOfTheSms() {
            CapturedNotification sms = sms(cardPurchase("9.980,00", "TIENDA D1 LOS SANTOS", "10:18"));
            CapturedNotification wallet = wallet("9.980", "D1 - Antonia Santos", "10:19");

            assertThat(sms.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(wallet.status()).isEqualTo(CaptureStatus.PAIRED);
            assertThat(wallet.pairedWith()).contains(sms.id());
            assertThat(wallet.footprint()).hasValueSatisfying(
                    footprint -> assertThat(footprint.productId()).isEqualTo(creditCard));
            assertThat(wallet.transactionId()).isEmpty();
            assertThat(ledger.recorded).hasSize(1);
            assertThat(ledger.last().merchant()).isEqualTo("TIENDA D1 LOS SANTOS");
        }

        @Test
        void walletAloneWithSeveralBancolombiaProductsGoesToOtherForReview() {
            CapturedNotification wallet = wallet("7.900", "Drogueria Santiago For Ever", "19:54");

            assertThat(wallet.status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(wallet.reviewReason()).contains(ReviewReason.AMBIGUOUS_PRODUCT);
            assertThat(ledger.last().source()).contains(products.otherProduct(owner));
            assertThat(ledger.last().merchant()).isEqualTo("Drogueria Santiago For Ever");
        }

        @Test
        void walletAloneWithASingleBancolombiaCardIsRecorded() {
            onlyTheBancolombiaCard();

            CapturedNotification wallet = wallet("7.900", "Drogueria Santiago For Ever", "19:54");

            assertThat(wallet.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(ledger.last().source()).contains(creditCard);
            assertThat(ledger.last().date()).isEqualTo(LocalDate.of(2026, 11, 8));
        }

        @Test
        void aLateSmsIsPairedWithTheWalletPurchaseAlreadyRecorded() {
            onlyTheBancolombiaCard();
            CapturedNotification wallet = wallet("9.780", "Tiendas Ara", "08:49");
            CapturedNotification sms = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            assertThat(wallet.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(sms.status()).isEqualTo(CaptureStatus.PAIRED);
            assertThat(ledger.recorded).hasSize(1);
        }

        @Test
        void threeEqualPurchasesAreThreeMovementsWithOneWalletNoticeEach() {
            List<CapturedNotification> smsNotices = List.of(
                    sms(cardPurchase("5.000,00", "TIENDAS ARA", "08:48")),
                    sms(cardPurchase("5.000,00", "TIENDAS ARA", "08:48")),
                    sms(cardPurchase("5.000,00", "TIENDAS ARA", "08:49")));
            List<CapturedNotification> walletNotices = List.of(
                    wallet("5.000", "Tiendas Ara", "08:48"),
                    wallet("5.000", "Tiendas Ara", "08:49"),
                    wallet("5.000", "Tiendas Ara", "08:49"));

            assertThat(smsNotices).allMatch(notice -> notice.status() == CaptureStatus.RECORDED);
            assertThat(walletNotices).allMatch(notice -> notice.status() == CaptureStatus.PAIRED);
            assertThat(walletNotices).extracting(notice -> notice.pairedWith().orElseThrow())
                    .containsExactlyInAnyOrderElementsOf(smsNotices.stream().map(CapturedNotification::id).toList());
            assertThat(ledger.recorded).hasSize(3);
        }

        @Test
        void moreWalletNoticesThanSmsLeaveTheExtraAsANewPurchase() {
            sms(cardPurchase("5.000,00", "TIENDAS ARA", "08:48"));
            wallet("5.000", "Tiendas Ara", "08:48");
            CapturedNotification extra = wallet("5.000", "Tiendas Ara", "08:49");

            assertThat(extra.status()).isNotEqualTo(CaptureStatus.PAIRED);
            assertThat(extra.transactionId()).isPresent();
            assertThat(ledger.recorded).hasSize(2);
        }

        @Test
        void theSameSourceRepeatedIsSeveralPurchases() {
            CapturedNotification first = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));
            CapturedNotification second = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            assertThat(first.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(second.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(ledger.recorded).hasSize(2);
        }

        @Test
        void walletWithoutCentsMatchesTheSmsAmount() {
            sms(cardPurchase("25.005,50", "EDS LOS LIBERTADORES", "16:44"));

            assertThat(wallet("25.005", "Eds Los Libertadores", "16:45").status())
                    .isEqualTo(CaptureStatus.PAIRED);
        }

        @Test
        void anotherAmountOrMomentIsAnotherPurchase() {
            sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            assertThat(wallet("9.790", "Tiendas Ara", "08:48").status()).isNotEqualTo(CaptureStatus.PAIRED);
            assertThat(wallet("9.780", "Tiendas Ara", "08:54").status()).isNotEqualTo(CaptureStatus.PAIRED);
        }

        @Test
        void aPurchaseOfAnotherBankIsNeverPaired() {
            process("Rappi", "RappiCard | Compra exitosa", "Tu compra en TIENDAS ARA por $ 9.780 fue exitosa.");

            assertThat(wallet("9.780", "Tiendas Ara", "09:00").status()).isNotEqualTo(CaptureStatus.PAIRED);
        }

        @Test
        void purchasesOfUnidentifiedProductsAreNeverPaired() {
            sms("Bancolombia: Compraste COP9.780,00 en TIENDAS ARA con tu T.Cred *4321, el 08/11/2026 a las 08:48."
                    + " Si tienes dudas.");

            assertThat(wallet("9.780", "Tiendas Ara", "08:48").status()).isNotEqualTo(CaptureStatus.PAIRED);
            assertThat(ledger.recorded).hasSize(2);
        }

        @Test
        void notificationsWaitTwoMinutesBeforeBeingProcessed() {
            CapturedNotification fresh = receive("Mensajes", "85540", cardPurchase("9.780,00", "TIENDAS ARA", "08:48"),
                    NOW.minusSeconds(90));

            useCase.execute(fresh.id());

            assertThat(notifications.findById(fresh.id()).orElseThrow().isPending()).isTrue();
            assertThat(ledger.recorded).isEmpty();
        }
    }

    @Nested
    class WithoutMovement {

        @Test
        void anEnrollmentIsDiscarded() {
            CapturedNotification result = sms("Bancolombia: Listo. Inscribiste tu Clave Dinamica por APP el"
                    + " 30/10/2026 a las 14:12.");

            assertThat(result.status()).isEqualTo(CaptureStatus.DISCARDED);
            assertThat(ledger.recorded).isEmpty();
        }

        @Test
        void anUnrecognizedTextWaitsForReviewWithoutMovement() {
            CapturedNotification result = sms("Bancolombia: Pagaste tu tarjeta de credito *1234 por $500,000.00.");

            assertThat(result.status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(result.reviewReason()).contains(ReviewReason.UNRECOGNIZED_TEXT);
            assertThat(result.transactionId()).isEmpty();
            assertThat(ledger.recorded).isEmpty();
        }

        @Test
        void processedOrMissingNotificationsAreIgnored() {
            CapturedNotification result = sms(cardPurchase("9.780,00", "TIENDAS ARA", "08:48"));

            useCase.execute(result.id());
            useCase.execute(NotificationId.newId());

            assertThat(ledger.recorded).hasSize(1);
        }
    }
}
