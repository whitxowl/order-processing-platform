package com.whitxowl.orderservice.kafka.producer.impl;

import com.whitxowl.orderservice.events.order.OrderCreated;
import com.whitxowl.orderservice.kafka.producer.OrderCreatedEventProducer;
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
public class OrderCreatedEventProducerImpl implements OrderCreatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.order-created}")
    private String orderCreatedTopic;

    @Override
    public void produce(String orderId, String userId, String productId, int quantity) {
        OrderCreated event = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setCreatedAt(Instant.now())
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

    private void doSend(String key, OrderCreated event) {
        kafkaTemplate.send(orderCreatedTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order.created [orderId={}]", key, ex);
                    } else {
                        log.info("Published order.created [orderId={}, partition={}, offset={}]",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}