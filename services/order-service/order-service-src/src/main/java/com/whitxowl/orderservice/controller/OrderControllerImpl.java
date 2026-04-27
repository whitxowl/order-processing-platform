package com.whitxowl.orderservice.controller;

import com.whitxowl.orderservice.api.controller.OrderController;
import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.api.constant.RoleConstant;
import com.whitxowl.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderControllerImpl implements OrderController {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderResponse> createOrder(CreateOrderRequest request) {
        String userId = currentUserId();
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(UUID id) {
        String userId = currentUserId();
        OrderResponse response = orderService.getOrder(id, userId, isPrivileged());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<OrderResponse>> getMyOrders(int page, int size) {
        String userId = currentUserId();
        Page<OrderResponse> result = orderService.getMyOrders(userId, page, size);
        return ResponseEntity.ok(result.getContent());
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UUID id) {
        String userId = currentUserId();
        OrderResponse response = orderService.cancelOrder(id, userId, isPrivileged());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<OrderResponse>> getAllOrders(int page, int size) {
        Page<OrderResponse> result = orderService.getAllOrders(page, size);
        return ResponseEntity.ok(result.getContent());
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals(RoleConstant.ROLE_MANAGER)
                        || role.equals(RoleConstant.ROLE_ADMIN));
    }
}