package com.whitxowl.inventoryservice.exception;

public class InventoryItemNotFoundException extends RuntimeException {

    public InventoryItemNotFoundException(String productId) {
        super("Inventory item not found [productId=%s]".formatted(productId));
    }
}