package com.whitxowl.inventoryservice.kafka;

import com.whitxowl.inventoryservice.events.inventory.InventoryReserved;
import com.whitxowl.inventoryservice.kafka.producer.impl.InventoryReservedEventProducerImpl;
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

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReservedEventProducerImplTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryReservedEventProducerImpl producer;

    private final String topic = "inventory.reserved";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "inventoryReservedTopic", topic);
    }

    @Test
    void produceSuccess_shouldSendEventWithSuccessTrue_whenNoTransaction() {
        when(kafkaTemplate.send(eq(topic), eq("o1"), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        try (MockedStatic<TransactionSynchronizationManager> mocked =
                     mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);

            producer.produceSuccess("o1", "p1", 3);

            ArgumentCaptor<InventoryReserved> captor =
                    ArgumentCaptor.forClass(InventoryReserved.class);
            verify(kafkaTemplate).send(eq(topic), eq("o1"), captor.capture());

            InventoryReserved event = captor.getValue();
            assertThat(event.getOrderId()).isEqualTo("o1");
            assertThat(event.getProductId()).isEqualTo("p1");
            assertThat(event.getQuantity()).isEqualTo(3);
            assertThat(event.getSuccess()).isTrue();
            assertThat(event.getReason()).isNull();
        }
    }

    @Test
    void produceFailure_shouldSendEventWithSuccessFalseAndReason_whenNoTransaction() {
        when(kafkaTemplate.send(eq(topic), eq("o1"), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        try (MockedStatic<TransactionSynchronizationManager> mocked =
                     mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(false);

            producer.produceFailure("o1", "p1", 3, "Insufficient stock");

            ArgumentCaptor<InventoryReserved> captor =
                    ArgumentCaptor.forClass(InventoryReserved.class);
            verify(kafkaTemplate).send(eq(topic), eq("o1"), captor.capture());

            InventoryReserved event = captor.getValue();
            assertThat(event.getSuccess()).isFalse();
            assertThat(event.getReason()).isEqualTo("Insufficient stock");
        }
    }

    @Test
    void produceSuccess_shouldRegisterSynchronization_whenTransactionActive() {
        try (MockedStatic<TransactionSynchronizationManager> mocked =
                     mockStatic(TransactionSynchronizationManager.class)) {
            mocked.when(TransactionSynchronizationManager::isSynchronizationActive)
                    .thenReturn(true);

            producer.produceSuccess("o1", "p1", 3);

            verify(kafkaTemplate, never()).send(any(), any(), any());

            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            mocked.verify(() ->
                    TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            syncCaptor.getValue().afterCommit();

            verify(kafkaTemplate).send(eq(topic), eq("o1"), any(InventoryReserved.class));
        }
    }
}