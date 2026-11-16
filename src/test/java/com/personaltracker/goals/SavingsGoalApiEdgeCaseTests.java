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

/** Casos límite y de seguridad de la API de metas de ahorro (ADR-0018). */
@IntegrationTest
class SavingsGoalApiEdgeCaseTests {

    private static final String GOALS = "/api/v1/goals/savings";
    private static final String MAX_AMOUNT = "99999999999999999.99";

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

    private static Map<String, Object> byDate(String name, String target, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("targetAmount", target);
        body.put("plan", "TARGET_DATE");
        body.put("targetDate", date);
        return body;
    }

    private Map<String, Object> validGoal(String name) {
        return byDate(name, "1000000", today.plusMonths(10).toString());
    }

    private String create(Map<String, Object> body) {
        EntityExchangeResult<String> created = browser.post(GOALS, body);
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        return (String) json(created, "$.id");
    }

    private Map<String, Object> contribution(String date, String amount) {
        return Map.of("date", date, "amount", amount);
    }

    @Test
    void aWriteWithoutTheCsrfTokenIsRejected() {
        EntityExchangeResult<String> result = browser.postWithoutCsrf(GOALS, validGoal("Consola"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((String) json(result, "$.code")).isEqualTo("csrf_invalid");
        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }

    @Test
    void malformedRequestsAnswerBadRequest() {
        assertThat(browser.get(GOALS + "/no-es-un-uuid").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> unknownPlan = validGoal("Consola");
        unknownPlan.put("plan", "WEEKLY");
        assertThat(browser.post(GOALS, unknownPlan).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(browser.post(GOALS, byDate("Consola", "1000", "2026-02-30")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(GOALS, byDate("Consola", "1000.123", today.plusMonths(1).toString())).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(GOALS, byDate("Consola", "-5", today.plusMonths(1).toString())).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post(GOALS, byDate("   ", "1000", today.plusMonths(1).toString())).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        EntityExchangeResult<String> longName =
                browser.post(GOALS, byDate("a".repeat(101), "1000", today.plusMonths(1).toString()));
        assertThat(longName.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Object>) json(longName, "$.errors[*].field")).containsExactly("name");

        assertThat((List<Object>) json(browser.get(GOALS), "$")).isEmpty();
    }

    @Test
    void aNameOfExactlyAHundredCharactersIsAccepted() {
        EntityExchangeResult<String> created =
                browser.post(GOALS, byDate("ñ".repeat(100), "1000", today.plusMonths(1).toString()));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.name")).hasSize(100);
    }

    @Test
    void theLargestAmountIsStoredExactly() {
        String id = create(byDate("Grande", MAX_AMOUNT, today.plusMonths(2).toString()));

        EntityExchangeResult<String> loaded = browser.get(GOALS + "/" + id);

        assertThat((String) json(loaded, "$.targetAmount")).isEqualTo(MAX_AMOUNT);
        assertThat((String) json(loaded, "$.recommendedMonthlyAmount")).isEqualTo("50000000000000000.00");
    }

    /** H-04: con un mes o menos, el aporte recomendado es exactamente lo que falta, aun con el monto máximo. */
    @Test
    void theLargestAmountWithOneMonthLeftCanStillBeCreated() {
        EntityExchangeResult<String> created =
                browser.post(GOALS, byDate("Grande", MAX_AMOUNT, today.plusMonths(1).toString()));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.recommendedMonthlyAmount")).isEqualTo(MAX_AMOUNT);
        assertThat(browser.get(GOALS).getStatus()).isEqualTo(HttpStatus.OK);
    }

    /** La suma de abonos no cabe en un monto: la petición se rechaza y la transacción se revierte. */
    @Test
    void contributionsThatOverflowTheAmountAreRejectedAndRolledBack() {
        String id = create(byDate("Grande", MAX_AMOUNT, today.plusMonths(2).toString()));
        browser.post(GOALS + "/" + id + "/contributions", contribution(today.toString(), MAX_AMOUNT));

        EntityExchangeResult<String> overflow =
                browser.post(GOALS + "/" + id + "/contributions", contribution(today.toString(), "0.01"));

        assertThat(overflow.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(overflow, "$.code")).isEqualTo("money.amount_invalid");
        EntityExchangeResult<String> loaded = browser.get(GOALS + "/" + id);
        assertThat(loaded.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(loaded, "$.savedAmount")).isEqualTo(MAX_AMOUNT);
        assertThat((List<Object>) json(loaded, "$.contributions")).hasSize(1);
        assertThat((Boolean) json(loaded, "$.completed")).isTrue();
    }

    @Test
    void aContributionOfAnotherGoalOfTheSameUserIsNotFoundAndStays() {
        String goal = create(validGoal("Consola"));
        String other = create(validGoal("Viaje"));
        EntityExchangeResult<String> added =
                browser.post(GOALS + "/" + other + "/contributions", contribution(today.toString(), "5000"));
        String contributionId = (String) json(added, "$.contributions[0].id");

        EntityExchangeResult<String> removed = browser.delete(GOALS + "/" + goal + "/contributions/" + contributionId);

        assertThat(removed.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) json(removed, "$.code")).isEqualTo("goals.contribution.not_found");
        assertThat((String) json(browser.get(GOALS + "/" + other), "$.savedAmount")).isEqualTo("5000.00");
    }

    @Test
    void reachingTheTargetCompletesTheGoal() {
        String id = create(validGoal("Consola"));

        EntityExchangeResult<String> completed =
                browser.post(GOALS + "/" + id + "/contributions", contribution(today.toString(), "1000000"));

        assertThat((Boolean) json(completed, "$.completed")).isTrue();
        assertThat((String) json(completed, "$.remainingAmount")).isEqualTo("0.00");
        assertThat((String) json(completed, "$.recommendedMonthlyAmount")).isEqualTo("0.00");
        assertThat((String) json(completed, "$.progressPercent")).isEqualTo("100.0");
    }

    @Test
    void theRateKeepsFourDecimalsAndRejectsMore() {
        String id = create(validGoal("Fondo"));

        EntityExchangeResult<String> precise =
                browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "10.1234"));
        assertThat((String) json(precise, "$.ratePercent")).isEqualTo("10.1234");
        assertThat((String) json(browser.get(GOALS + "/" + id), "$.rateChanges[0].ratePercent"))
                .isEqualTo("10.1234");

        assertThat(browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "10.12345")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "10,5")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        EntityExchangeResult<String> limit = browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "1000"));
        assertThat(limit.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theListComesNewestFirstAndEachGoalHasItsOwnData() {
        String first = create(validGoal("Primera"));
        String second = create(validGoal("Segunda"));
        browser.post(GOALS + "/" + first + "/contributions", contribution(today.toString(), "1000"));

        EntityExchangeResult<String> list = browser.get(GOALS);

        assertThat((List<Object>) json(list, "$[*].id")).containsExactly(second, first);
        assertThat((List<Object>) json(list, "$[*].savedAmount")).containsExactly("0.00", "1000.00");
    }

    /**
     * H-01: una fecha que PostgreSQL no puede guardar (después del año 5874897 o antes del 4713 a. C.)
     * se rechaza como dato inválido antes de llegar a la base.
     */
    @Test
    void datesTheDatabaseCannotStoreAreRejectedAsInvalid() {
        EntityExchangeResult<String> farFuture = browser.post(GOALS, byDate("Lejana", "1000", "+5874898-01-01"));
        assertThat(farFuture.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(farFuture, "$.code")).isEqualTo("goals.savings_goal.target_date_invalid");

        String id = create(validGoal("Consola"));
        EntityExchangeResult<String> farPast =
                browser.post(GOALS + "/" + id + "/contributions", contribution("-4800-01-01", "1000"));
        assertThat(farPast.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(farPast, "$.code")).isEqualTo("goals.contribution.date_invalid");
        assertThat((String) json(browser.get(GOALS + "/" + id), "$.savedAmount")).isEqualTo("0.00");
    }

    /** H-02: una fecha objetivo a más de 100 años se rechaza; justo a 100 años se acepta. */
    @Test
    void aTargetDateMoreThanAHundredYearsAwayIsRejected() {
        EntityExchangeResult<String> result =
                browser.post(GOALS, byDate("Lejana", "1000", today.plusYears(101).toString()));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(result, "$.code")).isEqualTo("goals.savings_goal.target_date_invalid");

        EntityExchangeResult<String> atTheLimit =
                browser.post(GOALS, byDate("Lejana", "1200", today.plusYears(100).toString()));
        assertThat(atTheLimit.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((Integer) json(atTheLimit, "$.monthsRemaining")).isEqualTo(1200);
        assertThat((String) json(atTheLimit, "$.recommendedMonthlyAmount")).isEqualTo("1.00");
    }

    /** H-10: cada regla responde con su propio código, para que el cliente marque el campo. */
    @Test
    void everyBusinessRuleAnswersWithItsOwnCode() {
        EntityExchangeResult<String> zeroTarget = browser.post(GOALS, byDate("Consola", "0", today.plusMonths(2).toString()));
        assertThat((String) json(zeroTarget, "$.code")).isEqualTo("goals.savings_goal.target_invalid");

        EntityExchangeResult<String> pastDate = browser.post(GOALS, byDate("Consola", "1000", today.toString()));
        assertThat((String) json(pastDate, "$.code")).isEqualTo("goals.savings_goal.target_date_invalid");

        Map<String, Object> zeroMonthly = new HashMap<>();
        zeroMonthly.put("name", "Consola");
        zeroMonthly.put("targetAmount", "1000");
        zeroMonthly.put("plan", "MONTHLY_AMOUNT");
        zeroMonthly.put("monthlyAmount", "0");
        assertThat((String) json(browser.post(GOALS, zeroMonthly), "$.code"))
                .isEqualTo("goals.savings_goal.monthly_amount_invalid");

        String id = create(validGoal("Consola"));
        EntityExchangeResult<String> zeroContribution =
                browser.post(GOALS + "/" + id + "/contributions", contribution(today.toString(), "0"));
        assertThat((String) json(zeroContribution, "$.code")).isEqualTo("goals.contribution.amount_invalid");
        EntityExchangeResult<String> futureContribution = browser.post(GOALS + "/" + id + "/contributions",
                contribution(today.plusDays(1).toString(), "1000"));
        assertThat((String) json(futureContribution, "$.code")).isEqualTo("goals.contribution.date_invalid");
    }
}
