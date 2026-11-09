package com.personaltracker.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.application.usecase.ProcessNotification;
import com.personaltracker.capture.application.usecase.ProcessPendingNotifications;
import com.personaltracker.capture.application.usecase.PurgeRawNotifications;
import com.personaltracker.capture.domain.model.CaptureFootprint;
import com.personaltracker.capture.domain.model.CaptureSource;
import com.personaltracker.capture.domain.model.CaptureStatus;
import com.personaltracker.capture.domain.model.CapturedNotification;
import com.personaltracker.capture.domain.model.NotificationId;
import com.personaltracker.capture.domain.model.RawNotification;
import com.personaltracker.capture.domain.model.ReviewReason;
import com.personaltracker.finance.application.port.out.AccountRepository;
import com.personaltracker.finance.application.port.out.TransactionRepository;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.AccountKind;
import com.personaltracker.finance.domain.model.CreditCardAccount;
import com.personaltracker.finance.domain.model.DepositAccount;
import com.personaltracker.finance.domain.model.Expense;
import com.personaltracker.finance.domain.model.FinanceTestData;
import com.personaltracker.finance.domain.model.Institution;
import com.personaltracker.finance.domain.model.LastFourDigits;
import com.personaltracker.finance.domain.model.PaymentChannel;
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionOrigin;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.PersonName;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.Money;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Captura de punta a punta contra PostgreSQL: cola en {@code capture}, productos y movimientos en
 * {@code finance} a través de su API, y nombres del perfil en {@code identity}. Los textos están
 * anonimizados.
 */
@IntegrationTest
class CaptureIntegrationTests {

    @Autowired
    ProcessNotification processNotification;

    @Autowired
    ProcessPendingNotifications processPending;

    @Autowired
    PurgeRawNotifications purgeRaw;

    @Autowired
    CapturedNotificationRepository notifications;

    @Autowired
    AccountRepository accounts;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcClient jdbc;

    private UserId owner;
    private CreditCardAccount card;
    private DepositAccount savings;
    private DepositAccount rappiAccount;

    @BeforeEach
    void setUp() {
        User user = new TestAccounts(users, passwordHasher).create();
        user.changePersonName(new PersonName("Ana María", "Pérez Gómez"), Instant.now());
        users.save(user);
        owner = user.id();
        Instant now = Instant.now();
        card = CreditCardAccount.open(AccountId.newId(), owner, "Mastercard", new Institution("Bancolombia"),
                new LastFourDigits("1234"), FinanceTestData.cop("5000000"), FinanceTestData.cop("0"), now);
        savings = DepositAccount.open(AccountId.newId(), owner, AccountKind.SAVINGS, "Ahorros",
                new Institution("Bancolombia"), new LastFourDigits("5678"), FinanceTestData.cop("1000000"), now);
        rappiAccount = DepositAccount.open(AccountId.newId(), owner, AccountKind.DIGITAL_WALLET, "RappiCuenta",
                new Institution("Rappi"), null, FinanceTestData.cop("0"), now);
        accounts.save(card);
        accounts.save(savings);
        accounts.save(rappiAccount);
    }

    /** Guarda una notificación recibida hace 3 minutos (ya pasó la espera) y la procesa. */
    private CapturedNotification process(String body) {
        Instant receivedAt = Instant.now().minus(3, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        CapturedNotification notification = CapturedNotification.receive(NotificationId.newId(), owner,
                new RawNotification("Mensajes", "85540", body, receivedAt), receivedAt);
        notifications.save(notification);
        processNotification.execute(notification.id());
        return notifications.findById(notification.id()).orElseThrow();
    }

    private Transaction transactionOf(CapturedNotification notification) {
        return transactions.findByIdAndOwner(new TransactionId(notification.transactionId().orElseThrow()), owner)
                .orElseThrow();
    }

    @Test
    void aCardPurchaseIsRecordedInFinanceAsACapturedExpenseWithoutCategory() {
        CapturedNotification result = process("Bancolombia: Compraste COP9.780,00 en TIENDAS ARA con tu T.Cred"
                + " *1234, el 08/11/2026 a las 08:48. Si tienes dudas, encuentranos aqui: 018000931987.");

        assertThat(result.status()).isEqualTo(CaptureStatus.RECORDED);
        assertThat(result.footprint()).hasValueSatisfying(footprint -> {
            assertThat(footprint.productId()).isEqualTo(card.id().value());
            assertThat(footprint.source()).isEqualTo(CaptureSource.BANCOLOMBIA_SMS);
            assertThat(footprint.amount().amount()).isEqualByComparingTo("9780");
        });
        Expense expense = (Expense) transactionOf(result);
        assertThat(expense.source()).isEqualTo(card.id());
        assertThat(expense.category()).isNull();
        assertThat(expense.amount().amount()).isEqualByComparingTo("9780");
        assertThat(expense.details().date()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(expense.details().merchant()).isEqualTo("TIENDAS ARA");
        assertThat(expense.details().channel()).isEqualTo(PaymentChannel.CARD);
        assertThat(expense.origin()).isEqualTo(TransactionOrigin.CAPTURE);
        assertThat(expense.isAmountEstimated()).isFalse();
    }

    @Test
    void aTransferToAnOwnKeyIsATransferBetweenProducts() {
        jdbc.sql("INSERT INTO capture.own_keys (id, user_id, key_value, product_id, created_at)"
                        + " VALUES (?, ?, ?, ?, now())")
                .params(UUID.randomUUID(), owner.value(), "@rp1000000001", rappiAccount.id().value())
                .update();

        CapturedNotification result = process("Bancolombia: ANA, transferiste $176,700.00 a la llave"
                + " @RP1000000001 desde tu cuenta *5678 a Ana Maria Perez Gomez el 05/11/26 a las 13:36. Con Bre-b"
                + " es de una y gratis. Dudas al 018000912345");

        Transfer transfer = (Transfer) transactionOf(result);
        assertThat(transfer.source()).isEqualTo(savings.id());
        assertThat(transfer.destination()).isEqualTo(rappiAccount.id());
        assertThat(transfer.origin()).isEqualTo(TransactionOrigin.CAPTURE);
    }

    @Test
    void theOwnNameFromTheProfileProposesAnOwnTransfer() {
        CapturedNotification result = process("Bancolombia: ANA, transferiste $50,000.00 a la llave 3001112233"
                + " desde tu cuenta *5678 a Maria Perez el 05/11/26 a las 13:36. Con Bre-b es de una y gratis.");

        assertThat(result.status()).isEqualTo(CaptureStatus.REVIEW);
        assertThat(result.reviewReason()).contains(ReviewReason.POSSIBLE_OWN_TRANSFER);
        assertThat(transactionOf(result)).isInstanceOf(Expense.class);
    }

    @Test
    void anUnknownCardGoesToOtherEvenIfFinanceWasNeverSetUp() {
        assertThat(accounts.findOtherAccount(owner)).isEmpty();

        CapturedNotification result = process("Bancolombia: Compraste COP25.005,00 en EDS con tu T.Cred *4321, el"
                + " 03/11/2026 a las 16:44. Si tienes dudas.");

        Expense expense = (Expense) transactionOf(result);
        assertThat(expense.source()).isEqualTo(accounts.findOtherAccount(owner).orElseThrow().id());
    }

    @Test
    void pairedNotificationsAreNoLongerCandidatesAndOtherUsersAreNeverCandidates() {
        Instant moment = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        CapturedNotification sms = recorded(owner, CaptureSource.BANCOLOMBIA_SMS, moment, "sms");
        CapturedNotification other = recorded(new TestAccounts(users, passwordHasher).create().id(),
                CaptureSource.BANCOLOMBIA_SMS, moment, "otro");

        assertThat(notifications.findUnpairedWithMovement(owner, moment.minusSeconds(60), moment.plusSeconds(60)))
                .extracting(CapturedNotification::id).containsExactly(sms.id());

        CapturedNotification wallet = CapturedNotification.receive(NotificationId.newId(), owner,
                new RawNotification("Wallet", "Tiendas Ara", "9.780", moment), moment);
        wallet.markPaired(sms, footprint(CaptureSource.APPLE_WALLET, moment), moment);
        notifications.save(wallet);

        assertThat(notifications.findUnpairedWithMovement(owner, moment.minusSeconds(60), moment.plusSeconds(60)))
                .isEmpty();
        CapturedNotification reloaded = notifications.findById(wallet.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(CaptureStatus.PAIRED);
        assertThat(reloaded.pairedWith()).contains(sms.id());
        assertThat(other.owner()).isNotEqualTo(owner);
    }

    @Test
    void anExactResendOfTheShortcutIsRejectedByTheDatabase() {
        Instant moment = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        RawNotification raw = new RawNotification("Mensajes", "85540", "Bancolombia: texto", moment);
        notifications.save(CapturedNotification.receive(NotificationId.newId(), owner, raw, moment));

        assertThatThrownBy(() -> notifications.save(CapturedNotification.receive(NotificationId.newId(), owner,
                raw, moment))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theQueueProcessesOnlyNotificationsThatPassedTheWait() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String body = "Bancolombia: Compraste COP9.780,00 en TIENDAS ARA con tu T.Cred *1234, el 08/11/2026 a las"
                + " 08:48. Si tienes dudas.";
        CapturedNotification settled = CapturedNotification.receive(NotificationId.newId(), owner,
                new RawNotification("Mensajes", "85540", body, now.minusSeconds(200)), now.minusSeconds(180));
        CapturedNotification fresh = CapturedNotification.receive(NotificationId.newId(), owner,
                new RawNotification("Mensajes", "85540", body, now.minusSeconds(30)), now.minusSeconds(30));
        notifications.save(settled);
        notifications.save(fresh);

        processPending.execute();

        assertThat(notifications.findById(settled.id()).orElseThrow().status()).isEqualTo(CaptureStatus.RECORDED);
        assertThat(notifications.findById(fresh.id()).orElseThrow().isPending()).isTrue();
    }

    @Test
    void theRawTextIsDeletedAfter45DaysAndTheResultIsKept() {
        Instant old = Instant.now().minus(46, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant recent = Instant.now().minus(44, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        CapturedNotification oldOne = recorded(owner, CaptureSource.BANCOLOMBIA_SMS, old, "texto viejo");
        CapturedNotification recentOne = recorded(owner, CaptureSource.BANCOLOMBIA_SMS, recent, "texto reciente");

        assertThat(purgeRaw.execute()).isGreaterThanOrEqualTo(1);

        CapturedNotification purged = notifications.findById(oldOne.id()).orElseThrow();
        assertThat(purged.raw().title()).isEmpty();
        assertThat(purged.raw().body()).isEmpty();
        assertThat(purged.status()).isEqualTo(CaptureStatus.RECORDED);
        assertThat(purged.transactionId()).isEqualTo(oldOne.transactionId());
        assertThat(purged.footprint()).isPresent();
        assertThat(notifications.findById(recentOne.id()).orElseThrow().raw().body()).isEqualTo("texto reciente");
    }

    private CaptureFootprint footprint(CaptureSource source, Instant moment) {
        return new CaptureFootprint(card.id().value(),
                new Money(new BigDecimal("9780.00"), Currency.getInstance("COP")), source, moment);
    }

    private CapturedNotification recorded(UserId user, CaptureSource source, Instant moment, String body) {
        CapturedNotification notification = CapturedNotification.receive(NotificationId.newId(), user,
                new RawNotification("Mensajes", "85540", body, moment), moment);
        notification.markRecorded(UUID.randomUUID(), footprint(source, moment), moment);
        notifications.save(notification);
        return notification;
    }
}
