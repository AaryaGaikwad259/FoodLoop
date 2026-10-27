package com.foodloop.exception;

/** Business-rule violation caused by the client's request (HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
