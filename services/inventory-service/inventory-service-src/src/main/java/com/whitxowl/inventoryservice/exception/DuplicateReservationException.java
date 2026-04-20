package com.whitxowl.inventoryservice.exception;

public class DuplicateReservationException extends RuntimeException {

    public DuplicateReservationException(String orderId) {
        super("Reservation already exists for order [orderId=%s]".formatted(orderId));
    }
}