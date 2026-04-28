package com.whitxowl.notificationservice.kafka;

import com.whitxowl.notificationservice.service.NotificationService;
import com.whitxowl.orderservice.events.order.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.order-status-changed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderStatusChanged(@Payload OrderStatusChanged event, Acknowledgment acknowledgment) {
        log.info("Received order.status-changed [orderId={}, status={}]",
                event.getOrderId(), event.getStatus());
        try {
            notificationService.sendOrderStatusChanged(event);
        } catch (Exception e) {
            log.error("Failed to process order.status-changed [orderId={}]", event.getOrderId(), e);
            return;
        }
        acknowledgment.acknowledge();
    }
}