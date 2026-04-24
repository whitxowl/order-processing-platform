package com.whitxowl.authservice.kafka;

import com.whitxowl.authservice.domain.entity.RoleEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import com.whitxowl.authservice.events.auth.UserCreated;
import com.whitxowl.authservice.kafka.producer.impl.UserCreatedEventProducerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCreatedEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserCreatedEventProducerImpl producer;

    private final String topic = "user-created-topic";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "userCreatedTopic", topic);
    }

    @Test
    void produce_ShouldSendImmediately_WhenNoTransactionActive() {
        UserEntity user = createTestUser();

        when(kafkaTemplate.send(eq(topic), eq(user.getId().toString()), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(false);

            producer.produce(user);

            ArgumentCaptor<UserCreated> eventCaptor = ArgumentCaptor.forClass(UserCreated.class);
            verify(kafkaTemplate).send(eq(topic), eq(user.getId().toString()), eventCaptor.capture());

            UserCreated capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getUserId()).isEqualTo(user.getId());
            assertThat(capturedEvent.getEmail()).isEqualTo(user.getEmail());
            assertThat(capturedEvent.getRoles()).containsExactly("ROLE_USER");
        }
    }

    @Test
    void produce_ShouldRegisterSynchronization_WhenTransactionIsActive() {
        UserEntity user = createTestUser();

        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {

            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            producer.produce(user);

            verify(kafkaTemplate, never()).send(any(), any(), any());

            ArgumentCaptor<TransactionSynchronization> syncCaptor = ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

            TransactionSynchronization capturedSync = syncCaptor.getValue();

            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            capturedSync.afterCommit();

            verify(kafkaTemplate).send(eq(topic), eq(user.getId().toString()), any(UserCreated.class));
        }
    }

    private UserEntity createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");

        RoleEntity role = new RoleEntity();
        role.setRole("ROLE_USER");
        user.setRoles(Set.of(role));

        return user;
    }
}
