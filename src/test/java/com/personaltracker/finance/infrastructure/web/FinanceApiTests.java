package com.personaltracker.finance.infrastructure.web;

import static com.personaltracker.finance.infrastructure.web.FinanceApi.assertStatus;
import static com.personaltracker.finance.infrastructure.web.FinanceApi.body;
import static com.personaltracker.support.BrowserSession.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.personaltracker.identity.application.port.out.PasswordHasher;
import com.personaltracker.identity.application.port.out.UserRepository;
import com.personaltracker.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Flujos principales de la API de finanzas por HTTP, con sesión y CSRF como el frontend. */
@IntegrationTest
class FinanceApiTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordHasher passwordHasher;

    private FinanceApi api;

    @BeforeEach
    void logIn() {
        api = FinanceApi.newUser(client, users, passwordHasher);
    }

    @Test
    void setUpCreatesTheOtherAccountAndTheSuggestedCategories() {
        assertThat(json(api.get("/setup"), "$.setUp")).isEqualTo(false);

        EntityExchangeResult<String> setUp = api.post("/setup", body("withSuggestedCategories", true));

        assertStatus(setUp, HttpStatus.OK);
        assertThat(json(setUp, "$.setUp")).isEqualTo(true);
        EntityExchangeResult<String> accounts = api.get("/accounts");
        assertThat(json(accounts, "$.accounts[*].kind")).isEqualTo(List.of("OTHER"));
        assertThat(json(accounts, "$.accounts[0].balance")).isNull();
        assertThat(json(accounts, "$.accounts[0].systemManaged")).isEqualTo(true);
        assertThat(json(accounts, "$.netWorth")).isEqualTo("0.00");
        assertThat(json(accounts, "$.currency")).isEqualTo("COP");
        EntityExchangeResult<String> categories = api.get("/categories");
        assertThat(json(categories, "$[?(@.name == 'Alimentación')].subcategories[*].name"))
                .asString()
                .contains("Mercado", "Restaurantes");
    }

    @Test
    void managesAccountsWithDerivedBalances() {
        EntityExchangeResult<String> cash = api.post("/accounts",
                body("kind", "CASH", "name", "Billetera", "openingBalance", "50000"));
        EntityExchangeResult<String> card = api.post("/accounts", body("kind", "CREDIT_CARD", "name", "Visa",
                "institution", "Bancolombia", "lastFourDigits", "5403", "creditLimit", "5000000",
                "initialDebt", "200000"));
        String savings = api.savings("Ahorros", "1000000");

        assertStatus(cash, HttpStatus.CREATED);
        assertThat(json(cash, "$.balance")).isEqualTo("50000.00");
        assertThat(json(cash, "$.institution")).isNull();
        assertStatus(card, HttpStatus.CREATED);
        assertThat(json(card, "$.balance")).isEqualTo("-200000.00");
        assertThat(json(card, "$.debt")).isEqualTo("200000.00");
        assertThat(json(card, "$.availableCredit")).isEqualTo("4800000.00");
        assertThat(json(card, "$.lastFourDigits")).isEqualTo("5403");

        EntityExchangeResult<String> updated = api.put("/accounts/" + savings, body("kind", "CHECKING",
                "name", "Corriente", "institution", "Davivienda", "openingBalance", "1000000"));
        assertStatus(updated, HttpStatus.OK);
        assertThat(json(updated, "$.kind")).isEqualTo("CHECKING");
        assertThat(json(updated, "$.institution")).isEqualTo("Davivienda");

        String cashId = (String) json(cash, "$.id");
        assertThat(json(api.post("/accounts/" + cashId + "/archive", body()), "$.archived")).isEqualTo(true);
        assertThat(json(api.post("/accounts/" + cashId + "/reactivate", body()), "$.archived")).isEqualTo(false);
        assertStatus(api.delete("/accounts/" + cashId), HttpStatus.NO_CONTENT);

        EntityExchangeResult<String> list = api.get("/accounts");
        assertThat(json(list, "$.accounts[*].name")).isEqualTo(List.of("Corriente", "Visa"));
        assertThat(json(list, "$.netWorth")).isEqualTo("800000.00");
    }

    @Test
    void managesCategories() {
        String food = api.category("EXPENSE", "Alimentación", null);
        EntityExchangeResult<String> groceries = api.post("/categories",
                body("kind", "EXPENSE", "name", "Mercado", "parentId", food));

        assertStatus(groceries, HttpStatus.CREATED);
        assertStatus(api.patch("/categories/" + food, body("name", "Comida")), HttpStatus.NO_CONTENT);
        assertStatus(api.post("/categories/" + food + "/archive", body()), HttpStatus.NO_CONTENT);

        EntityExchangeResult<String> archived = api.get("/categories");
        assertThat(json(archived, "$[0].name")).isEqualTo("Comida");
        assertThat(json(archived, "$[0].archived")).isEqualTo(true);
        assertThat(json(archived, "$[0].subcategories[0].archived")).isEqualTo(true);

        assertStatus(api.post("/categories/" + food + "/reactivate", body()), HttpStatus.NO_CONTENT);
        assertStatus(api.delete("/categories/" + json(groceries, "$.id")), HttpStatus.NO_CONTENT);
        assertThat(json(api.get("/categories"), "$[0].subcategories")).isEqualTo(List.of());
    }

    @Test
    void recordsListsUpdatesAndDeletesTransactions() {
        String bank = api.savings("Ahorros", "0");
        String card = api.creditCard("Visa", "3000000");
        String salary = api.category("INCOME", "Salario", null);

        EntityExchangeResult<String> income = api.post("/transactions", body("type", "INCOME",
                "destinationAccountId", bank, "categoryId", salary, "amount", "4500000", "date", "2025-03-01",
                "description", "Nómina"));
        EntityExchangeResult<String> expense = api.post("/transactions", body("type", "EXPENSE",
                "sourceAccountId", card, "amount", "85000.50", "date", "2025-03-03", "merchant", "Éxito",
                "channel", "CARD"));
        api.transfer(bank, card, "85000.50", "2025-03-25");
        EntityExchangeResult<String> adjustment = api.post("/transactions", body("type", "ADJUSTMENT",
                "accountId", bank, "direction", "DECREASE", "amount", "100", "date", "2025-03-31"));

        assertStatus(income, HttpStatus.CREATED);
        assertThat(json(income, "$.amount")).isEqualTo("4500000.00");
        assertThat(json(income, "$.currency")).isEqualTo("COP");
        assertThat(json(income, "$.destinationAccountId")).isEqualTo(bank);
        assertThat(json(income, "$.sourceAccountId")).isNull();
        assertThat(json(expense, "$.merchant")).isEqualTo("Éxito");
        assertThat(json(expense, "$.channel")).isEqualTo("CARD");
        assertThat(json(adjustment, "$.sourceAccountId")).isEqualTo(bank);
        assertThat(json(adjustment, "$.direction")).isEqualTo("DECREASE");

        EntityExchangeResult<String> all = api.get("/transactions");
        assertThat(json(all, "$.totalItems")).isEqualTo(4);
        assertThat(json(all, "$.page")).isEqualTo(0);
        assertThat(json(all, "$.size")).isEqualTo(20);
        assertThat(json(all, "$.totalPages")).isEqualTo(1);
        assertThat(json(all, "$.items[*].type")).isEqualTo(List.of("ADJUSTMENT", "TRANSFER", "EXPENSE", "INCOME"));

        EntityExchangeResult<String> cardPage = api.get(
                "/transactions?accountId=" + card + "&sort=amount,asc&size=1&page=1");
        assertThat(json(cardPage, "$.totalItems")).isEqualTo(2);
        assertThat(json(cardPage, "$.totalPages")).isEqualTo(2);
        assertThat(json(cardPage, "$.items[0].type")).isEqualTo("EXPENSE");
        assertThat(json(api.get("/transactions?q=ÉXITO"), "$.totalItems")).isEqualTo(1);
        assertThat(json(api.get("/transactions?from=2025-03-02&to=2025-03-25"), "$.totalItems")).isEqualTo(2);

        String expenseId = (String) json(expense, "$.id");
        EntityExchangeResult<String> updated = api.put("/transactions/" + expenseId, body("type", "EXPENSE",
                "sourceAccountId", card, "amount", "90000", "date", "2025-03-04"));
        assertStatus(updated, HttpStatus.OK);
        assertThat(json(updated, "$.amount")).isEqualTo("90000.00");
        assertThat(json(updated, "$.merchant")).isNull();

        assertStatus(api.delete("/transactions/" + json(income, "$.id")), HttpStatus.NO_CONTENT);
        assertThat(json(api.get("/transactions"), "$.totalItems")).isEqualTo(3);
    }

    @Test
    void summarizesAPeriodAndAccountBalances() {
        String bank = api.savings("Ahorros", "0");
        String card = api.creditCard("Visa", "3000000");
        String food = api.category("EXPENSE", "Alimentación", null);
        String groceries = api.category("EXPENSE", "Mercado", food);
        api.income(bank, null, "4000000", "2025-03-01");
        api.expense(card, groceries, "350000.50", "2025-03-10");
        api.transfer(bank, card, "350000.50", "2025-03-25");
        api.expense(bank, null, "20000", "2025-02-27");

        EntityExchangeResult<String> summary = api.get("/summary?period=MONTH&date=2025-03-15");

        assertStatus(summary, HttpStatus.OK);
        assertThat(json(summary, "$.kind")).isEqualTo("MONTH");
        assertThat(json(summary, "$.start")).isEqualTo("2025-03-01");
        assertThat(json(summary, "$.end")).isEqualTo("2025-03-31");
        assertThat(json(summary, "$.currency")).isEqualTo("COP");
        assertThat(json(summary, "$.current.income")).isEqualTo("4000000.00");
        assertThat(json(summary, "$.current.expense")).isEqualTo("350000.50");
        assertThat(json(summary, "$.current.net")).isEqualTo("3649999.50");
        assertThat(json(summary, "$.previous.start")).isEqualTo("2025-02-01");
        assertThat(json(summary, "$.previous.end")).isEqualTo("2025-02-28");
        assertThat(json(summary, "$.previous.expense")).isEqualTo("20000.00");
        assertThat(json(summary, "$.expensesByCategory[0].categoryId")).isEqualTo(food);
        assertThat(json(summary, "$.expensesByCategory[0].total")).isEqualTo("350000.50");
        assertThat(json(summary, "$.trend.length()")).isEqualTo(6);
        assertThat(json(summary, "$.trend[5].start")).isEqualTo("2025-03-01");

        EntityExchangeResult<String> accounts = api.get("/accounts");
        assertThat(json(accounts, "$.accounts[?(@.name == 'Ahorros')].balance")).isEqualTo(List.of("3629999.50"));
        assertThat(json(accounts, "$.accounts[?(@.name == 'Visa')].balance")).isEqualTo(List.of("0.00"));
        assertThat(json(accounts, "$.netWorth")).isEqualTo("3629999.50");

        EntityExchangeResult<String> today = api.get("/summary");
        assertStatus(today, HttpStatus.OK);
        assertThat(json(today, "$.kind")).isEqualTo("MONTH");
    }
}
