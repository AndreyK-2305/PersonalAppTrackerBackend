package com.personaltracker.finance.infrastructure.web;

import static com.personaltracker.finance.infrastructure.web.FinanceApi.assertProblem;
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

/**
 * Errores de la API de finanzas: 400 para datos mal formados o campos que no corresponden al tipo,
 * 422 para reglas de negocio y 409 para conflictos con datos existentes.
 */
@IntegrationTest
class FinanceApiValidationTests {

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
    void accountFieldsDependOnTheKind() {
        EntityExchangeResult<String> cashWithCardFields = api.post("/accounts",
                body("kind", "CASH", "name", "Billetera", "institution", "Bancolombia", "creditLimit", "1"));
        EntityExchangeResult<String> savingsWithoutInstitution = api.post("/accounts",
                body("kind", "SAVINGS", "name", "Ahorros"));
        EntityExchangeResult<String> cardWithoutLimit = api.post("/accounts",
                body("kind", "CREDIT_CARD", "name", "Visa", "institution", "Bancolombia", "openingBalance", "0"));

        assertProblem(cashWithCardFields, HttpStatus.BAD_REQUEST, "validation_error");
        assertThat(json(cashWithCardFields, "$.errors[*].field")).isEqualTo(List.of("institution", "creditLimit"));
        assertThat(json(cashWithCardFields, "$.errors[0].message")).isEqualTo("no aplica a este tipo de producto");
        assertProblem(savingsWithoutInstitution, HttpStatus.BAD_REQUEST, "validation_error");
        assertThat(json(savingsWithoutInstitution, "$.errors[0].message"))
                .isEqualTo("es obligatorio para este tipo de producto");
        assertThat(json(cardWithoutLimit, "$.errors[*].field")).isEqualTo(List.of("creditLimit", "openingBalance"));
        assertThat(json(api.get("/accounts"), "$.accounts")).isEqualTo(List.of());
    }

    @Test
    void malformedAccountDataIsRejected() {
        EntityExchangeResult<String> other = api.post("/accounts", body("kind", "OTHER", "name", "Mío"));
        EntityExchangeResult<String> tooManyDecimals = api.post("/accounts",
                body("kind", "CASH", "name", "Billetera", "openingBalance", "10.555"));
        EntityExchangeResult<String> blankName = api.post("/accounts", body("kind", "CASH", "name", " "));
        EntityExchangeResult<String> fullCardNumber = api.post("/accounts", body("kind", "CREDIT_CARD",
                "name", "Visa", "institution", "Bancolombia", "lastFourDigits", "4111111111111111", "creditLimit", "1"));
        EntityExchangeResult<String> unknownKind = api.post("/accounts", body("kind", "CRYPTO", "name", "X"));

        assertProblem(other, HttpStatus.BAD_REQUEST, "validation_error");
        assertThat(json(other, "$.errors[0].field")).isEqualTo("kind");
        assertThat(json(tooManyDecimals, "$.errors[0].field")).isEqualTo("openingBalance");
        assertThat(json(tooManyDecimals, "$.errors[0].message")).isEqualTo(Amounts.MESSAGE);
        assertThat(json(blankName, "$.errors[0].field")).isEqualTo("name");
        assertThat(json(fullCardNumber, "$.errors[0].field")).isEqualTo("lastFourDigits");
        assertProblem(unknownKind, HttpStatus.BAD_REQUEST, "bad_request");
    }

    @Test
    void transactionFieldsDependOnTheType() {
        String cash = api.cash("Billetera", "0");

        EntityExchangeResult<String> expenseWithDestination = api.post("/transactions", body("type", "EXPENSE",
                "destinationAccountId", cash, "amount", "1000", "date", "2025-03-01"));
        EntityExchangeResult<String> adjustmentWithoutDirection = api.post("/transactions", body("type",
                "ADJUSTMENT", "accountId", cash, "amount", "1000", "date", "2025-03-01"));
        EntityExchangeResult<String> negativeAmount = api.post("/transactions", body("type", "EXPENSE",
                "sourceAccountId", cash, "amount", "-5", "date", "2025-03-01"));
        EntityExchangeResult<String> missingDate = api.post("/transactions", body("type", "EXPENSE",
                "sourceAccountId", cash, "amount", "5"));

        assertProblem(expenseWithDestination, HttpStatus.BAD_REQUEST, "validation_error");
        assertThat(json(expenseWithDestination, "$.errors[*].field"))
                .isEqualTo(List.of("sourceAccountId", "destinationAccountId"));
        assertThat(json(adjustmentWithoutDirection, "$.errors[*].field")).isEqualTo(List.of("direction"));
        assertThat(json(negativeAmount, "$.errors[0].field")).isEqualTo("amount");
        assertThat(json(missingDate, "$.errors[0].field")).isEqualTo("date");
        assertThat(json(api.get("/transactions"), "$.totalItems")).isEqualTo(0);
    }

    @Test
    void businessRulesAnswerUnprocessable() {
        api.setUp(false);
        String other = (String) json(api.get("/accounts"), "$.accounts[0].id");
        String cash = api.cash("Billetera", "0");
        String expense = api.expense(cash, null, "1000", "2025-03-01");

        assertProblem(api.post("/accounts", body("kind", "CASH", "name", "Alcancía", "openingBalance", "-1")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.account.opening_balance_invalid");
        assertProblem(api.delete("/accounts/" + other), HttpStatus.UNPROCESSABLE_CONTENT,
                "finance.account.system_managed");
        assertProblem(api.post("/transactions", body("type", "TRANSFER", "sourceAccountId", cash,
                        "destinationAccountId", cash, "amount", "1", "date", "2025-03-01")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.transfer.same_account");
        assertProblem(api.post("/transactions", body("type", "TRANSFER", "sourceAccountId", cash,
                        "destinationAccountId", other, "amount", "1", "date", "2025-03-01")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.transaction.account_not_allowed");
        assertProblem(api.post("/transactions", body("type", "EXPENSE", "sourceAccountId", cash,
                        "amount", "0", "date", "2025-03-01")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.transaction.amount_invalid");
        assertProblem(api.put("/transactions/" + expense, body("type", "INCOME", "destinationAccountId", cash,
                        "amount", "1000", "date", "2025-03-01")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.transaction.type_change");
        assertProblem(api.put("/accounts/" + cash, body("kind", "SAVINGS", "name", "Billetera",
                        "institution", "Bancolombia")),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.account.kind_change");
    }

    @Test
    void conflictsWithExistingDataAnswerConflict() {
        String cash = api.cash("Billetera", "0");
        String food = api.category("EXPENSE", "Alimentación", null);
        api.expense(cash, food, "1000", "2025-03-01");

        assertProblem(api.post("/accounts", body("kind", "CASH", "name", "BILLETERA")),
                HttpStatus.CONFLICT, "finance.account.name_taken");
        assertProblem(api.post("/categories", body("kind", "EXPENSE", "name", "alimentación")),
                HttpStatus.CONFLICT, "finance.category.name_taken");
        assertProblem(api.delete("/accounts/" + cash), HttpStatus.CONFLICT, "finance.account.has_transactions");
        assertProblem(api.delete("/categories/" + food), HttpStatus.CONFLICT, "finance.category.in_use");
    }

    @Test
    void listingParametersAreValidated() {
        EntityExchangeResult<String> tooBig = api.get("/transactions?size=101");
        EntityExchangeResult<String> negativePage = api.get("/transactions?page=-1");
        EntityExchangeResult<String> unknownSort = api.get("/transactions?sort=name,asc");

        assertProblem(tooBig, HttpStatus.BAD_REQUEST, "validation_error");
        assertThat(json(tooBig, "$.errors[0].field")).isEqualTo("size");
        assertThat(json(negativePage, "$.errors[0].field")).isEqualTo("page");
        assertThat(json(unknownSort, "$.errors[0].field")).isEqualTo("sort");
        assertProblem(api.get("/transactions?from=2025-03-10&to=2025-03-01"),
                HttpStatus.UNPROCESSABLE_CONTENT, "finance.date_range.invalid");
        assertProblem(api.get("/transactions?type=LOAN"), HttpStatus.BAD_REQUEST, "bad_request");
        assertProblem(api.get("/transactions?accountId=no-es-un-uuid"), HttpStatus.BAD_REQUEST, "bad_request");
        assertProblem(api.get("/summary?period=YEAR"), HttpStatus.BAD_REQUEST, "bad_request");
    }
}
