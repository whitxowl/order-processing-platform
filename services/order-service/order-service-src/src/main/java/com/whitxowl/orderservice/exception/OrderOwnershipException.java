package com.whitxowl.orderservice.exception;

import java.util.UUID;

public class OrderOwnershipException extends RuntimeException {

    public OrderOwnershipException(UUID orderId) {
        super("Access denied: order does not belong to the current user [orderId=%s]"
                .formatted(orderId));
    }
}