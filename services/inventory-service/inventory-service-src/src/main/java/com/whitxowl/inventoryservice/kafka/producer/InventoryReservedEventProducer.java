package com.whitxowl.inventoryservice.kafka.producer;

public interface InventoryReservedEventProducer {

    void produceSuccess(String orderId, String productId, int quantity);

    void produceFailure(String orderId, String productId, int quantity, String reason);
}