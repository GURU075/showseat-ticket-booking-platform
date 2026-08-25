package com.guru.booking_service.exception;

import com.guru.booking_service.config.BookingConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingException.class)
    ResponseEntity<ProblemDetail> handleBooking(BookingException exception, HttpServletRequest request) {
        return build(exception.status(), exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> handleValidation(Exception exception, HttpServletRequest request) {
        List<String> details = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList()
                : List.of(exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body", request, List.of());
    }

    private ResponseEntity<ProblemDetail> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<String> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:showseat:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        String correlationId = request.getHeader(BookingConfiguration.CORRELATION_ID_HEADER);
        problem.setProperty("correlationId", correlationId == null ? MDC.get("correlationId") : correlationId);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).body(problem);
    }
}
