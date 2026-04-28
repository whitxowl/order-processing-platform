package com.whitxowl.userservice.kafka.producer;

import com.whitxowl.userservice.domain.entity.UserEntity;

public interface UserRoleChangedEventProducer {

    void produce(UserEntity user);
}