package com.personaltracker.identity;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import com.personaltracker.support.TestAccounts;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Registro con REGISTRATION_ENABLED=true (contexto propio: así se crea la primera cuenta, ADR-0012). */
@IntegrationTest
@TestPropertySource(properties = "app.identity.registration-enabled=true")
class RegistrationTests {

    @Autowired
    RestTestClient client;

    @Test
    void registersTheAccountAndStartsASession() {
        BrowserSession browser = new BrowserSession(client);
        assertThat(json(browser.get("/api/v1/auth/registration"), "$.enabled")).isEqualTo(true);
        String email = TestAccounts.uniqueEmail();

        EntityExchangeResult<String> created = browser.post("/api/v1/auth/register",
                Map.of("email", "  " + email.toUpperCase() + " ", "password", TestAccounts.PASSWORD,
                        "displayName", "Kevin"));

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(json(created, "$.email")).isEqualTo(email);
        assertThat(json(browser.get("/api/v1/me"), "$.email")).isEqualTo(email);
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegistered() {
        String email = TestAccounts.uniqueEmail();
        register(new BrowserSession(client), email, TestAccounts.PASSWORD);

        EntityExchangeResult<String> duplicate = register(new BrowserSession(client), email, TestAccounts.PASSWORD);

        assertThat(duplicate.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(duplicate, "$.code")).isEqualTo("identity.email_already_registered");
    }

    @Test
    void validatesEmailPasswordAndRequiredFields() {
        EntityExchangeResult<String> weakPassword =
                register(new BrowserSession(client), TestAccounts.uniqueEmail(), "corta");
        EntityExchangeResult<String> invalidEmail =
                register(new BrowserSession(client), "no-es-un-correo", TestAccounts.PASSWORD);
        BrowserSession browser = new BrowserSession(client);
        browser.get("/api/v1/auth/registration");
        EntityExchangeResult<String> missingName = browser.post("/api/v1/auth/register",
                Map.of("email", TestAccounts.uniqueEmail(), "password", TestAccounts.PASSWORD));

        assertThat(weakPassword.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(weakPassword, "$.code")).isEqualTo("identity.password_policy");
        assertThat(invalidEmail.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json(invalidEmail, "$.code")).isEqualTo("identity.email_invalid");
        assertThat(missingName.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(missingName, "$.errors[0].field")).isEqualTo("displayName");
    }

    private static EntityExchangeResult<String> register(BrowserSession browser, String email, String password) {
        browser.get("/api/v1/auth/registration");
        return browser.post("/api/v1/auth/register",
                Map.of("email", email, "password", password, "displayName", "Kevin"));
    }
}
