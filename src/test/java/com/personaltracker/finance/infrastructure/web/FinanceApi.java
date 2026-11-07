package com.personaltracker.finance.infrastructure.web;

import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.identity.domain.model.User;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.TestAccounts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Cliente de la API de finanzas para las pruebas: un usuario nuevo con sesión iniciada y atajos para
 * crear recursos por HTTP. Las rutas son relativas a {@code /api/v1/finance}.
 */
final class FinanceApi {

    static final String BASE = "/api/v1/finance";

    private final BrowserSession browser;

    private FinanceApi(BrowserSession browser) {
        this.browser = browser;
    }

    static FinanceApi newUser(RestTestClient client, UserRepository users, PasswordHasher passwordHasher) {
        User user = new TestAccounts(users, passwordHasher).create();
        BrowserSession browser = new BrowserSession(client);
        browser.login(user.email().value(), TestAccounts.PASSWORD);
        return new FinanceApi(browser);
    }

    BrowserSession browser() {
        return browser;
    }

    EntityExchangeResult<String> get(String path) {
        return browser.get(BASE + path);
    }

    EntityExchangeResult<String> post(String path, Map<String, Object> body) {
        return browser.post(BASE + path, body);
    }

    EntityExchangeResult<String> put(String path, Map<String, Object> body) {
        return browser.put(BASE + path, body);
    }

    EntityExchangeResult<String> patch(String path, Map<String, Object> body) {
        return browser.patch(BASE + path, body);
    }

    EntityExchangeResult<String> delete(String path) {
        return browser.delete(BASE + path);
    }

    void setUp(boolean withSuggestedCategories) {
        assertStatus(post("/setup", body("withSuggestedCategories", withSuggestedCategories)), HttpStatus.OK);
    }

    String cash(String name, String openingBalance) {
        return created(post("/accounts", body("kind", "CASH", "name", name, "openingBalance", openingBalance)));
    }

    String savings(String name, String openingBalance) {
        return created(post("/accounts", body("kind", "SAVINGS", "name", name, "institution", "Bancolombia",
                "openingBalance", openingBalance)));
    }

    String creditCard(String name, String creditLimit) {
        return created(post("/accounts", body("kind", "CREDIT_CARD", "name", name, "institution", "Bancolombia",
                "lastFourDigits", "5403", "creditLimit", creditLimit)));
    }

    String category(String kind, String name, String parentId) {
        return created(post("/categories", body("kind", kind, "name", name, "parentId", parentId)));
    }

    String expense(String sourceAccountId, String categoryId, String amount, String date) {
        return created(post("/transactions", body("type", "EXPENSE", "sourceAccountId", sourceAccountId,
                "categoryId", categoryId, "amount", amount, "date", date)));
    }

    String income(String destinationAccountId, String categoryId, String amount, String date) {
        return created(post("/transactions", body("type", "INCOME", "destinationAccountId", destinationAccountId,
                "categoryId", categoryId, "amount", amount, "date", date)));
    }

    String transfer(String sourceAccountId, String destinationAccountId, String amount, String date) {
        return created(post("/transactions", body("type", "TRANSFER", "sourceAccountId", sourceAccountId,
                "destinationAccountId", destinationAccountId, "amount", amount, "date", date)));
    }

    /** Arma un cuerpo JSON a partir de pares clave-valor; omite los valores nulos. */
    static Map<String, Object> body(Object... keysAndValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                body.put((String) keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        return body;
    }

    static void assertStatus(EntityExchangeResult<String> result, HttpStatus status) {
        assertThat(result.getStatus()).as(result.getResponseBody()).isEqualTo(status);
    }

    /** Comprueba un error de la API: estado y {@code code}. */
    static void assertProblem(EntityExchangeResult<String> result, HttpStatus status, String code) {
        assertStatus(result, status);
        assertThat(json(result, "$.code")).as(result.getResponseBody()).isEqualTo(code);
    }

    private static String created(EntityExchangeResult<String> result) {
        assertStatus(result, HttpStatus.CREATED);
        return (String) json(result, "$.id");
    }
}
