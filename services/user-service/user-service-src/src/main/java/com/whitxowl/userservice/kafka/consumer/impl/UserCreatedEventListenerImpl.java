package com.whitxowl.userservice.kafka.consumer.impl;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.userservice.kafka.consumer.UserCreatedEventListener;
import com.whitxowl.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventListenerImpl implements UserCreatedEventListener {

    private final UserService userService;

    @Override
    @KafkaListener(
            topics = "${app.kafka.topics.user-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserCreated(@Payload UserCreated event, Acknowledgment acknowledgment) {
        String userId = event.getUserId().toString();
        String email  = event.getEmail();

        log.info("Received user.created [userId={}, email={}]", userId, email);

        try {
            userService.createIfAbsent(userId, email);
        } catch (Exception e) {
            log.error("Failed to process user.created [userId={}]", userId, e);
            return;
        }

        acknowledgment.acknowledge();
    }
}