package com.whitxowl.inventoryservice.kafka.consumer.impl;

import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.kafka.consumer.OrderCreatedEventListener;
import com.whitxowl.inventoryservice.service.InventoryService;
import com.whitxowl.orderservice.events.order.OrderCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventListenerImpl implements OrderCreatedEventListener {

    private final InventoryService inventoryService;

    @Override
    @KafkaListener(
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderCreated(@Payload OrderCreated event, Acknowledgment acknowledgment) {
        String orderId   = event.getOrderId();
        String productId = event.getProductId();
        int    quantity  = event.getQuantity();

        log.info("Received order.created [orderId={}, productId={}, quantity={}]",
                orderId, productId, quantity);

        try {
            inventoryService.reserve(orderId, productId, quantity);
        } catch (DuplicateReservationException e) {
            log.warn("Duplicate order.created skipped [orderId={}]", orderId);
        } catch (Exception e) {
            log.error("Unexpected error processing order.created [orderId={}]", orderId, e);
            return;
        }

        acknowledgment.acknowledge();
    }
}