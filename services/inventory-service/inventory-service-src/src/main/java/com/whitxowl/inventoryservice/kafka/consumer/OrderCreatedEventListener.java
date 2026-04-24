package com.whitxowl.inventoryservice.kafka.consumer;

import com.whitxowl.orderservice.events.order.OrderCreated;
import org.springframework.kafka.support.Acknowledgment;

public interface OrderCreatedEventListener {

    void onOrderCreated(OrderCreated event, Acknowledgment acknowledgment);
}