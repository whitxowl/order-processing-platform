package com.whitxowl.userservice.service;

import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.domain.entity.UserEntity;
import com.whitxowl.userservice.exception.UserNotFoundException;
import com.whitxowl.userservice.kafka.producer.UserRoleChangedEventProducer;
import com.whitxowl.userservice.mapper.UserMapper;
import com.whitxowl.userservice.repository.UserRepository;
import com.whitxowl.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleChangedEventProducer roleChangedEventProducer;

    @InjectMocks
    private UserServiceImpl userService;

    private UserEntity buildUser(UUID id) {
        return UserEntity.builder()
                .id(id)
                .email("user@example.com")
                .build();
    }

    // ── createIfAbsent ────────────────────────────────────────────────────────

    @Test
    void createIfAbsent_shouldSaveUser_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        userService.createIfAbsent(id.toString(), "user@example.com");

        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void createIfAbsent_shouldSkip_whenAlreadyExists() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.createIfAbsent(id.toString(), "user@example.com");

        verify(userRepository, never()).save(any());
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_shouldReturnResponse_whenFound() {
        UUID id = UUID.randomUUID();
        UserEntity user = buildUser(id);
        UserResponse response = UserResponse.builder().id(id).email("user@example.com").build();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.getUser(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void getUser_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── addRole ───────────────────────────────────────────────────────────────

    @Test
    void addRole_shouldAddRoleAndProduceEvent_whenUserFound() {
        UUID id = UUID.randomUUID();
        UserEntity user = buildUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().id(id).build());

        userService.addRole(id, new AddRoleRequest("ROLE_MANAGER"));

        assertThat(user.getRoleNames()).contains("ROLE_MANAGER");
        verify(userRepository).save(user);
        verify(roleChangedEventProducer).produce(user);
    }

    @Test
    void addRole_shouldSkip_whenRoleAlreadyPresent() {
        UUID id = UUID.randomUUID();
        UserEntity user = buildUser(id);
        user.addRole("ROLE_MANAGER");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().id(id).build());

        userService.addRole(id, new AddRoleRequest("ROLE_MANAGER"));

        verify(userRepository, never()).save(any());
        verify(roleChangedEventProducer, never()).produce(any());
    }

    @Test
    void addRole_shouldThrow_whenUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.addRole(id, new AddRoleRequest("ROLE_MANAGER")))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── removeRole ────────────────────────────────────────────────────────────

    @Test
    void removeRole_shouldRemoveRoleAndProduceEvent_whenRolePresent() {
        UUID id = UUID.randomUUID();
        UserEntity user = buildUser(id);
        user.addRole("ROLE_MANAGER");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().id(id).build());

        userService.removeRole(id, "ROLE_MANAGER");

        assertThat(user.getRoleNames()).doesNotContain("ROLE_MANAGER");
        verify(userRepository).save(user);
        verify(roleChangedEventProducer).produce(user);
    }

    @Test
    void removeRole_shouldSkip_whenRoleNotPresent() {
        UUID id = UUID.randomUUID();
        UserEntity user = buildUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().id(id).build());

        userService.removeRole(id, "ROLE_MANAGER");

        verify(userRepository, never()).save(any());
        verify(roleChangedEventProducer, never()).produce(any());
    }

    @Test
    void removeRole_shouldThrow_whenUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.removeRole(id, "ROLE_MANAGER"))
                .isInstanceOf(UserNotFoundException.class);
    }
}