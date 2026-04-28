package com.whitxowl.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.exception.OrderCancellationNotAllowedException;
import com.whitxowl.orderservice.exception.OrderNotFoundException;
import com.whitxowl.orderservice.exception.OrderOwnershipException;
import com.whitxowl.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            ;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void createOrder_asUser_shouldReturn201() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = orderResponse(orderId, USER_ID, OrderStatus.NEW);
        when(orderService.createOrder(eq(USER_ID), any())).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest("product-1", 2)))
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void createOrder_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest("product-1", 2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_asManager_shouldReturn403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest("product-1", 2)))
                        .header("X-User-Id",    "manager-1")
                        .header("X-User-Roles", MGR_ROLE))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withInvalidRequest_shouldReturn400() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateOrderRequest(null, 0)))
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_asOwner_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.RESERVED));

        mockMvc.perform(get(BASE_URL + "/" + orderId)
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void getOrder_asManager_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq("manager-1"), eq(true)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.NEW));

        mockMvc.perform(get(BASE_URL + "/" + orderId)
                        .header("X-User-Id",    "manager-1")
                        .header("X-User-Roles", MGR_ROLE))
                .andExpect(status().isOk());
    }

    @Test
    void getOrder_whenNotFound_shouldReturn404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), any(), anyBoolean()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get(BASE_URL + "/" + orderId)
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrder_whenNotOwner_shouldReturn403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(eq(orderId), eq("other-user"), eq(false)))
                .thenThrow(new OrderOwnershipException(orderId));

        mockMvc.perform(get(BASE_URL + "/" + orderId)
                        .header("X-User-Id",    "other-user")
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
    }

    // ── getMyOrders ───────────────────────────────────────────────────────────

    @Test
    void getMyOrders_asUser_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getMyOrders(eq(USER_ID), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(orderResponse(orderId, USER_ID, OrderStatus.NEW))));

        mockMvc.perform(get(BASE_URL + "/my")
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()));
    }

    @Test
    void getMyOrders_withInvalidPage_shouldReturn400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my?page=-1")
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isBadRequest());
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Test
    void cancelOrder_asOwner_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.CANCELLED));

        mockMvc.perform(put(BASE_URL + "/" + orderId + "/cancel")
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_asManager_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq("manager-1"), eq(true)))
                .thenReturn(orderResponse(orderId, USER_ID, OrderStatus.CANCELLED));

        mockMvc.perform(put(BASE_URL + "/" + orderId + "/cancel")
                        .header("X-User-Id",    "manager-1")
                        .header("X-User-Roles", MGR_ROLE))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_whenNotCancellable_shouldReturn409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(eq(orderId), eq(USER_ID), eq(false)))
                .thenThrow(new OrderCancellationNotAllowedException(orderId, OrderStatus.SHIPPED));

        mockMvc.perform(put(BASE_URL + "/" + orderId + "/cancel")
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // ── getAllOrders ──────────────────────────────────────────────────────────

    @Test
    void getAllOrders_asManager_shouldReturn200() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getAllOrders(eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(orderResponse(orderId, USER_ID, OrderStatus.NEW))));

        mockMvc.perform(get(BASE_URL + "/all")
                        .header("X-User-Id",    "manager-1")
                        .header("X-User-Roles", MGR_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()));
    }

    @Test
    void getAllOrders_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/all")
                        .header("X-User-Id",    USER_ID)
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
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