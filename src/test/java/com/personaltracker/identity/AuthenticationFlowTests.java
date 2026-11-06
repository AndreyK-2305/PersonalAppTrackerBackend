package com.personaltracker.identity;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Flujo de autenticación por HTTP con el registro cerrado (configuración por defecto). */
@IntegrationTest
class AuthenticationFlowTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    JdbcTemplate jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(users, passwordHasher);
    }

    @Test
    void registrationIsClosedByDefault() {
        BrowserSession browser = new BrowserSession(client);

        EntityExchangeResult<String> status = browser.get("/api/v1/auth/registration");
        EntityExchangeResult<String> register = browser.post("/api/v1/auth/register", Map.of(
                "email", TestAccounts.uniqueEmail(), "password", TestAccounts.PASSWORD, "displayName", "Kevin"));

        assertThat(json(status, "$.enabled")).isEqualTo(false);
        assertThat(register.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(register, "$.code")).isEqualTo("identity.registration_closed");
    }

    @Test
    void loginRequiresTheCsrfToken() {
        User user = accounts.create();

        EntityExchangeResult<String> result = new BrowserSession(client).postWithoutCsrf(
                "/api/v1/auth/login", Map.of("email", user.email().value(), "password", TestAccounts.PASSWORD));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(result, "$.code")).isEqualTo("csrf_invalid");
    }

    @Test
    void loginOpensASecureSessionAndLogoutClosesIt() {
        User user = accounts.create();
        BrowserSession browser = new BrowserSession(client);
        browser.get("/api/v1/auth/registration");
        String csrfBeforeLogin = browser.cookie(BrowserSession.CSRF_COOKIE);

        EntityExchangeResult<String> login = browser.post("/api/v1/auth/login",
                Map.of("email", user.email().value().toUpperCase(), "password", TestAccounts.PASSWORD));

        assertThat(login.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(json(login, "$.email")).isEqualTo(user.email().value());
        ResponseCookie sessionCookie = login.getResponseCookies().getFirst(BrowserSession.SESSION_COOKIE);
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.isSecure()).isTrue();
        assertThat(sessionCookie.getSameSite()).isEqualToIgnoringCase("Lax");
        assertThat(browser.cookie(BrowserSession.CSRF_COOKIE)).isNotNull().isNotEqualTo(csrfBeforeLogin);

        EntityExchangeResult<String> me = browser.get("/api/v1/me");
        assertThat(me.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(json(me, "$.id")).isEqualTo(user.id().toString());
        assertThat(json(me, "$.timeZone")).isEqualTo("America/Bogota");

        assertThat(browser.post("/api/v1/auth/logout", Map.of()).getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get("/api/v1/me").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sessionsArePersistedAndIndexedByUser() {
        User user = accounts.create();

        new BrowserSession(client).login(user.email().value(), TestAccounts.PASSWORD);

        Integer sessions = jdbc.queryForObject(
                "select count(*) from identity.spring_session where principal_name = ?",
                Integer.class, user.id().toString());
        assertThat(sessions).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        User user = accounts.create();
        BrowserSession browser = new BrowserSession(client);
        browser.get("/api/v1/auth/registration");

        EntityExchangeResult<String> wrongPassword = browser.post("/api/v1/auth/login",
                Map.of("email", user.email().value(), "password", "no-es-la-contraseña"));
        EntityExchangeResult<String> unknownEmail = browser.post("/api/v1/auth/login",
                Map.of("email", TestAccounts.uniqueEmail(), "password", TestAccounts.PASSWORD));

        for (EntityExchangeResult<String> result : List.of(wrongPassword, unknownEmail)) {
            assertThat(result.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(json(result, "$.code")).isEqualTo("identity.invalid_credentials");
            assertThat(json(result, "$.detail")).isEqualTo("El correo o la contraseña no son correctos.");
        }
    }

    @Test
    void accountLocksAfterFiveFailedAttempts() {
        User user = accounts.create();
        BrowserSession browser = new BrowserSession(client);
        browser.get("/api/v1/auth/registration");
        Map<String, String> wrong = Map.of("email", user.email().value(), "password", "no-es-la-contraseña");

        for (int attempt = 1; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThat(browser.post("/api/v1/auth/login", wrong).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        EntityExchangeResult<String> fifth = browser.post("/api/v1/auth/login", wrong);
        EntityExchangeResult<String> rightPasswordWhileLocked = browser.post("/api/v1/auth/login",
                Map.of("email", user.email().value(), "password", TestAccounts.PASSWORD));

        assertThat(fifth.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(json(fifth, "$.code")).isEqualTo("identity.too_many_attempts");
        assertThat(rightPasswordWhileLocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void updatesTheProfilePartially() {
        User user = accounts.create();
        BrowserSession browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);

        EntityExchangeResult<String> renamed = browser.patch("/api/v1/me", Map.of("displayName", "Kevin Jaimes"));
        EntityExchangeResult<String> invalidZone = browser.patch("/api/v1/me", Map.of("timeZone", "+05:00"));
        EntityExchangeResult<String> moved = browser.patch("/api/v1/me", Map.of("timeZone", "Europe/Madrid"));

        assertThat(json(renamed, "$.displayName")).isEqualTo("Kevin Jaimes");
        assertThat(invalidZone.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(invalidZone, "$.code")).isEqualTo("identity.time_zone_invalid");
        assertThat(json(moved, "$.displayName")).isEqualTo("Kevin Jaimes");
        assertThat(json(moved, "$.timeZone")).isEqualTo("Europe/Madrid");
    }

    @Test
    void changingThePasswordClosesTheOtherSessions() {
        User user = accounts.create();
        String email = user.email().value();
        BrowserSession laptop = new BrowserSession(client);
        BrowserSession phone = new BrowserSession(client);
        laptop.login(email, TestAccounts.PASSWORD);
        phone.login(email, TestAccounts.PASSWORD);

        EntityExchangeResult<String> wrongCurrent = laptop.put("/api/v1/me/password",
                Map.of("currentPassword", "no-es-la-actual", "newPassword", "otra-contraseña-segura"));
        EntityExchangeResult<String> changed = laptop.put("/api/v1/me/password",
                Map.of("currentPassword", TestAccounts.PASSWORD, "newPassword", "otra-contraseña-segura"));

        assertThat(wrongCurrent.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(wrongCurrent, "$.code")).isEqualTo("identity.current_password_incorrect");
        assertThat(changed.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(laptop.get("/api/v1/me").getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(phone.get("/api/v1/me").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        new BrowserSession(client).login(email, "otra-contraseña-segura");
    }
}
