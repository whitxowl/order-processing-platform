package com.whitxowl.orderservice.kafka.producer;

public interface OrderStatusChangedEventProducer {
    void produce(String orderId, String userId, String productId, int quantity, String status);
}
