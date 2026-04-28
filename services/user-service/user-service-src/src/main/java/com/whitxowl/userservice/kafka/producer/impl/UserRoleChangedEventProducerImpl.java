package com.whitxowl.userservice.kafka.producer.impl;

import com.whitxowl.userservice.domain.entity.UserEntity;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import com.whitxowl.userservice.kafka.producer.UserRoleChangedEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRoleChangedEventProducerImpl implements UserRoleChangedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-role-changed}")
    private String userRoleChangedTopic;

    @Override
    public void produce(UserEntity user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getRole())
                .collect(Collectors.toList());

        UserRoleChanged event = UserRoleChanged.newBuilder()
                .setUserId(user.getId().toString())
                .setRoles(roles)
                .setChangedAt(Instant.now())
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

    private void doSend(String key, UserRoleChanged event) {
        kafkaTemplate.send(userRoleChangedTopic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.role-changed [userId={}]", key, ex);
                    } else {
                        log.info("Published user.role-changed [userId={}, roles={}, partition={}, offset={}]",
                                key,
                                event.getRoles(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}