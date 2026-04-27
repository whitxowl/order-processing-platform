package com.whitxowl.authservice.kafka.consumer;

import com.whitxowl.userservice.events.user.UserRoleChanged;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;

public interface UserRoleChangedEventListener {

    void onUserRoleChanged(@Payload UserRoleChanged event, Acknowledgment acknowledgment);
}