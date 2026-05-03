package com.whitxowl.orderservice.controller;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.config.TestConfig;
import com.whitxowl.orderservice.exception.OrderCancellationNotAllowedException;
import com.whitxowl.orderservice.exception.OrderNotFoundException;
import com.whitxowl.orderservice.exception.OrderOwnershipException;
import com.whitxowl.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@Testcontainers
class OrderControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String USER_ID    = "user-uuid-1";
    private static final String USER_ROLE  = "ROLE_USER";
    private static final String MGR_ROLE   = "ROLE_MANAGER";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String BASE_URL   = "/api/v1/orders";

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    void createOrder_asUser_shouldReturn201() {
        UUID orderId = UUID.randomUUID();
        when(orderService.createOrder(eq(USER_ID), any()))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.NEW));

        webTestClient.post().uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest("product-1", 2))
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId.toString())
                .jsonPath("$.status").isEqualTo("NEW");
    }

    @Test
    void createOrder_withoutAuth_shouldReturn401() {
        webTestClient.post().uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest("product-1", 2))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createOrder_asManager_shouldReturn403() {
        webTestClient.post().uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest("product-1", 2))
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MGR_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createOrder_withInvalidRequest_shouldReturn400() {
        webTestClient.post().uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateOrderRequest(null, 0))
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").exists();
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_asOwner_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.RESERVED));

        webTestClient.get().uri(BASE_URL + "/" + orderId)
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("RESERVED");
    }

    @Test
    void getOrder_asManager_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq("manager-1"), eq(true)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.NEW));

        webTestClient.get().uri(BASE_URL + "/" + orderId)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MGR_ROLE)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrder_whenNotFound_shouldReturn404() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), any(), anyBoolean()))
                .thenThrow(new OrderNotFoundException(orderId));

        webTestClient.get().uri(BASE_URL + "/" + orderId)
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getOrder_whenNotOwner_shouldReturn403() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq("other-user"), eq(false)))
                .thenThrow(new OrderOwnershipException(orderId));

        webTestClient.get().uri(BASE_URL + "/" + orderId)
                .header("X-User-Id", "other-user")
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── getMyOrders ───────────────────────────────────────────────────────────

    @Test
    void getMyOrders_asUser_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getMyOrders(eq(USER_ID), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(orderResponse(orderId, USER_ID, OrderStatus.NEW))));

        webTestClient.get().uri(BASE_URL + "/my")
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(orderId.toString());
    }

    @Test
    void getMyOrders_withInvalidPage_shouldReturn400() {
        webTestClient.get().uri(BASE_URL + "/my?page=-1")
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_asOwner_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.CANCELLED));

        webTestClient.put().uri(BASE_URL + "/" + orderId + "/cancel")
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void cancelOrder_asManager_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq("manager-1"), eq(true)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.CANCELLED));

        webTestClient.put().uri(BASE_URL + "/" + orderId + "/cancel")
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MGR_ROLE)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cancelOrder_whenNotCancellable_shouldReturn409() {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenThrow(new OrderCancellationNotAllowedException(orderId, OrderStatus.SHIPPED));

        webTestClient.put().uri(BASE_URL + "/" + orderId + "/cancel")
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void cancelOrder_withoutAuth_shouldReturn401() {
        webTestClient.put().uri(BASE_URL + "/" + UUID.randomUUID() + "/cancel")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── getAllOrders ──────────────────────────────────────────────────────────

    @Test
    void getAllOrders_asManager_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getAllOrders(eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(orderResponse(orderId, USER_ID, OrderStatus.NEW))));

        webTestClient.get().uri(BASE_URL + "/all")
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MGR_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(orderId.toString());
    }

    @Test
    void getAllOrders_asUser_shouldReturn403() {
        webTestClient.get().uri(BASE_URL + "/all")
                .header("X-User-Id", USER_ID)
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private OrderResponse orderResponse(UUID id, String userId, OrderStatus status) {
        return OrderResponse.builder()
                .id(id)
                .userId(userId)
                .productId("product-1")
                .quantity(2)
                .status(status)
                .build();
    }
}