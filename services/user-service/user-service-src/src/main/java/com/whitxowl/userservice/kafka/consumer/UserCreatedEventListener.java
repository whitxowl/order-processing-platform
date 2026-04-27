package com.whitxowl.userservice.kafka.consumer;

import com.whitxowl.authservice.events.auth.UserCreated;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;

public interface UserCreatedEventListener {

    void onUserCreated(@Payload UserCreated event, Acknowledgment acknowledgment);
}