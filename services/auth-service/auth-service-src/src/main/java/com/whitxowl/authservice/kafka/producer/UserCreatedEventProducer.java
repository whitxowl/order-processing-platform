package com.whitxowl.authservice.kafka.producer;

import com.whitxowl.authservice.domain.entity.UserEntity;

public interface UserCreatedEventProducer {
    void produce(UserEntity user);
}