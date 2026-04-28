package com.whitxowl.orderservice.service;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.domain.entity.OrderEntity;
import com.whitxowl.orderservice.exception.OrderCancellationNotAllowedException;
import com.whitxowl.orderservice.exception.OrderNotFoundException;
import com.whitxowl.orderservice.exception.OrderOwnershipException;
import com.whitxowl.orderservice.grpc.GrpcInventoryClient;
import com.whitxowl.orderservice.kafka.producer.OrderCreatedEventProducer;
import com.whitxowl.orderservice.mapper.OrderMapper;
import com.whitxowl.orderservice.repository.OrderRepository;
import com.whitxowl.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private GrpcInventoryClient grpcInventoryClient;

    @Mock
    private OrderCreatedEventProducer  orderCreatedEventProducer;

    @InjectMocks
    private OrderServiceImpl service;

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_shouldSaveAndPublish_whenStockAvailable() {
        CreateOrderRequest request = new CreateOrderRequest("product-1", 3);
        OrderEntity saved = orderEntity(OrderStatus.NEW);

        when(grpcInventoryClient.checkStock("product-1", 3)).thenReturn(true);
        when(orderRepository.save(any())).thenReturn(saved);
        when(orderMapper.toOrderResponse(saved)).thenReturn(orderResponse(saved));

        OrderResponse response = service.createOrder("user-1", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        verify(orderRepository).save(any());
        verify(orderCreatedEventProducer).produce(
                saved.getId().toString(), "user-1", "product-1", 3);
    }

    @Test
    void createOrder_shouldThrow_whenStockInsufficient() {
        CreateOrderRequest request = new CreateOrderRequest("product-1", 10);

        when(grpcInventoryClient.checkStock("product-1", 10)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrder("user-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product-1");

        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderCreatedEventProducer);
    }

    @Test
    void createOrder_shouldProceedOptimistically_whenGrpcUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest("product-1", 2);
        OrderEntity saved = orderEntity(OrderStatus.NEW);

        when(grpcInventoryClient.checkStock("product-1", 2))
                .thenThrow(new com.whitxowl.orderservice.exception.InventoryGrpcException("unavailable"));
        when(orderRepository.save(any())).thenReturn(saved);
        when(orderMapper.toOrderResponse(saved)).thenReturn(orderResponse(saved));

        OrderResponse response = service.createOrder("user-1", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        verify(orderRepository).save(any());
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_shouldReturnOrder_whenOwner() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(orderResponse(order));

        OrderResponse response = service.getOrder(id, "user-1", false);

        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    void getOrder_shouldReturnOrder_whenPrivileged() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(orderResponse(order));

        service.getOrder(id, "manager-1", true);

        verify(orderMapper).toOrderResponse(order);
    }

    @Test
    void getOrder_shouldThrowOwnership_whenNotOwnerAndNotPrivileged() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrder(id, "user-2", false))
                .isInstanceOf(OrderOwnershipException.class);
    }

    @Test
    void getOrder_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(id, "user-1", false))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_shouldCancel_whenStatusNew() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderMapper.toOrderResponse(any())).thenReturn(orderResponse(order));

        service.cancelOrder(id, "user-1", false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(grpcInventoryClient);
    }

    @Test
    void cancelOrder_shouldCancelAndReleaseReservation_whenStatusReserved() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.RESERVED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderMapper.toOrderResponse(any())).thenReturn(orderResponse(order));

        service.cancelOrder(id, "user-1", false);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(grpcInventoryClient).cancelReservation(id.toString());
    }

    @Test
    void cancelOrder_shouldBeIdempotent_whenAlreadyCancelled() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.CANCELLED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(orderResponse(order));

        service.cancelOrder(id, "user-1", false);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(grpcInventoryClient);
    }

    @Test
    void cancelOrder_shouldThrow_whenStatusPaid() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.PAID);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(id, "user-1", false))
                .isInstanceOf(OrderCancellationNotAllowedException.class);
    }

    @Test
    void cancelOrder_shouldThrowOwnership_whenNotOwnerAndNotPrivileged() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(id, "user-2", false))
                .isInstanceOf(OrderOwnershipException.class);
    }

    @Test
    void cancelOrder_shouldAllow_whenPrivilegedAndNotOwner() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderMapper.toOrderResponse(any())).thenReturn(orderResponse(order));

        service.cancelOrder(id, "manager-1", true);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ── handleInventoryReserved ───────────────────────────────────────────────

    @Test
    void handleInventoryReserved_shouldSetReserved_whenSuccess() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.handleInventoryReserved(id.toString(), true, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void handleInventoryReserved_shouldSetCancelled_whenFailure() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.NEW);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.handleInventoryReserved(id.toString(), false, "Insufficient stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void handleInventoryReserved_shouldBeIdempotent_whenNotInNewStatus() {
        UUID id = UUID.randomUUID();
        OrderEntity order = orderEntity(id, "user-1", OrderStatus.RESERVED);

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        service.handleInventoryReserved(id.toString(), true, null);

        verify(orderRepository, never()).save(any());
    }

    // ── getMyOrders / getAllOrders ─────────────────────────────────────────────

    @Test
    void getMyOrders_shouldReturnPagedResults() {
        OrderEntity order = orderEntity(OrderStatus.NEW);
        when(orderRepository.findAllByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderMapper.toOrderResponse(order)).thenReturn(orderResponse(order));

        var result = service.getMyOrders("user-1", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getAllOrders_shouldReturnPagedResults() {
        OrderEntity order = orderEntity(OrderStatus.NEW);
        when(orderRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderMapper.toOrderResponse(order)).thenReturn(orderResponse(order));

        var result = service.getAllOrders(0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OrderEntity orderEntity(OrderStatus status) {
        return orderEntity(UUID.randomUUID(), "user-1", status);
    }

    private OrderEntity orderEntity(UUID id, String userId, OrderStatus status) {
        return OrderEntity.builder()
                .id(id)
                .userId(userId)
                .productId("product-1")
                .quantity(2)
                .status(status)
                .build();
    }

    private OrderResponse orderResponse(OrderEntity entity) {
        return OrderResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .productId(entity.getProductId())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .build();
    }
}