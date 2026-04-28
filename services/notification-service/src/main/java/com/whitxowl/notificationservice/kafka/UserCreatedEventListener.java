package com.whitxowl.notificationservice.kafka.consumer;

import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.user-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserCreated(@Payload UserCreated event, Acknowledgment acknowledgment) {
        log.info("Received user.created [userId={}]", event.getUserId());
        try {
            notificationService.sendUserCreated(event);
        } catch (Exception e) {
            log.error("Failed to process user.created [userId={}]", event.getUserId(), e);
            return;
        }
        acknowledgment.acknowledge();
    }
}