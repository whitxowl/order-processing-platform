package com.whitxowl.orderservice.service;

import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(String userId, CreateOrderRequest request);

    OrderResponse getOrder(UUID orderId, String userId, boolean isPrivileged);

    Page<OrderResponse> getMyOrders(String userId, int page, int size);

    OrderResponse cancelOrder(UUID orderId, String userId, boolean isPrivileged);

    Page<OrderResponse> getAllOrders(int page, int size);

    void handleInventoryReserved(String orderId, boolean success, String reason);
}