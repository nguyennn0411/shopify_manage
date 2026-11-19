package com.company.erp.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("fields", exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().toList());
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintValidation(ConstraintViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("fields", exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath().toString()).distinct().toList());
        return problem;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail forbidden(RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
        problem.setTitle("Access denied");
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail expected(ResponseStatusException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason() == null
            ? "Request could not be completed" : exception.getReason());
        problem.setTitle("Request rejected");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The operation conflicts with an existing record or related data");
        problem.setTitle("Data conflict");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled API error id={} method={} path={}", errorId, request.getMethod(), request.getRequestURI(), exception);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create("urn:company-erp:error:" + errorId));
        return problem;
    }
}
