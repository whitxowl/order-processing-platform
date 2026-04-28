package com.whitxowl.notificationservice.kafka.consumer;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onInventoryReserved(@Payload InventoryReserved event, Acknowledgment acknowledgment) {
        log.info("Received inventory.reserved [orderId={}, success={}]",
                event.getOrderId(), event.getSuccess());
        try {
            notificationService.sendInventoryReserved(event);
        } catch (Exception e) {
            log.error("Failed to process inventory.reserved [orderId={}]", event.getOrderId(), e);
            return;
        }
        acknowledgment.acknowledge();
    }
}