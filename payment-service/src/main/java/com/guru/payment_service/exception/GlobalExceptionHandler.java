package com.guru.payment_service.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ProblemDetail> payment(PaymentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setTitle("Payment request failed"); detail.setProperty("code", exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(detail);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    ResponseEntity<ProblemDetail> validation(Exception exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid");
        detail.setTitle("Validation failed"); detail.setProperty("code", "VALIDATION_FAILED");
        return ResponseEntity.badRequest().body(detail);
    }
}
