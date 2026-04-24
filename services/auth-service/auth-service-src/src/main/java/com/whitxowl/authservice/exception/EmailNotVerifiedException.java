package com.whitxowl.authservice.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String email) {
        super("Email not verified: " + email);
    }
}
