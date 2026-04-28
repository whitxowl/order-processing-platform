package com.whitxowl.notificationservice.kafka.consumer;

import com.whitxowl.notificationservice.service.NotificationService;
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
public class UserRoleChangedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-role-changed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserRoleChanged(@Payload UserRoleChanged event, Acknowledgment acknowledgment) {
        log.info("Received user.role-changed [userId={}]", event.getUserId());
        try {
            notificationService.sendRoleChanged(event);
        } catch (Exception e) {
            log.error("Failed to process user.role-changed [userId={}]", event.getUserId(), e);
            return;
        }
        acknowledgment.acknowledge();
    }
}