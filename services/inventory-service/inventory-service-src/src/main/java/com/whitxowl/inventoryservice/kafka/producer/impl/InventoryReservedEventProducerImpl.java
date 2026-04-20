package com.whitxowl.inventoryservice.kafka.producer.impl;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.inventoryservice.kafka.producer.InventoryReservedEventProducer;
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
public class InventoryReservedEventProducerImpl implements InventoryReservedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.inventory-reserved}")
    private String inventoryReservedTopic;

    @Override
    public void produceSuccess(String orderId, String productId, int quantity) {
        produce(orderId, productId, quantity, true, null);
    }

    @Override
    public void produceFailure(String orderId, String productId, int quantity, String reason) {
        produce(orderId, productId, quantity, false, reason);
    }

    private void produce(String orderId, String productId, int quantity,
                         boolean success, String reason) {
        InventoryReserved event = InventoryReserved.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setSuccess(success)
                .setReason(reason)
                .setReservedAt(Instant.now())
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

    private void doSend(String key, InventoryReserved event) {
        kafkaTemplate.send(inventoryReservedTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish inventory.reserved [orderId={}]", key, ex);
                    } else {
                        log.info("Published inventory.reserved [orderId={}, success={}, partition={}, offset={}]",
                                key,
                                event.getSuccess(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}