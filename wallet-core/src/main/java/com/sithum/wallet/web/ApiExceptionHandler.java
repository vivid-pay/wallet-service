package com.sithum.wallet.web;

import com.sithum.wallet.accounts.DuplicateWalletException;
import com.sithum.wallet.accounts.UnauthenticatedException;
import com.sithum.wallet.ledger.UnsupportedCurrencyException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place that turns exceptions into HTTP responses, as RFC 9457 problem details
 * ({@code application/problem+json}). Spring MVC's own exceptions (404, 405, 415, ...) are
 * mapped by the base class; this adds the correlation ID, per-field validation errors and a
 * catch-all that never exposes internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record FieldViolation(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toViolation)
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<Object> handleUnsupportedCurrency(UnsupportedCurrencyException ex, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Unsupported currency",
                "Currency must be an uppercase ISO 4217 code from the supported list", ex, request);
    }

    @ExceptionHandler(DuplicateWalletException.class)
    public ResponseEntity<Object> handleDuplicateWallet(DuplicateWalletException ex, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "Wallet already exists",
                "You already have a wallet in this currency", ex, request);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Object> handleUnauthenticated(UnauthenticatedException ex, WebRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthenticated", ex.getMessage(), ex, request);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String title, String detail, Exception ex,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return handleExceptionInternal(ex, problem, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Spring MVC exceptions arrive with a null body; take their problem so it gets enriched too.
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem) {
            if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
                HttpServletRequest http = servletRequest.getRequest();
                problem.setInstance(URI.create(http.getRequestURI()));
            }
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                problem.setProperty("correlationId", correlationId);
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
