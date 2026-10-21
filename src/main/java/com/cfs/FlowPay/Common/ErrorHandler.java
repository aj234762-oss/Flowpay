package com.cfs.FlowPay.Common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException e){
        return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));
    }
// or can give MethodArgumentTypeMismatchException.class
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class

    })
    ResponseEntity<?> validation(Exception e) {
        String message = e instanceof MethodArgumentNotValidException v
                ? v.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request")
                : "Invalid request,Missing header,unknown field";

        return ResponseEntity.badRequest().body(Map.of("message",message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)    ResponseEntity<?> duplicate(DataIntegrityViolationException e)
    {
        return ResponseEntity.status(409).body(Map.of("message","Duplicate or conflicting record"));
    }





}
