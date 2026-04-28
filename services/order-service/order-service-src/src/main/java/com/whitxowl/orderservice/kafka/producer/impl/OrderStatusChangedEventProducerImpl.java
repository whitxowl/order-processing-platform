package com.whitxowl.orderservice.kafka.producer.impl;

import com.whitxowl.orderservice.events.order.OrderStatusChanged;
import com.whitxowl.orderservice.kafka.producer.OrderStatusChangedEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedEventProducerImpl implements OrderStatusChangedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.order-status-changed}")
    private String topic;

    @Override
    public void produce(String orderId, String userId, String productId, int quantity, String status) {
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setStatus(status)
                .setChangedAt(Instant.now())
                .build();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doSend(orderId, event);
                        }
                    });
        } else {
            doSend(orderId, event);
        }
    }

    private void doSend(String key, OrderStatusChanged event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order.status-changed [orderId={}, status={}]",
                                key, event.getStatus(), ex);
                    } else {
                        log.info("Published order.status-changed [orderId={}, status={}, partition={}, offset={}]",
                                key, event.getStatus(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}