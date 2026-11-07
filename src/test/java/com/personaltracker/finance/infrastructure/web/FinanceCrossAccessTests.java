package com.personaltracker.finance.infrastructure.web;

import static com.personaltracker.finance.infrastructure.web.FinanceApi.assertProblem;
import static com.personaltracker.finance.infrastructure.web.FinanceApi.body;
import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.support.BrowserSession;
import com.personaltracker.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Aislamiento entre usuarios en cada endpoint de finanzas (ADR-0003): un recurso de otro usuario
 * responde 404, igual que uno inexistente, y nunca aparece en los listados ni en los totales.
 */
@IntegrationTest
class FinanceCrossAccessTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private FinanceApi owner;
    private FinanceApi intruder;
    private String ownerCash;
    private String ownerCategory;
    private String ownerTransaction;
    private String intruderCash;

    @BeforeEach
    void createTwoUsers() {
        owner = FinanceApi.newUser(client, users, passwordHasher);
        ownerCash = owner.cash("Billetera", "50000");
        ownerCategory = owner.category("EXPENSE", "Alimentación", null);
        ownerTransaction = owner.expense(ownerCash, ownerCategory, "12000", "2025-03-05");

        intruder = FinanceApi.newUser(client, users, passwordHasher);
        intruderCash = intruder.cash("Billetera", "0");
    }

    @Test
    void anotherUsersAccountIsNotFound() {
        assertNotFound(intruder.put("/accounts/" + ownerCash, body("kind", "CASH", "name", "Robada")), "account");
        assertNotFound(intruder.post("/accounts/" + ownerCash + "/archive", body()), "account");
        assertNotFound(intruder.post("/accounts/" + ownerCash + "/reactivate", body()), "account");
        assertNotFound(intruder.delete("/accounts/" + ownerCash), "account");
        assertNotFound(intruder.get("/transactions?accountId=" + ownerCash), "account");
        assertNotFound(intruder.post("/transactions", body("type", "EXPENSE", "sourceAccountId", ownerCash,
                "amount", "1", "date", "2025-03-01")), "account");
        assertNotFound(intruder.post("/transactions", body("type", "TRANSFER", "sourceAccountId", intruderCash,
                "destinationAccountId", ownerCash, "amount", "1", "date", "2025-03-01")), "account");
        assertNotFound(intruder.post("/transactions", body("type", "ADJUSTMENT", "accountId", ownerCash,
                "direction", "INCREASE", "amount", "1", "date", "2025-03-01")), "account");

        assertOwnerDataIsIntact();
    }

    @Test
    void anotherUsersCategoryIsNotFound() {
        assertNotFound(intruder.patch("/categories/" + ownerCategory, body("name", "Robada")), "category");
        assertNotFound(intruder.post("/categories/" + ownerCategory + "/archive", body()), "category");
        assertNotFound(intruder.post("/categories/" + ownerCategory + "/reactivate", body()), "category");
        assertNotFound(intruder.delete("/categories/" + ownerCategory), "category");
        assertNotFound(intruder.post("/categories", body("kind", "EXPENSE", "name", "Hija",
                "parentId", ownerCategory)), "category");
        assertNotFound(intruder.get("/transactions?categoryId=" + ownerCategory), "category");
        assertNotFound(intruder.post("/transactions", body("type", "EXPENSE", "sourceAccountId", intruderCash,
                "categoryId", ownerCategory, "amount", "1", "date", "2025-03-01")), "category");

        assertOwnerDataIsIntact();
    }

    @Test
    void anotherUsersTransactionIsNotFound() {
        assertNotFound(intruder.put("/transactions/" + ownerTransaction, body("type", "EXPENSE",
                "sourceAccountId", intruderCash, "amount", "1", "date", "2025-03-01")), "transaction");
        assertNotFound(intruder.delete("/transactions/" + ownerTransaction), "transaction");

        assertOwnerDataIsIntact();
    }

    @Test
    void listsAndTotalsOnlyIncludeOwnData() {
        EntityExchangeResult<String> accounts = intruder.get("/accounts");
        EntityExchangeResult<String> transactions = intruder.get("/transactions");
        EntityExchangeResult<String> summary = intruder.get("/summary?date=2025-03-15");

        assertThat(json(accounts, "$.accounts[*].id")).isEqualTo(List.of(intruderCash));
        assertThat(json(accounts, "$.netWorth")).isEqualTo("0.00");
        assertThat(json(intruder.get("/categories"), "$")).isEqualTo(List.of());
        assertThat(json(transactions, "$.totalItems")).isEqualTo(0);
        assertThat(json(summary, "$.current.expense")).isEqualTo("0.00");
        assertThat(json(summary, "$.byAccount")).isEqualTo(List.of());
        assertThat(json(intruder.get("/setup"), "$.setUp")).isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/setup", "/accounts", "/categories", "/transactions", "/summary"})
    void everyEndpointRequiresASession(String path) {
        EntityExchangeResult<String> result = new BrowserSession(client).get(FinanceApi.BASE + path);

        assertProblem(result, HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    @Test
    void changesRequireTheCsrfToken() {
        EntityExchangeResult<String> result = owner.browser().postWithoutCsrf(
                FinanceApi.BASE + "/accounts", body("kind", "CASH", "name", "Sin token"));

        assertProblem(result, HttpStatus.FORBIDDEN, "csrf_invalid");
    }

    private void assertOwnerDataIsIntact() {
        EntityExchangeResult<String> accounts = owner.get("/accounts");
        assertThat(json(accounts, "$.accounts[0].name")).isEqualTo("Billetera");
        assertThat(json(accounts, "$.accounts[0].archived")).isEqualTo(false);
        assertThat(json(accounts, "$.accounts[0].balance")).isEqualTo("38000.00");
        assertThat(json(owner.get("/categories"), "$[0].name")).isEqualTo("Alimentación");
        assertThat(json(owner.get("/transactions"), "$.items[*].id")).isEqualTo(List.of(ownerTransaction));
    }

    private static void assertNotFound(EntityExchangeResult<String> result, String entity) {
        assertProblem(result, HttpStatus.NOT_FOUND, "finance." + entity + ".not_found");
    }
}
