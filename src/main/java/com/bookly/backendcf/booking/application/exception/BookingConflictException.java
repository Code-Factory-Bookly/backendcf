package com.bookly.backendcf.booking.application.exception;

public class BookingConflictException extends RuntimeException {
    private final String errorCode;

    public BookingConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}