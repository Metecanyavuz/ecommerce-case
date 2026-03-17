package com.mete.ecommerce.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("This email has already registered: " + email);
    }
}
