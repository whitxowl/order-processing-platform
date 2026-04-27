package com.whitxowl.userservice.service.impl;

import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.domain.entity.UserEntity;
import com.whitxowl.userservice.exception.UserNotFoundException;
import com.whitxowl.userservice.kafka.producer.UserRoleChangedEventProducer;
import com.whitxowl.userservice.mapper.UserMapper;
import com.whitxowl.userservice.repository.UserRepository;
import com.whitxowl.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserRoleChangedEventProducer roleChangedEventProducer;

    @Override
    @Transactional
    public void createIfAbsent(String userId, String email) {
        UUID id = UUID.fromString(userId);

        if (userRepository.existsById(id)) {
            log.warn("User already exists, skipping [userId={}]", userId);
            return;
        }

        UserEntity user = UserEntity.builder()
                .id(id)
                .email(email)
                .build();

        userRepository.save(user);
        log.info("User profile created [userId={}, email={}]", userId, email);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        UserEntity user = findOrThrow(id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse addRole(UUID id, AddRoleRequest request) {
        UserEntity user = findOrThrow(id);
        String role = request.getRole();

        if (user.getRoleNames().contains(role)) {
            log.warn("User already has role, skipping [userId={}, role={}]", id, role);
            return userMapper.toResponse(user);
        }

        user.addRole(role);
        UserEntity saved = userRepository.save(user);

        log.info("Role added [userId={}, role={}]", id, role);
        roleChangedEventProducer.produce(saved);

        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse removeRole(UUID id, String role) {
        UserEntity user = findOrThrow(id);

        if (!user.getRoleNames().contains(role)) {
            log.warn("User does not have role, skipping [userId={}, role={}]", id, role);
            return userMapper.toResponse(user);
        }

        user.removeRole(role);
        UserEntity saved = userRepository.save(user);

        log.info("Role removed [userId={}, role={}]", id, role);
        roleChangedEventProducer.produce(saved);

        return userMapper.toResponse(saved);
    }

    private UserEntity findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}