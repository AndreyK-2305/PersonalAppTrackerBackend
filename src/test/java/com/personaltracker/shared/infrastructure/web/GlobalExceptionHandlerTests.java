package com.personaltracker.shared.infrastructure.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personaltracker.shared.domain.DomainException;
import com.personaltracker.shared.domain.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Formato de error de la API (RFC 9457) para cada tipo de excepción. */
@WebMvcTest(controllers = GlobalExceptionHandlerTests.ProbeController.class)
@Import(GlobalExceptionHandlerTests.ProbeController.class)
@WithMockUser
class GlobalExceptionHandlerTests {

    private static final String CORRELATION_ID = "test-correlation-id";

    @Autowired
    MockMvc mvc;

    @Test
    void validationErrorsListEachInvalidFieldInSpanish() throws Exception {
        mvc.perform(post("/probe/validation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \", \"amount\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "amount")))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("no debe estar vacío"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/probe/validation").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.detail").value("La solicitud no es válida."));
    }

    @Test
    void notFoundDomainExceptionKeepsItsCodeAndMessage() throws Exception {
        mvc.perform(get("/probe/not-found").header(CorrelationIdFilter.HEADER, CORRELATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("probe.not_found"))
                .andExpect(jsonPath("$.detail").value("La sonda no existe."))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @Test
    void businessRuleViolationIsUnprocessable() throws Exception {
        mvc.perform(get("/probe/business-rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("probe.rule_violated"))
                .andExpect(jsonPath("$.detail").value("La regla de la sonda no se cumple."));
    }

    @Test
    void accessDeniedIsForbidden() throws Exception {
        mvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalDetails() throws Exception {
        mvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Intenta de nuevo más tarde."))
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        mvc.perform(get("/probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(delete("/probe/business-rule").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("method_not_allowed"));
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/validation")
        void validation(@Valid @RequestBody ProbeRequest request) {}

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ProbeNotFoundException();
        }

        @GetMapping("/probe/business-rule")
        void businessRule() {
            throw new ProbeRuleViolatedException();
        }

        @GetMapping("/probe/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("secreto interno");
        }
    }

    record ProbeRequest(@NotBlank String name, @NotNull @Positive BigDecimal amount) {}

    static class ProbeNotFoundException extends NotFoundException {
        ProbeNotFoundException() {
            super("probe.not_found", "La sonda no existe.");
        }
    }

    static class ProbeRuleViolatedException extends DomainException {
        ProbeRuleViolatedException() {
            super("probe.rule_violated", "La regla de la sonda no se cumple.");
        }
    }
}
