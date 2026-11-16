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

/** Metas financiadas de punta a punta: API, reglas, tabla de amortización y aislamiento (ADR-0018). */
@IntegrationTest
class FinancedGoalIntegrationTests {

    private static final String GOALS = "/api/v1/goals/financed";
    private static final String SIMULATIONS = GOALS + "/simulations";

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private BrowserSession browser;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        User user = new TestAccounts(users, passwordHasher).create();
        browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);
        today = LocalDate.now(User.DEFAULT_TIME_ZONE);
    }

    /** $60.000.000 al 18 % EA en 48 cuotas desde 2027-02-15: el ejemplo del diseño. */
    private static Map<String, Object> terms() {
        Map<String, Object> body = new HashMap<>();
        body.put("assetValue", "60000000");
        body.put("ratePercent", "18");
        body.put("installments", 48);
        body.put("firstDueDate", "2027-02-15");
        return body;
    }

    private static Map<String, Object> goal(String name) {
        Map<String, Object> body = terms();
        body.put("name", name);
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        if (value == null) {
            body.remove(field);
        } else {
            body.put(field, value);
        }
        return body;
    }

    private String create(Map<String, Object> body) {
        EntityExchangeResult<String> created = browser.post(GOALS, body);
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        return (String) json(created, "$.goal.id");
    }

    @Test
    void createsAGoalAndReturnsItsAmortizationSchedule() {
        EntityExchangeResult<String> created = browser.post(GOALS, goal("  Carro  "));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.goal.name")).isEqualTo("Carro");
        assertThat((String) json(created, "$.goal.assetValue")).isEqualTo("60000000.00");
        assertThat((String) json(created, "$.goal.downPayment")).isEqualTo("0.00");
        assertThat((String) json(created, "$.goal.currency")).isEqualTo("COP");
        assertThat((String) json(created, "$.goal.ratePercent")).isEqualTo("18");
        assertThat((Integer) json(created, "$.goal.installments")).isEqualTo(48);
        assertThat((String) json(created, "$.goal.firstDueDate")).isEqualTo("2027-02-15");
        assertThat((Object) json(created, "$.goal.actualInstallment")).isNull();
        assertThat((String) json(created, "$.goal.summary.financedAmount")).isEqualTo("60000000.00");
        assertThat((String) json(created, "$.goal.summary.recommendedInstallment")).isEqualTo("1720956.00");
        assertThat((String) json(created, "$.goal.summary.installmentAmount")).isEqualTo("1720956.00");
        assertThat((String) json(created, "$.goal.summary.extrasPerInstallment")).isEqualTo("0.00");
        assertThat((String) json(created, "$.goal.summary.totalInterest")).isEqualTo("22605857.34");
        assertThat((String) json(created, "$.goal.summary.totalPaid")).isEqualTo("82605857.34");
        assertThat((String) json(created, "$.goal.summary.totalCost")).isEqualTo("82605857.34");
        assertThat((String) json(created, "$.goal.summary.lastDueDate")).isEqualTo("2031-01-15");
        assertThat((Integer) json(created, "$.goal.summary.scheduledInstallments")).isEqualTo(48);

        assertThat((List<Object>) json(created, "$.schedule")).hasSize(48);
        assertThat((Integer) json(created, "$.schedule[0].number")).isEqualTo(1);
        assertThat((String) json(created, "$.schedule[0].dueDate")).isEqualTo("2027-02-15");
        assertThat((String) json(created, "$.schedule[0].payment")).isEqualTo("1720956.00");
        assertThat((String) json(created, "$.schedule[0].interest")).isEqualTo("833305.82");
        assertThat((String) json(created, "$.schedule[0].principal")).isEqualTo("887650.18");
        assertThat((String) json(created, "$.schedule[0].balance")).isEqualTo("59112349.82");
        assertThat((String) json(created, "$.schedule[1].dueDate")).isEqualTo("2027-03-15");
        assertThat((String) json(created, "$.schedule[47].payment")).isEqualTo("1720925.34");
        assertThat((String) json(created, "$.schedule[47].balance")).isEqualTo("0.00");

        String id = (String) json(created, "$.goal.id");
        EntityExchangeResult<String> loaded = browser.get(GOALS + "/" + id);
        assertThat(loaded.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(loaded, "$.schedule[47].payment")).isEqualTo("1720925.34");
    }

    @Test
    void theDownPaymentAndTheActualInstallmentChangeThePlan() {
        Map<String, Object> body = goal("Carro");
        body.put("assetValue", "70000000");
        body.put("downPayment", "10000000");
        body.put("actualInstallment", "1760956");

        EntityExchangeResult<String> created = browser.post(GOALS, body);

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.goal.actualInstallment")).isEqualTo("1760956.00");
        assertThat((String) json(created, "$.goal.summary.financedAmount")).isEqualTo("60000000.00");
        assertThat((String) json(created, "$.goal.summary.recommendedInstallment")).isEqualTo("1720956.00");
        assertThat((String) json(created, "$.goal.summary.installmentAmount")).isEqualTo("1760956.00");
        assertThat((String) json(created, "$.goal.summary.extrasPerInstallment")).isEqualTo("40000.00");
        assertThat((String) json(created, "$.goal.summary.totalExtras")).isEqualTo("1920000.00");
        assertThat((String) json(created, "$.goal.summary.totalCost")).isEqualTo("94525857.34");
        assertThat((String) json(created, "$.schedule[0].payment")).isEqualTo("1760956.00");
        assertThat((String) json(created, "$.schedule[0].extras")).isEqualTo("40000.00");
        assertThat((String) json(created, "$.schedule[0].principal")).isEqualTo("887650.18");
    }

    @Test
    void theListBringsTheSummaryOfEachGoalNewestFirstWithoutTheSchedule() {
        String first = create(goal("Primera"));
        String second = create(with(goal("Segunda"), "installments", 12));

        EntityExchangeResult<String> list = browser.get(GOALS);

        assertThat(list.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) json(list, "$[*].id")).containsExactly(second, first);
        assertThat((List<Object>) json(list, "$[*].summary.scheduledInstallments")).containsExactly(12, 48);
        assertThat(list.getResponseBody()).doesNotContain("\"schedule\"");
    }

    @Test
    void simulatingCalculatesThePlanWithoutSavingAnything() {
        EntityExchangeResult<String> simulated = browser.post(SIMULATIONS, terms());

        assertThat(simulated.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(simulated, "$.currency")).isEqualTo("COP");
        assertThat((String) json(simulated, "$.summary.recommendedInstallment")).isEqualTo("1720956.00");
        assertThat((String) json(simulated, "$.summary.totalInterest")).isEqualTo("22605857.34");
        assertThat((List<Object>) json(simulated, "$.schedule")).hasSize(48);
        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }

    @Test
    void simulatingAppliesTheSameRulesAndNeedsTheCsrfToken() {
        EntityExchangeResult<String> lowInstallment =
                browser.post(SIMULATIONS, with(terms(), "actualInstallment", "1720955.99"));
        assertThat(lowInstallment.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(lowInstallment, "$.code")).isEqualTo("goals.financed_goal.actual_installment_invalid");

        assertThat(browser.post(SIMULATIONS, with(terms(), "installments", null)).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.postWithoutCsrf(SIMULATIONS, terms()).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void editsTheGoalAndRecalculatesThePlan() {
        String id = create(goal("Carro"));
        Map<String, Object> changes = with(with(goal("Carro nuevo"), "installments", 24), "ratePercent", "0");

        EntityExchangeResult<String> updated = browser.put(GOALS + "/" + id, changes);

        assertThat(updated.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(updated, "$.goal.name")).isEqualTo("Carro nuevo");
        assertThat((String) json(updated, "$.goal.ratePercent")).isEqualTo("0");
        assertThat((String) json(updated, "$.goal.summary.recommendedInstallment")).isEqualTo("2500000.00");
        assertThat((String) json(updated, "$.goal.summary.totalInterest")).isEqualTo("0.00");
        assertThat((List<Object>) json(updated, "$.schedule")).hasSize(24);
        assertThat((List<Object>) json(browser.get(GOALS), "$")).hasSize(1);
    }

    @Test
    void aCreditThatAlreadyStartedCanBeRegistered() {
        String start = today.minusYears(2).toString();

        EntityExchangeResult<String> created = browser.post(GOALS, with(goal("Carro"), "firstDueDate", start));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.schedule[0].dueDate")).isEqualTo(start);
    }

    @Test
    void everyBusinessRuleAnswersWithItsOwnCode() {
        assertCode(with(goal("Carro"), "assetValue", "0"), "goals.financed_goal.asset_value_invalid");
        assertCode(with(goal("Carro"), "downPayment", "60000000"), "goals.financed_goal.down_payment_invalid");
        assertCode(with(goal("Carro"), "actualInstallment", "1000"), "goals.financed_goal.actual_installment_invalid");
        assertCode(with(goal("Carro"), "firstDueDate", today.plusYears(100).plusDays(1).toString()),
                "goals.financed_goal.first_due_date_invalid");
        assertCode(with(goal("Carro"), "firstDueDate", "+5874898-01-01"),
                "goals.financed_goal.first_due_date_invalid");
        assertCode(with(goal("Carro"), "firstDueDate", "-4800-01-01"), "goals.financed_goal.first_due_date_invalid");
        assertCode(with(goal("Carro"), "ratePercent", "1500"), "rate.invalid");
        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }

    private void assertCode(Map<String, Object> body, String code) {
        EntityExchangeResult<String> result = browser.post(GOALS, body);
        assertThat(result.getStatus()).as(code).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(result, "$.code")).isEqualTo(code);
    }

    @Test
    void validatesTheShapeOfTheRequest() {
        assertField(with(goal("Carro"), "name", "   "), "name");
        assertField(with(goal("Carro"), "name", "a".repeat(101)), "name");
        assertField(with(goal("Carro"), "assetValue", "1000.123"), "assetValue");
        assertField(with(goal("Carro"), "assetValue", null), "assetValue");
        assertField(with(goal("Carro"), "downPayment", "-1"), "downPayment");
        assertField(with(goal("Carro"), "ratePercent", "18,5"), "ratePercent");
        assertField(with(goal("Carro"), "installments", 0), "installments");
        assertField(with(goal("Carro"), "installments", 601), "installments");
        assertField(with(goal("Carro"), "firstDueDate", null), "firstDueDate");
        assertField(with(goal("Carro"), "actualInstallment", "abc"), "actualInstallment");
        assertThat(browser.post(GOALS, with(goal("Carro"), "firstDueDate", "2027-02-30")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.get(GOALS + "/no-es-un-uuid").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }

    private void assertField(Map<String, Object> body, String field) {
        EntityExchangeResult<String> result = browser.post(GOALS, body);
        assertThat(result.getStatus()).as(field).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Object>) json(result, "$.errors[*].field")).contains(field);
    }

    /** Un plan cuyos totales no caben en un monto se rechaza y la meta no llega a guardarse. */
    @Test
    void aPlanThatDoesNotFitInAnAmountIsRejectedAndNothingIsSaved() {
        Map<String, Object> huge = goal("Imposible");
        huge.put("assetValue", "99999999999999999.99");
        huge.put("ratePercent", "1000");
        huge.put("installments", 600);

        EntityExchangeResult<String> result = browser.post(GOALS, huge);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(result, "$.code")).isEqualTo("money.amount_invalid");
        EntityExchangeResult<String> list = browser.get(GOALS);
        assertThat(list.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) json(list, "$")).isEmpty();
    }

    @Test
    void theLongestTermAnswersWithItsSixHundredInstallments() {
        EntityExchangeResult<String> created = browser.post(GOALS, with(goal("Casa"), "installments", 600));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<Object>) json(created, "$.schedule")).hasSize(600);
        assertThat((String) json(created, "$.schedule[599].balance")).isEqualTo("0.00");
    }

    @Test
    void deletesTheGoal() {
        String id = create(goal("Carro"));

        assertThat(browser.delete(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) json(browser.get(GOALS + "/" + id), "$.code")).isEqualTo("goals.financed_goal.not_found");
    }

    @Test
    void anotherUserCannotSeeOrChangeTheGoal() {
        String id = create(goal("Carro"));

        User other = new TestAccounts(users, passwordHasher).create();
        BrowserSession intruder = new BrowserSession(client);
        intruder.login(other.email().value(), TestAccounts.PASSWORD);

        assertThat((List<Object>) json(intruder.get(GOALS), "$")).isEmpty();
        assertThat(intruder.get(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.put(GOALS + "/" + id, goal("Mía")).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.delete(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat((String) json(browser.get(GOALS + "/" + id), "$.goal.name")).isEqualTo("Carro");
    }

    @Test
    void requiresASessionAndTheCsrfToken() {
        BrowserSession anonymous = new BrowserSession(client);
        assertThat(anonymous.get(GOALS).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.post(SIMULATIONS, terms()).getStatus()).isIn(HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN);

        EntityExchangeResult<String> withoutCsrf = browser.postWithoutCsrf(GOALS, goal("Carro"));
        assertThat(withoutCsrf.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }
}
