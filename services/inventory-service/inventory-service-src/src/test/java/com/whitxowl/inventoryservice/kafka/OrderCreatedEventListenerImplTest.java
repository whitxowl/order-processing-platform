package com.whitxowl.inventoryservice.kafka;

import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.exception.InsufficientStockException;
import com.whitxowl.inventoryservice.kafka.consumer.impl.OrderCreatedEventListenerImpl;
import com.whitxowl.inventoryservice.service.InventoryService;
import com.whitxowl.orderservice.events.order.OrderCreated;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreatedEventListenerImplTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderCreatedEventListenerImpl listener;

    private OrderCreated buildEvent(String orderId, String productId, int quantity) {
        return OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("user-1")
                .setProductId(productId)
                .setQuantity(quantity)
                .setCreatedAt(Instant.now())
                .build();
    }

    @Test
    void onOrderCreated_shouldReserveAndAcknowledge_whenSuccess() {
        OrderCreated event = buildEvent("o1", "p1", 3);

        listener.onOrderCreated(event, acknowledgment);

        verify(inventoryService).reserve("o1", "p1", 3);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderCreated_shouldAcknowledgeWithoutReserving_whenDuplicate() {
        OrderCreated event = buildEvent("o1", "p1", 3);
        doThrow(new DuplicateReservationException("o1"))
                .when(inventoryService).reserve("o1", "p1", 3);

        listener.onOrderCreated(event, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderCreated_shouldAcknowledgeOnInsufficientStock() {
        OrderCreated event = buildEvent("o1", "p1", 100);
        doThrow(new InsufficientStockException("p1", 100, 5))
                .when(inventoryService).reserve("o1", "p1", 100);

        // InsufficientStockException не является DuplicateReservationException,
        // поэтому попадёт в catch(Exception) и offset НЕ будет подтверждён
        listener.onOrderCreated(event, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onOrderCreated_shouldNotAcknowledge_whenUnexpectedException() {
        OrderCreated event = buildEvent("o1", "p1", 3);
        doThrow(new RuntimeException("DB down"))
                .when(inventoryService).reserve("o1", "p1", 3);

        listener.onOrderCreated(event, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }
}