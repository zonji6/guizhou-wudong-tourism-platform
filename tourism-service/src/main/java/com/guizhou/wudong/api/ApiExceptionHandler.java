package com.guizhou.wudong.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiEnvelope<Void> notFound(NotFoundException exception) {
        return ApiEnvelope.fail(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiEnvelope<Void> badRequest(IllegalArgumentException exception) {
        return ApiEnvelope.fail(exception.getMessage());
    }
}
