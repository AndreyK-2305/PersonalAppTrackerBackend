package com.personaltracker.finance.infrastructure.web;

import com.personaltracker.finance.application.dto.ListTransactionsQuery;
import com.personaltracker.finance.application.dto.PageQuery;
import com.personaltracker.finance.application.dto.RecordTransactionCommand;
import com.personaltracker.finance.application.dto.TransactionSort;
import com.personaltracker.finance.application.dto.UpdateTransactionCommand;
import com.personaltracker.finance.application.usecase.DeleteTransaction;
import com.personaltracker.finance.application.usecase.ListTransactions;
import com.personaltracker.finance.application.usecase.RecordTransaction;
import com.personaltracker.finance.application.usecase.UpdateTransaction;
import com.personaltracker.finance.domain.model.AccountId;
import com.personaltracker.finance.domain.model.CategoryId;
import com.personaltracker.finance.domain.model.TransactionId;
import com.personaltracker.finance.domain.model.TransactionType;
import com.personaltracker.identity.api.CurrentUser;
import com.personaltracker.shared.domain.UserId;
import com.personaltracker.shared.infrastructure.web.RequestValidationException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Movimientos manuales (ADR-0013 y ADR-0015). Un producto, una categoría o un movimiento ajenos
 * responden 404. Editar no cambia el tipo (422): se borra y se registra de nuevo.
 */
@RestController
@RequestMapping("/api/v1/finance/transactions")
class TransactionController {

    private static final Map<String, TransactionSort> SORTS = Map.of(
            "date,desc", TransactionSort.DATE_DESC,
            "date,asc", TransactionSort.DATE_ASC,
            "amount,desc", TransactionSort.AMOUNT_DESC,
            "amount,asc", TransactionSort.AMOUNT_ASC);

    private final ListTransactions listTransactions;
    private final RecordTransaction recordTransaction;
    private final UpdateTransaction updateTransaction;
    private final DeleteTransaction deleteTransaction;

    TransactionController(
            ListTransactions listTransactions,
            RecordTransaction recordTransaction,
            UpdateTransaction updateTransaction,
            DeleteTransaction deleteTransaction) {
        this.listTransactions = listTransactions;
        this.recordTransaction = recordTransaction;
        this.updateTransaction = updateTransaction;
        this.deleteTransaction = deleteTransaction;
    }

    /**
     * Filtros opcionales. {@code accountId} incluye los movimientos donde el producto es origen o
     * destino; {@code categoryId} de una categoría principal incluye sus subcategorías; {@code q} busca
     * en la descripción y el comercio. A igual criterio de orden, los registrados más recientemente
     * van primero.
     */
    @GetMapping
    PageResponse<TransactionResponse> list(
            @CurrentUser UserId owner,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @Size(max = 100) String q,
            @Parameter(schema = @Schema(
                            allowableValues = {"date,desc", "date,asc", "amount,desc", "amount,asc"},
                            defaultValue = "date,desc"))
                    @RequestParam(defaultValue = "date,desc") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        ListTransactionsQuery query = new ListTransactionsQuery(
                owner,
                from,
                to,
                accountId == null ? null : new AccountId(accountId),
                categoryId == null ? null : new CategoryId(categoryId),
                type,
                q,
                sortOf(sort),
                new PageQuery(page, size));
        return PageResponse.from(listTransactions.execute(query), TransactionResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse record(@CurrentUser UserId owner, @Valid @RequestBody TransactionRequest body) {
        return TransactionResponse.from(recordTransaction.execute(new RecordTransactionCommand(owner, body.toDraft())));
    }

    /** Reemplaza todos los datos del movimiento; el tipo debe ser el mismo. */
    @PutMapping("/{id}")
    TransactionResponse update(
            @CurrentUser UserId owner, @PathVariable UUID id, @Valid @RequestBody TransactionRequest body) {
        return TransactionResponse.from(updateTransaction.execute(
                new UpdateTransactionCommand(owner, new TransactionId(id), body.toDraft())));
    }

    /** Borrado definitivo, sin historial (ADR-0015). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser UserId owner, @PathVariable UUID id) {
        deleteTransaction.execute(owner, new TransactionId(id));
    }

    private static TransactionSort sortOf(String value) {
        TransactionSort sort = SORTS.get(value);
        if (sort == null) {
            throw RequestValidationException.of("sort", "debe ser date,desc, date,asc, amount,desc o amount,asc");
        }
        return sort;
    }
}
