package com.whitxowl.authservice.kafka.consumer.impl;

import com.whitxowl.authservice.kafka.consumer.UserRoleChangedEventListener;
import com.whitxowl.authservice.service.AuthService;
import com.whitxowl.userservice.events.user.UserRoleChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRoleChangedEventListenerImpl implements UserRoleChangedEventListener {

    private final AuthService authService;

    @Override
    @KafkaListener(
            topics = "${app.kafka.topics.user-role-changed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserRoleChanged(@Payload UserRoleChanged event, Acknowledgment acknowledgment) {
        String userId = event.getUserId();
        log.info("Received user.role-changed [userId={}, roles={}]", userId, event.getRoles());

        try {
            authService.syncRoles(userId, event.getRoles());
        } catch (Exception e) {
            log.error("Failed to process user.role-changed [userId={}]", userId, e);
            return;
        }

        acknowledgment.acknowledge();
    }
}