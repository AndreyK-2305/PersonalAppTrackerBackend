package com.personaltracker.capture;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.capture.application.port.out.CapturedNotificationRepository;
import com.personaltracker.capture.application.usecase.ProcessNotification;
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
import com.personaltracker.finance.domain.model.Transaction;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.Transfer;
import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Ajustes de la captura y revisión por la API (ADR-0017): nombres del perfil, llaves propias,
 * acciones de la bandeja "Por revisar" y descarte al borrar un movimiento captado (ADR-0015).
 */
@IntegrationTest
@SuppressWarnings("unchecked")
class CaptureReviewTests {

    private static final String REVIEWS = "/api/v1/capture/reviews";
    private static final String KEYS = "/api/v1/capture/own-keys";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    AccountRepository accounts;

    @Autowired
    TransactionRepository transactions;

    @Autowired
    CapturedNotificationRepository notifications;

    @Autowired
    ProcessNotification processNotification;

    private UserId owner;
    private BrowserSession browser;
    private CreditCardAccount card;
    private DepositAccount savings;
    private DepositAccount rappiAccount;

    @BeforeEach
    void setUp() {
        User user = new TestAccounts(users, passwordHasher).create();
        owner = user.id();
        browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);
        assertThat(browser.patch("/api/v1/me", Map.of("givenNames", "Ana María", "surnames", "Pérez Gómez"))
                .getStatus()).isEqualTo(HttpStatus.OK);
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

    /** Recibe y procesa un SMS como lo haría la cola, ya pasada la espera. */
    private CapturedNotification sms(String body) {
        Instant receivedAt = Instant.now().minus(3, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        CapturedNotification notification = CapturedNotification.receive(NotificationId.newId(), owner,
                new RawNotification("Mensajes", "85540", body, receivedAt), receivedAt);
        notifications.save(notification);
        processNotification.execute(notification.id());
        return reload(notification);
    }

    private CapturedNotification reload(CapturedNotification notification) {
        return notifications.findById(notification.id()).orElseThrow();
    }

    private Optional<Transaction> transaction(UUID id) {
        return transactions.findByIdAndOwner(new TransactionId(id), owner);
    }

    private static String transferTo(String key, String recipient) {
        return "Bancolombia: ANA, transferiste $50,000.00 a la llave " + key + " desde tu cuenta *5678 a " + recipient
                + " el 05/11/26 a las 13:36. Con Bre-b es de una y gratis. Dudas al 018000912345";
    }

    private static final String DOLLAR_PURCHASE = "Bancolombia: Compraste USD20,00 en ANTHROPIC* CLAUDE SU con tu"
            + " T.Cred *1234, el 17/10/2026 a las 13:26. Si tienes dudas, encuentranos aqui.";

    private EntityExchangeResult<String> post(String path, Object body) {
        return browser.post(path, body);
    }

    @Nested
    class Profile {

        @Test
        void namesAreSavedAndAnEmptyValueClearsThem() {
            EntityExchangeResult<String> profile = browser.get("/api/v1/me");
            assertThat((String) json(profile, "$.givenNames")).isEqualTo("Ana María");
            assertThat((String) json(profile, "$.surnames")).isEqualTo("Pérez Gómez");

            browser.patch("/api/v1/me", Map.of("surnames", ""));

            profile = browser.get("/api/v1/me");
            assertThat((String) json(profile, "$.givenNames")).isEqualTo("Ana María");
            assertThat((Object) json(profile, "$.surnames")).isNull();
        }
    }

    @Nested
    class OwnKeys {

        @Test
        void keysAreNormalizedListedAndRemoved() {
            EntityExchangeResult<String> created = post(KEYS,
                    Map.of("key", "  @RP1000000001 ", "productId", rappiAccount.id().value()));

            assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
            assertThat((String) json(created, "$.key")).isEqualTo("@rp1000000001");
            assertThat((List<Object>) json(browser.get(KEYS), "$[*].key")).containsExactly("@rp1000000001");

            assertThat(post(KEYS, Map.of("key", "@rp1000000001", "productId", savings.id().value())).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(browser.delete(KEYS + "/" + json(created, "$.id")).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat((List<Object>) json(browser.get(KEYS), "$")).isEmpty();
        }

        @Test
        void aKeyMustPointToAnActiveAccountOfTheUser() {
            EntityExchangeResult<String> toCard = post(KEYS, Map.of("key", "3001112233", "productId", card.id().value()));
            EntityExchangeResult<String> toUnknown = post(KEYS,
                    Map.of("key", "3001112233", "productId", UUID.randomUUID()));

            assertThat(toCard.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat((String) json(toCard, "$.code")).isEqualTo("capture.own_key.product_invalid");
            assertThat(toUnknown.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    @Nested
    class Reviews {

        @Test
        void itIsMineTurnsTheExpenseIntoATransfer() {
            CapturedNotification review = sms(transferTo("3001112233", "Maria Perez"));
            UUID expenseId = review.transactionId().orElseThrow();

            EntityExchangeResult<String> listed = browser.get(REVIEWS);
            assertThat((List<Object>) json(listed, "$[*].reason")).containsExactly("POSSIBLE_OWN_TRANSFER");
            assertThat((String) json(listed, "$[0].amount")).isEqualTo("50000.00");

            assertThat(post(REVIEWS + "/" + review.id().value() + "/own-transfer",
                    Map.of("destinationProductId", rappiAccount.id().value())).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            CapturedNotification resolved = reload(review);
            assertThat(resolved.status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(transaction(expenseId)).isEmpty();
            Transfer transfer = (Transfer) transaction(resolved.transactionId().orElseThrow()).orElseThrow();
            assertThat(transfer.source()).isEqualTo(savings.id());
            assertThat(transfer.destination()).isEqualTo(rappiAccount.id());
            assertThat((List<Object>) json(browser.get(REVIEWS), "$")).isEmpty();
        }

        @Test
        void confirmingKeepsTheExpense() {
            CapturedNotification review = sms(transferTo("3001112233", "Ana Perez"));

            assertThat(post(REVIEWS + "/" + review.id().value() + "/confirm", Map.of()).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(reload(review).status()).isEqualTo(CaptureStatus.RECORDED);
            assertThat(transaction(review.transactionId().orElseThrow())).get().isInstanceOf(Expense.class);
        }

        @Test
        void aDollarPurchaseWithoutRateIsRecordedWithTheAmountTheUserWrites() {
            CapturedNotification review = sms(DOLLAR_PURCHASE);
            assertThat(review.reviewReason()).contains(ReviewReason.MISSING_EXCHANGE_RATE);
            String path = REVIEWS + "/" + review.id().value() + "/amount";

            assertThat(post(path, Map.of("amount", "0")).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(post(path, Map.of("amount", "65.120,00")).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(post(path, Map.of("amount", "65120.00")).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

            CapturedNotification resolved = reload(review);
            assertThat(resolved.status()).isEqualTo(CaptureStatus.RECORDED);
            Expense expense = (Expense) transaction(resolved.transactionId().orElseThrow()).orElseThrow();
            assertThat(expense.amount().amount()).isEqualByComparingTo("65120");
            assertThat(expense.source()).isEqualTo(card.id());
            assertThat(expense.isAmountEstimated()).isFalse();
        }

        @Test
        void discardingDeletesTheMovementIfThereIsOne() {
            CapturedNotification withMovement = sms(transferTo("3001112233", "Ana Perez"));
            CapturedNotification withoutMovement = sms("Bancolombia: Pagaste tu tarjeta de credito por $500,000.00.");

            assertThat(post(REVIEWS + "/" + withMovement.id().value() + "/discard", Map.of()).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(post(REVIEWS + "/" + withoutMovement.id().value() + "/discard", Map.of()).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(reload(withMovement).status()).isEqualTo(CaptureStatus.DISCARDED);
            assertThat(reload(withoutMovement).status()).isEqualTo(CaptureStatus.DISCARDED);
            assertThat(transaction(withMovement.transactionId().orElseThrow())).isEmpty();
        }

        @Test
        void anActionThatDoesNotMatchTheReasonIsRejected() {
            CapturedNotification unrecognized = sms("Bancolombia: Pagaste tu tarjeta de credito por $500,000.00.");

            EntityExchangeResult<String> result = post(REVIEWS + "/" + unrecognized.id().value() + "/confirm", Map.of());

            assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat((String) json(result, "$.code")).isEqualTo("capture.review.action_invalid");
        }

        @Test
        void notMineTurnsAnOwnKeyTransferIntoAnExpense() {
            post(KEYS, Map.of("key", "@rp1000000001", "productId", rappiAccount.id().value()));
            CapturedNotification recorded = sms(transferTo("@rp1000000001", "Ana Maria Perez Gomez"));
            UUID transferId = recorded.transactionId().orElseThrow();
            assertThat(transaction(transferId)).get().isInstanceOf(Transfer.class);

            assertThat(post("/api/v1/capture/transactions/" + transferId + "/not-own", Map.of()).getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            CapturedNotification changed = reload(recorded);
            assertThat(changed.status()).isEqualTo(CaptureStatus.RECORDED);
            Expense expense = (Expense) transaction(changed.transactionId().orElseThrow()).orElseThrow();
            assertThat(expense.source()).isEqualTo(savings.id());
            assertThat(transaction(transferId)).isEmpty();
        }

        @Test
        void deletingACapturedMovementInFinanceDiscardsItsNotification() {
            CapturedNotification recorded = sms("Bancolombia: Compraste COP9.780,00 en TIENDAS ARA con tu T.Cred *1234,"
                    + " el 08/11/2026 a las 08:48. Si tienes dudas.");

            assertThat(browser.delete("/api/v1/finance/transactions/" + recorded.transactionId().orElseThrow())
                    .getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

            CapturedNotification discarded = reload(recorded);
            assertThat(discarded.status()).isEqualTo(CaptureStatus.DISCARDED);
            assertThat(discarded.transactionId()).isEmpty();
        }
    }

    @Nested
    class CrossAccess {

        @Test
        void anotherUserCannotSeeOrActOnTheReviewsKeysOrMovements() {
            CapturedNotification review = sms(transferTo("3001112233", "Ana Perez"));
            EntityExchangeResult<String> key = post(KEYS, Map.of("key", "@rp1", "productId", rappiAccount.id().value()));
            User intruderUser = new TestAccounts(users, passwordHasher).create();
            BrowserSession intruder = new BrowserSession(client);
            intruder.login(intruderUser.email().value(), TestAccounts.PASSWORD);
            String reviewPath = REVIEWS + "/" + review.id().value();

            assertThat((List<Object>) json(intruder.get(REVIEWS), "$")).isEmpty();
            assertThat((List<Object>) json(intruder.get(KEYS), "$")).isEmpty();
            assertThat(intruder.post(reviewPath + "/confirm", Map.of()).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.post(reviewPath + "/discard", Map.of()).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.post(reviewPath + "/amount", Map.of("amount", "1000")).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.post(reviewPath + "/own-transfer",
                    Map.of("destinationProductId", rappiAccount.id().value())).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.post("/api/v1/capture/transactions/" + review.transactionId().orElseThrow()
                    + "/not-own", Map.of()).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.delete(KEYS + "/" + json(key, "$.id")).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.post(KEYS, Map.of("key", "@rp2", "productId", rappiAccount.id().value())).getStatus())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

            assertThat(reload(review).status()).isEqualTo(CaptureStatus.REVIEW);
        }

        @Test
        void itIsMineCannotPointToAnotherUsersAccount() {
            CapturedNotification review = sms(transferTo("3001112233", "Ana Perez"));
            User otherUser = new TestAccounts(users, passwordHasher).create();
            DepositAccount foreign = DepositAccount.open(AccountId.newId(), otherUser.id(), AccountKind.SAVINGS,
                    "Ajena", new Institution("Bancolombia"), null, FinanceTestData.cop("0"), Instant.now());
            accounts.save(foreign);

            assertThat(post(REVIEWS + "/" + review.id().value() + "/own-transfer",
                    Map.of("destinationProductId", foreign.id().value())).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(reload(review).status()).isEqualTo(CaptureStatus.REVIEW);
            assertThat(transaction(review.transactionId().orElseThrow())).isPresent();
        }
    }
}
