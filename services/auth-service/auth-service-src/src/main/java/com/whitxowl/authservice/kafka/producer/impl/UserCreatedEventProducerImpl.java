package com.whitxowl.authservice.kafka.producer.impl;

import com.whitxowl.authservice.domain.entity.UserEntity;
import com.whitxowl.authservice.kafka.producer.UserCreatedEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.whitxowl.authservice.events.auth.UserCreated;

import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventProducerImpl implements UserCreatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-created}")
    private String userCreatedTopic;

    @Override
    public void produce(UserEntity user) {
        UserCreated event = UserCreated.newBuilder()
                .setUserId(user.getId())
                .setEmail(user.getEmail())
                .setRoles(user.getRoles().stream()
                        .map(r -> r.getRole())
                        .collect(Collectors.toList()))
                .setCreatedAt(Instant.now())
                .build();

        String key = user.getId().toString();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doSend(key, event);
                        }
                    });
        } else {
            doSend(key, event);
        }
    }

    private void doSend(String key, UserCreated event) {
        kafkaTemplate.send(userCreatedTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.created for userId={}", key, ex);
                    } else {
                        log.info("Published user.created: userId={}, partition={}, offset={}",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
