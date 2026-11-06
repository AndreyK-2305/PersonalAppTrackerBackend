package com.personaltracker.identity.infrastructure.web;

import com.personaltracker.identity.domain.exception.AccountLockedException;
import com.personaltracker.identity.domain.exception.InvalidCredentialsException;
import com.personaltracker.identity.domain.exception.RegistrationClosedException;
import com.personaltracker.shared.domain.DomainException;
import com.personaltracker.shared.infrastructure.web.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Estados HTTP de las excepciones de autenticación de identity. Tiene precedencia sobre el
 * manejador global, que respondería 422 por ser excepciones de dominio.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ProblemDetail> handleAccountLocked(AccountLockedException ex) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, ex);
    }

    @ExceptionHandler(RegistrationClosedException.class)
    ResponseEntity<ProblemDetail> handleRegistrationClosed(RegistrationClosedException ex) {
        return respond(HttpStatus.FORBIDDEN, ex);
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, DomainException ex) {
        return ApiProblems.response(status, ex.code(), ex.getMessage());
    }
}
