package com.whitxowl.orderservice.exception;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;

import java.util.UUID;

public class OrderCancellationNotAllowedException extends RuntimeException {

    public OrderCancellationNotAllowedException(UUID orderId, OrderStatus currentStatus) {
        super("Order cannot be cancelled in status %s [orderId=%s]"
                .formatted(currentStatus, orderId));
    }
}