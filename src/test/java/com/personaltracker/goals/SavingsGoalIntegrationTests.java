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

/** Metas de ahorro de punta a punta: API, reglas, proyección, persistencia y aislamiento (ADR-0018). */
@IntegrationTest
class SavingsGoalIntegrationTests {

    private static final String GOALS = "/api/v1/goals/savings";

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

    private static Map<String, Object> byDate(String name, String target, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("targetAmount", target);
        body.put("plan", "TARGET_DATE");
        body.put("targetDate", date.toString());
        return body;
    }

    private String create(Map<String, Object> body) {
        EntityExchangeResult<String> created = browser.post(GOALS, body);
        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        return (String) json(created, "$.id");
    }

    @Test
    void createsAGoalByDateAndProjectsTheMonthlyContribution() {
        EntityExchangeResult<String> created =
                browser.post(GOALS, byDate("  Consola  ", "1200000", today.plusMonths(12)));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.name")).isEqualTo("Consola");
        assertThat((String) json(created, "$.targetAmount")).isEqualTo("1200000.00");
        assertThat((String) json(created, "$.currency")).isEqualTo("COP");
        assertThat((String) json(created, "$.plan")).isEqualTo("TARGET_DATE");
        assertThat((Integer) json(created, "$.monthsRemaining")).isEqualTo(12);
        assertThat((String) json(created, "$.recommendedMonthlyAmount")).isEqualTo("100000.00");
        assertThat((String) json(created, "$.savedAmount")).isEqualTo("0.00");
        assertThat((String) json(created, "$.progressPercent")).isEqualTo("0.0");
        assertThat((Object) json(created, "$.ratePercent")).isNull();

        String id = (String) json(created, "$.id");
        EntityExchangeResult<String> list = browser.get(GOALS);
        assertThat((List<Object>) json(list, "$[*].id")).containsExactly(id);
    }

    @Test
    void contributionsAddUpAndCanBeRemoved() {
        String id = create(byDate("Viaje", "1000000", today.plusMonths(10)));

        EntityExchangeResult<String> first =
                browser.post(GOALS + "/" + id + "/contributions", Map.of("date", today.toString(), "amount", "250000"));
        assertThat(first.getStatus()).isEqualTo(HttpStatus.CREATED);
        EntityExchangeResult<String> second = browser.post(GOALS + "/" + id + "/contributions",
                Map.of("date", today.minusDays(3).toString(), "amount", "50000.50"));

        assertThat((String) json(second, "$.savedAmount")).isEqualTo("300000.50");
        assertThat((String) json(second, "$.remainingAmount")).isEqualTo("699999.50");
        assertThat((String) json(second, "$.progressPercent")).isEqualTo("30.0");
        assertThat((List<Object>) json(second, "$.contributions[*].amount")).containsExactly("250000.00", "50000.50");

        String contributionId = (String) json(second, "$.contributions[0].id");
        EntityExchangeResult<String> removed = browser.delete(GOALS + "/" + id + "/contributions/" + contributionId);
        assertThat(removed.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(removed, "$.savedAmount")).isEqualTo("50000.50");
    }

    @Test
    void aRateStartsTomorrowAndReplacesAPendingChange() {
        String id = create(byDate("Fondo", "1200000", today.plusMonths(12)));

        browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "8"));
        EntityExchangeResult<String> adjusted = browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "12"));

        assertThat(adjusted.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(adjusted, "$.ratePercent")).isEqualTo("12");
        assertThat((List<Object>) json(adjusted, "$.rateChanges[*].effectiveFrom"))
                .containsExactly(today.plusDays(1).toString());
        assertThat((String) json(adjusted, "$.recommendedMonthlyAmount")).isEqualTo("94888.00");

        EntityExchangeResult<String> off = browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "0"));
        assertThat((Object) json(off, "$.ratePercent")).isNull();
        assertThat((String) json(off, "$.recommendedMonthlyAmount")).isEqualTo("100000.00");
    }

    @Test
    void aMonthlyPlanEstimatesTheDate() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Bicicleta");
        body.put("targetAmount", "900000");
        body.put("plan", "MONTHLY_AMOUNT");
        body.put("monthlyAmount", "300000");

        EntityExchangeResult<String> created = browser.post(GOALS, body);

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) json(created, "$.monthlyAmount")).isEqualTo("300000.00");
        assertThat((String) json(created, "$.estimatedDate")).isEqualTo(today.plusMonths(3).toString());
        assertThat((Boolean) json(created, "$.reachable")).isTrue();
        assertThat((Object) json(created, "$.recommendedMonthlyAmount")).isNull();
    }

    @Test
    void editsTheGoalAndKeepsAnOverdueDateOnlyIfUnchanged() {
        String id = create(byDate("Consola", "1200000", today.plusMonths(6)));

        EntityExchangeResult<String> renamed =
                browser.put(GOALS + "/" + id, byDate("Consola nueva", "1500000", today.plusMonths(6)));
        assertThat(renamed.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((String) json(renamed, "$.name")).isEqualTo("Consola nueva");
        assertThat((String) json(renamed, "$.targetAmount")).isEqualTo("1500000.00");

        EntityExchangeResult<String> past = browser.put(GOALS + "/" + id, byDate("Consola", "1500000", today));
        assertThat(past.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(past, "$.code")).isEqualTo("goals.savings_goal.target_date_invalid");
    }

    @Test
    void validatesTheRequest() {
        Map<String, Object> missingDate = byDate("Consola", "1200000", today.plusMonths(6));
        missingDate.remove("targetDate");
        missingDate.put("monthlyAmount", "100000");
        EntityExchangeResult<String> invalidPlan = browser.post(GOALS, missingDate);
        assertThat(invalidPlan.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Object>) json(invalidPlan, "$.errors[*].field")).containsExactlyInAnyOrder("targetDate",
                "monthlyAmount");

        EntityExchangeResult<String> pastDate = browser.post(GOALS, byDate("Consola", "1200000", today));
        assertThat(pastDate.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        EntityExchangeResult<String> zero = browser.post(GOALS, byDate("Consola", "0", today.plusMonths(2)));
        assertThat(zero.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        String id = create(byDate("Consola", "1200000", today.plusMonths(6)));
        EntityExchangeResult<String> future = browser.post(GOALS + "/" + id + "/contributions",
                Map.of("date", today.plusDays(1).toString(), "amount", "1000"));
        assertThat(future.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        EntityExchangeResult<String> badRate = browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "-1"));
        assertThat(badRate.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        EntityExchangeResult<String> highRate = browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "1500"));
        assertThat(highRate.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(highRate, "$.code")).isEqualTo("rate.invalid");
    }

    @Test
    void deletingAGoalRemovesItWithItsContributions() {
        String id = create(byDate("Consola", "1200000", today.plusMonths(6)));
        browser.post(GOALS + "/" + id + "/contributions", Map.of("date", today.toString(), "amount", "1000"));
        browser.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "5"));

        assertThat(browser.delete(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUserCannotSeeOrChangeTheGoal() {
        String id = create(byDate("Consola", "1200000", today.plusMonths(6)));
        EntityExchangeResult<String> withContribution = browser.post(GOALS + "/" + id + "/contributions",
                Map.of("date", today.toString(), "amount", "1000"));
        String contributionId = (String) json(withContribution, "$.contributions[0].id");

        User other = new TestAccounts(users, passwordHasher).create();
        BrowserSession intruder = new BrowserSession(client);
        intruder.login(other.email().value(), TestAccounts.PASSWORD);

        assertThat((List<Object>) json(intruder.get(GOALS), "$")).isEmpty();
        assertThat(intruder.get(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.put(GOALS + "/" + id, byDate("Mía", "1", today.plusMonths(1))).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.post(GOALS + "/" + id + "/contributions", Map.of("date", today.toString(), "amount", "1"))
                .getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.delete(GOALS + "/" + id + "/contributions/" + contributionId).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.put(GOALS + "/" + id + "/rate", Map.of("ratePercent", "5")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(intruder.delete(GOALS + "/" + id).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat((String) json(browser.get(GOALS + "/" + id), "$.savedAmount")).isEqualTo("1000.00");
    }

    @Test
    void requiresASession() {
        assertThat(new BrowserSession(client).get(GOALS).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
