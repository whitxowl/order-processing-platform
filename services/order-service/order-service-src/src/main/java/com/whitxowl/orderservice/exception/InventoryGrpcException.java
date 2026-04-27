package com.whitxowl.orderservice.exception;

public class InventoryGrpcException extends RuntimeException {

    public InventoryGrpcException(String message) {
        super(message);
    }

    public InventoryGrpcException(String message, Throwable cause) {
        super(message, cause);
    }
}