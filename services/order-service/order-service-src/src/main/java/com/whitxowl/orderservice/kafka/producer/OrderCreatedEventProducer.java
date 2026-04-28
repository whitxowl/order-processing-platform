package com.whitxowl.orderservice.kafka.producer;

public interface OrderCreatedEventProducer {

    void produce(String orderId, String userId, String productId, int quantity);
}