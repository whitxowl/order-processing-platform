package com.whitxowl.notificationservice.kafka.consumer;

import com.whitxowl.notificationservice.service.NotificationService;
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
public class OrderCreatedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.order-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderCreated(@Payload OrderCreated event, Acknowledgment acknowledgment) {
        log.info("Received order.created [orderId={}]", event.getOrderId());
        try {
            notificationService.sendOrderCreated(event);
        } catch (Exception e) {
            log.error("Failed to process order.created [orderId={}]", event.getOrderId(), e);
            return;
        }
        acknowledgment.acknowledge();
    }
}