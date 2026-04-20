package com.whitxowl.inventoryservice.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock [productId=%s]: requested=%d, available=%d"
                .formatted(productId, requested, available));
    }
}