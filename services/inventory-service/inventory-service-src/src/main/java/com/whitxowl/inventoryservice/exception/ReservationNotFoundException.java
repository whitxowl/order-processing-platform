package com.whitxowl.inventoryservice.exception;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(String orderId) {
        super("Reservation not found [orderId=%s]".formatted(orderId));
    }
}