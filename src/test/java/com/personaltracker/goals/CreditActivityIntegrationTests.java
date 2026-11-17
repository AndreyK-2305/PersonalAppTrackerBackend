package com.personaltracker.goals;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Pagos, abonos, ajustes de tasa y deshacer de punta a punta (ADR-0018). El crédito es el del ejemplo:
 * $60.000.000 al 18 % EA en 48 cuotas de $1.720.956, con la primera hace 14 meses.
 */
@IntegrationTest
class CreditActivityIntegrationTests {

    private static final String GOALS = "/api/v1/goals/financed";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private BrowserSession browser;
    private LocalDate today;
    private LocalDate first;
    private String goal;

    @BeforeEach
    void setUp() {
        User user = new TestAccounts(users, passwordHasher).create();
        browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);
        today = LocalDate.now(User.DEFAULT_TIME_ZONE);
        first = today.minusMonths(14);
        EntityExchangeResult<String> created = browser.post(GOALS, goalBody("Carro"));
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        goal = GOALS + "/" + json(created, "$.goal.id");
    }

    private Map<String, Object> goalBody(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("assetValue", "60000000");
        body.put("ratePercent", "18");
        body.put("installments", 48);
        body.put("firstDueDate", first.toString());
        return body;
    }

    private Map<String, Object> amountOn(String amount, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("date", date.toString());
        return body;
    }

    private EntityExchangeResult<String> payThrough(int number) {
        EntityExchangeResult<String> result = browser.post(goal + "/payments/through", Map.of("installmentNumber", number));
        assertThat(result.getStatus()).isEqualTo(HttpStatus.CREATED);
        return result;
    }

    private void assertCode(EntityExchangeResult<String> result, HttpStatus status, String code) {
        assertThat(result.getStatus()).as(code).isEqualTo(status);
        assertThat((String) json(result, "$.code")).isEqualTo(code);
    }

    @Test
    void aNewCreditStartsWithoutRecordsAndWithItsFirstInstallmentPending() {
        EntityExchangeResult<String> detail = browser.get(goal);

        assertThat((String) json(detail, "$.goal.progress.status")).isEqualTo("OVERDUE");
        assertThat((Integer) json(detail, "$.goal.progress.paidInstallments")).isZero();
        assertThat((Integer) json(detail, "$.goal.progress.pendingInstallments")).isEqualTo(48);
        assertThat((String) json(detail, "$.goal.progress.balance")).isEqualTo("60000000.00");
        assertThat((String) json(detail, "$.goal.progress.interestSavings")).isEqualTo("0.00");
        assertThat((Boolean) json(detail, "$.goal.progress.hasRecords")).isFalse();
        assertThat((Integer) json(detail, "$.goal.progress.nextInstallment.number")).isEqualTo(1);
        assertThat((Boolean) json(detail, "$.schedule[0].paid")).isFalse();
        assertThat((List<Object>) json(detail, "$.records")).isEmpty();
    }

    @Test
    void payingAnInstallmentMarksItAndReturnsTheUpdatedPlan() {
        EntityExchangeResult<String> paid = browser.post(goal + "/payments", amountOn("1720956", today));

        assertThat(paid.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((Integer) json(paid, "$.goal.progress.paidInstallments")).isEqualTo(1);
        assertThat((String) json(paid, "$.goal.progress.balance")).isEqualTo("59112349.82");
        assertThat((String) json(paid, "$.goal.progress.paidInterest")).isEqualTo("833305.82");
        assertThat((String) json(paid, "$.goal.progress.paidSoFar")).isEqualTo("1720956.00");
        assertThat((Boolean) json(paid, "$.goal.progress.hasRecords")).isTrue();
        assertThat((Boolean) json(paid, "$.schedule[0].paid")).isTrue();
        assertThat((String) json(paid, "$.schedule[0].paidOn")).isEqualTo(today.toString());
        assertThat((String) json(paid, "$.schedule[0].extraPrincipal")).isEqualTo("0.00");
        assertThat((Boolean) json(paid, "$.schedule[1].paid")).isFalse();
        assertThat((String) json(paid, "$.records[0].kind")).isEqualTo("PAYMENT");
        assertThat((Integer) json(paid, "$.records[0].installmentNumber")).isEqualTo(1);
        assertThat((String) json(paid, "$.records[0].amount")).isEqualTo("1720956.00");
        assertThat((Boolean) json(paid, "$.records[0].undoable")).isTrue();
        assertThat((String) json(browser.get(goal), "$.goal.progress.balance")).isEqualTo("59112349.82");
    }

    @Test
    void payingMoreThanTheInstallmentPrepaysTheRest() {
        Map<String, Object> body = amountOn("6720956", today);
        body.put("excessMode", "REDUCE_INSTALLMENT");

        EntityExchangeResult<String> paid = browser.post(goal + "/payments", body);

        assertThat(paid.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(paid, "$.schedule[0].payment")).isEqualTo("6720956.00");
        assertThat((String) json(paid, "$.schedule[0].extraPrincipal")).isEqualTo("5000000.00");
        assertThat((String) json(paid, "$.schedule[0].balance")).isEqualTo("54112349.82");
        assertThat((Integer) json(paid, "$.goal.progress.pendingInstallments")).isEqualTo(47);
        assertThat((String) json(paid, "$.records[0].mode")).isEqualTo("REDUCE_INSTALLMENT");
    }

    @Test
    void theWholeLifeOfACreditMatchesTheExampleOfTheDesign() {
        EntityExchangeResult<String> twelve = payThrough(12);
        assertThat((Integer) json(twelve, "$.goal.progress.paidInstallments")).isEqualTo(12);
        assertThat((String) json(twelve, "$.goal.progress.balance")).isEqualTo("48495673.87");
        assertThat((String) json(twelve, "$.goal.progress.paidInterest")).isEqualTo("9147145.87");
        assertThat((String) json(twelve, "$.goal.progress.status")).isEqualTo("OVERDUE");
        assertThat((String) json(twelve, "$.schedule[0].paidOn")).isEqualTo(first.toString());
        assertThat((List<Object>) json(twelve, "$.records")).hasSize(12);
        assertThat((List<Object>) json(twelve, "$.records[?(@.undoable == true)]")).hasSize(1);
        assertThat((Integer) json(twelve, "$.records[0].installmentNumber")).isEqualTo(12);

        EntityExchangeResult<String> prepaid = browser.post(goal + "/prepayments", amountOn("5000000", today));
        assertThat(prepaid.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((Integer) json(prepaid, "$.goal.progress.pendingInstallments")).isEqualTo(32);
        assertThat((String) json(prepaid, "$.goal.progress.interestSavings")).isEqualTo("3006724.63");
        assertThat((String) json(prepaid, "$.goal.summary.recommendedInstallment")).isEqualTo("1720956.00");
        assertThat((List<Object>) json(prepaid, "$.schedule")).hasSize(44);
        assertThat((String) json(prepaid, "$.schedule[43].payment")).isEqualTo("598024.71");
        assertThat((String) json(prepaid, "$.records[0].kind")).isEqualTo("PREPAYMENT");
        assertThat((String) json(prepaid, "$.records[0].mode")).isEqualTo("REDUCE_TERM");
        String prepaymentId = (String) json(prepaid, "$.records[0].id");

        EntityExchangeResult<String> undone = browser.delete(goal + "/records/" + prepaymentId);
        assertThat(undone.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) json(undone, "$.goal.progress.pendingInstallments")).isEqualTo(36);
        assertThat((String) json(undone, "$.goal.progress.interestSavings")).isEqualTo("0.00");

        Map<String, Object> reduceInstallment = amountOn("5000000", today);
        reduceInstallment.put("mode", "REDUCE_INSTALLMENT");
        EntityExchangeResult<String> reduced = browser.post(goal + "/prepayments", reduceInstallment);
        assertThat((Integer) json(reduced, "$.goal.progress.pendingInstallments")).isEqualTo(36);
        assertThat((String) json(reduced, "$.goal.summary.recommendedInstallment")).isEqualTo("1543522.00");
        assertThat((String) json(reduced, "$.goal.progress.interestSavings")).isEqualTo("1387619.50");
        browser.delete(goal + "/records/" + json(reduced, "$.records[0].id"));

        EntityExchangeResult<String> adjusted = browser.post(goal + "/rate-adjustments", Map.of("ratePercent", "20"));
        assertThat(adjusted.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(adjusted, "$.goal.progress.currentRatePercent")).isEqualTo("20");
        assertThat((String) json(adjusted, "$.goal.ratePercent")).isEqualTo("18");
        assertThat((String) json(adjusted, "$.goal.summary.recommendedInstallment")).isEqualTo("1762283.00");
        assertThat((String) json(adjusted, "$.goal.progress.interestSavings")).isEqualTo("-1487778.71");
        assertThat((String) json(adjusted, "$.records[0].kind")).isEqualTo("RATE_ADJUSTMENT");
        assertThat((String) json(adjusted, "$.records[0].ratePercent")).isEqualTo("20");
        assertThat((String) json(adjusted, "$.records[0].date")).isEqualTo(today.toString());

        EntityExchangeResult<String> list = browser.get(GOALS);
        assertThat((Integer) json(list, "$[0].progress.paidInstallments")).isEqualTo(12);
        assertThat((String) json(list, "$[0].summary.installmentAmount")).isEqualTo("1762283.00");
    }

    @Test
    void aRateAdjustmentCanRegisterTheNewActualInstallment() {
        payThrough(12);

        EntityExchangeResult<String> adjusted = browser.post(goal + "/rate-adjustments",
                Map.of("ratePercent", "20", "actualInstallment", "1812283"));

        assertThat(adjusted.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(adjusted, "$.goal.summary.installmentAmount")).isEqualTo("1812283.00");
        assertThat((String) json(adjusted, "$.goal.summary.extrasPerInstallment")).isEqualTo("50000.00");
        assertThat((String) json(adjusted, "$.records[0].actualInstallment")).isEqualTo("1812283.00");
    }

    @Test
    void payingThroughTheLastInstallmentPaysOffTheCredit() {
        EntityExchangeResult<String> paidOff = payThrough(48);

        assertThat((String) json(paidOff, "$.goal.progress.status")).isEqualTo("PAID_OFF");
        assertThat((String) json(paidOff, "$.goal.progress.balance")).isEqualTo("0.00");
        assertThat((Integer) json(paidOff, "$.goal.progress.pendingInstallments")).isZero();
        assertThat((Object) json(paidOff, "$.goal.progress.nextInstallment")).isNull();
        // Las cuotas que aún no vencen quedan con la fecha de hoy, no con una fecha futura.
        assertThat((String) json(paidOff, "$.schedule[47].paidOn")).isEqualTo(today.toString());
        assertCode(browser.post(goal + "/payments", amountOn("1720956", today)), HttpStatus.UNPROCESSABLE_CONTENT,
                "goals.financed_record.credit_paid_off");
    }

    @Test
    void everyRuleAnswersWithItsOwnCodeAndSavesNothing() {
        payThrough(2);

        assertCode(browser.post(goal + "/payments", amountOn("1720955.99", today)),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.payment_amount_invalid");
        assertCode(browser.post(goal + "/payments", amountOn("1720956", today.plusDays(1))),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.date_invalid");
        assertCode(browser.post(goal + "/prepayments", amountOn("60000000", today)),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.prepayment_amount_invalid");
        assertCode(browser.post(goal + "/prepayments", amountOn("1000", LocalDate.parse("-4800-01-01"))),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.date_invalid");
        assertCode(browser.post(goal + "/rate-adjustments", Map.of("ratePercent", "20", "actualInstallment", "1000")),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.actual_installment_invalid");
        assertCode(browser.post(goal + "/rate-adjustments", Map.of("ratePercent", "1500")),
                HttpStatus.UNPROCESSABLE_CONTENT, "rate.invalid");
        assertCode(browser.post(goal + "/payments/through", Map.of("installmentNumber", 2)),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.installment_number_invalid");
        assertCode(browser.post(goal + "/payments/through", Map.of("installmentNumber", 49)),
                HttpStatus.UNPROCESSABLE_CONTENT, "goals.financed_record.installment_number_invalid");

        EntityExchangeResult<String> detail = browser.get(goal);
        assertThat((List<Object>) json(detail, "$.records")).hasSize(2);
        assertThat((Integer) json(detail, "$.goal.progress.paidInstallments")).isEqualTo(2);
    }

    @Test
    void validatesTheShapeOfEachRequest() {
        assertThat(browser.post(goal + "/payments", Map.of("date", today.toString())).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(goal + "/payments", Map.of("amount", "1720956")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(goal + "/payments", amountOn("1720956,00", today)).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> unknownMode = amountOn("1000", today);
        unknownMode.put("mode", "OTHER");
        assertThat(browser.post(goal + "/prepayments", unknownMode).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(goal + "/payments/through", Map.of("installmentNumber", 0)).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(goal + "/payments/through", Map.of("installmentNumber", 601)).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(goal + "/rate-adjustments", Map.of("ratePercent", "20,5")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.delete(goal + "/records/no-es-un-uuid").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Object>) json(browser.get(goal), "$.records")).isEmpty();
    }

    @Test
    void onlyTheLastRecordCanBeUndone() {
        EntityExchangeResult<String> paid = payThrough(3);
        String oldest = (String) json(paid, "$.records[2].id");
        String latest = (String) json(paid, "$.records[0].id");

        assertCode(browser.delete(goal + "/records/" + oldest), HttpStatus.UNPROCESSABLE_CONTENT,
                "goals.financed_record.not_last");
        assertCode(browser.delete(goal + "/records/" + java.util.UUID.randomUUID()), HttpStatus.NOT_FOUND,
                "goals.financed_record.not_found");

        EntityExchangeResult<String> undone = browser.delete(goal + "/records/" + latest);
        assertThat(undone.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) json(undone, "$.goal.progress.paidInstallments")).isEqualTo(2);
        assertThat((Boolean) json(undone, "$.schedule[2].paid")).isFalse();
        assertThat((List<Object>) json(undone, "$.records")).hasSize(2);
    }

    @Test
    void withRecordsOnlyTheNameCanBeEdited() {
        payThrough(1);
        Map<String, Object> otherRate = goalBody("Carro");
        otherRate.put("ratePercent", "19");

        assertCode(browser.put(goal, otherRate), HttpStatus.UNPROCESSABLE_CONTENT,
                "goals.financed_goal.terms_locked");
        EntityExchangeResult<String> renamed = browser.put(goal, goalBody("Carro rojo"));

        assertThat(renamed.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(renamed, "$.goal.name")).isEqualTo("Carro rojo");
        assertThat((String) json(renamed, "$.goal.ratePercent")).isEqualTo("18");
        assertThat((Integer) json(renamed, "$.goal.progress.paidInstallments")).isEqualTo(1);
    }

    @Test
    void deletingTheGoalDeletesItsRecords() {
        payThrough(5);

        assertThat(browser.delete(goal).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get(goal).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUserCannotSeeOrChangeTheCredit() {
        EntityExchangeResult<String> paid = payThrough(2);
        String record = (String) json(paid, "$.records[0].id");

        User other = new TestAccounts(users, passwordHasher).create();
        BrowserSession intruder = new BrowserSession(client);
        intruder.login(other.email().value(), TestAccounts.PASSWORD);

        assertThat(intruder.post(goal + "/payments", amountOn("1720956", today)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.post(goal + "/payments/through", Map.of("installmentNumber", 5)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.post(goal + "/prepayments", amountOn("1000", today)).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.post(goal + "/rate-adjustments", Map.of("ratePercent", "20")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.delete(goal + "/records/" + record).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        EntityExchangeResult<String> detail = browser.get(goal);
        assertThat((List<Object>) json(detail, "$.records")).hasSize(2);
    }

    @Test
    void requiresASessionAndTheCsrfToken() {
        BrowserSession anonymous = new BrowserSession(client);
        assertThat(anonymous.post(goal + "/payments", amountOn("1720956", today)).getStatus())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        assertThat(browser.postWithoutCsrf(goal + "/payments", amountOn("1720956", today)).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(browser.postWithoutCsrf(goal + "/prepayments", amountOn("1000", today)).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((List<Object>) json(browser.get(goal), "$.records")).isEmpty();
    }
}
