package com.whitxowl.orderservice.service.impl;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.domain.entity.OrderEntity;
import com.whitxowl.orderservice.exception.InventoryGrpcException;
import com.whitxowl.orderservice.exception.OrderCancellationNotAllowedException;
import com.whitxowl.orderservice.exception.OrderNotFoundException;
import com.whitxowl.orderservice.exception.OrderOwnershipException;
import com.whitxowl.orderservice.grpc.GrpcInventoryClient;
import com.whitxowl.orderservice.kafka.producer.OrderCreatedEventProducer;
import com.whitxowl.orderservice.kafka.producer.OrderStatusChangedEventProducer;
import com.whitxowl.orderservice.mapper.OrderMapper;
import com.whitxowl.orderservice.repository.OrderRepository;
import com.whitxowl.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<OrderStatus> CANCELLABLE_STATUSES =
            Set.of(OrderStatus.NEW, OrderStatus.RESERVED);

    private final OrderRepository            orderRepository;
    private final OrderMapper                orderMapper;
    private final GrpcInventoryClient        grpcInventoryClient;
    private final OrderCreatedEventProducer  orderCreatedEventProducer;
    private final OrderStatusChangedEventProducer orderStatusChangedEventProducer;

    @Override
    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request) {
        String productId = request.getProductId();
        int    quantity  = request.getQuantity();

        boolean inStock;
        try {
            inStock = grpcInventoryClient.checkStock(productId, quantity);
        } catch (InventoryGrpcException e) {
            log.warn("checkStock unavailable, proceeding optimistically [productId={}]", productId);
            inStock = true;
        }

        if (!inStock) {
            log.info("Insufficient stock, rejecting order [userId={}, productId={}, quantity={}]",
                    userId, productId, quantity);
            throw new IllegalArgumentException(
                    "Insufficient stock for productId=%s".formatted(productId));
        }

        OrderEntity order = OrderEntity.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .status(OrderStatus.NEW)
                .build();

        OrderEntity saved = orderRepository.save(order);
        log.info("Order created [orderId={}, userId={}, productId={}, quantity={}]",
                saved.getId(), userId, productId, quantity);

        orderCreatedEventProducer.produce(
                saved.getId().toString(), userId, productId, quantity);

        return orderMapper.toOrderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, String userId, boolean isPrivileged) {
        OrderEntity order = findById(orderId);

        if (!isPrivileged && !order.getUserId().equals(userId)) {
            throw new OrderOwnershipException(orderId);
        }

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findAllByUserId(userId, pageable)
                .map(orderMapper::toOrderResponse);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String userId, boolean isPrivileged) {
        OrderEntity order = findById(orderId);

        if (!isPrivileged && !order.getUserId().equals(userId)) {
            throw new OrderOwnershipException(orderId);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order already cancelled, skipping [orderId={}]", orderId);
            return orderMapper.toOrderResponse(order);
        }

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new OrderCancellationNotAllowedException(orderId, order.getStatus());
        }

        if (order.getStatus() == OrderStatus.RESERVED) {
            grpcInventoryClient.cancelReservation(orderId.toString());
        }

        order.setStatus(OrderStatus.CANCELLED);
        OrderEntity saved = orderRepository.save(order);
        log.info("Order cancelled [orderId={}, cancelledBy={}]", orderId, userId);

        orderStatusChangedEventProducer.produce(
                orderId.toString(), saved.getUserId(), saved.getProductId(),
                saved.getQuantity(), OrderStatus.CANCELLED.name());

        return orderMapper.toOrderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findAll(pageable)
                .map(orderMapper::toOrderResponse);
    }

    @Override
    @Transactional
    public void handleInventoryReserved(String orderId, boolean success, String reason) {
        UUID id = UUID.fromString(orderId);
        OrderEntity order = findById(id);

        if (order.getStatus() != OrderStatus.NEW) {
            log.warn("Skipping inventory.reserved: order not in NEW status [orderId={}, status={}]",
                    orderId, order.getStatus());
            return;
        }

        if (success) {
            order.setStatus(OrderStatus.RESERVED);
            log.info("Order status -> RESERVED [orderId={}]", orderId);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            log.info("Order status -> CANCELLED (insufficient stock) [orderId={}, reason={}]",
                    orderId, reason);
        }

        orderRepository.save(order);

        orderStatusChangedEventProducer.produce(
                orderId, order.getUserId(), order.getProductId(),
                order.getQuantity(), order.getStatus().name());
    }

    private OrderEntity findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
