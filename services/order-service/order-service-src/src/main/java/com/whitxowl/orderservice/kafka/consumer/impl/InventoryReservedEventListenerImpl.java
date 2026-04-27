package com.whitxowl.orderservice.kafka.consumer.impl;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.orderservice.kafka.consumer.InventoryReservedEventListener;
import com.whitxowl.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservedEventListenerImpl implements InventoryReservedEventListener {

    private final OrderService orderService;

    @Override
    @KafkaListener(
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onInventoryReserved(@Payload InventoryReserved event, Acknowledgment acknowledgment) {
        String orderId   = event.getOrderId();
        boolean success  = event.getSuccess();
        String reason    = event.getReason();

        log.info("Received inventory.reserved [orderId={}, success={}]", orderId, success);

        try {
            orderService.handleInventoryReserved(orderId, success, reason);
        } catch (Exception e) {
            log.error("Unexpected error processing inventory.reserved [orderId={}]", orderId, e);
            return;
        }

        acknowledgment.acknowledge();
    }
}