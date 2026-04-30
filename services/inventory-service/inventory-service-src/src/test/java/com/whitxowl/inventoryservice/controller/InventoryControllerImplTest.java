package com.whitxowl.inventoryservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import com.whitxowl.inventoryservice.api.dto.request.SetStockRequest;
import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.exception.InventoryItemNotFoundException;
import com.whitxowl.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class InventoryControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String ADMIN_ROLE   = "ROLE_ADMIN";
    private static final String USER_ROLE    = "ROLE_USER";
    private static final String BASE_URL     = "/api/v1/inventory";

    // ── setStock ─────────────────────────────────────────────────────────────

    @Test
    void setStock_asManager_shouldReturn200() throws Exception {
        when(inventoryService.setStock(eq("p1"), eq(10)))
                .thenReturn(StockResponse.builder()
                        .productId("p1").quantity(10).reserved(0).available(10).build());

        mockMvc.perform(put(BASE_URL + "/p1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetStockRequest(10)))
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("p1"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void setStock_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(put(BASE_URL + "/p1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetStockRequest(10))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setStock_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(put(BASE_URL + "/p1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetStockRequest(10)))
                        .header("X-User-Id", "user-1")
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
    }

    @Test
    void setStock_withInvalidQuantity_shouldReturn400() throws Exception {
        mockMvc.perform(put(BASE_URL + "/p1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetStockRequest(-1)))
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    // ── getStock ─────────────────────────────────────────────────────────────

    @Test
    void getStock_asAdmin_shouldReturn200() throws Exception {
        when(inventoryService.getStock("p1"))
                .thenReturn(StockResponse.builder()
                        .productId("p1").quantity(10).reserved(3).available(7).build());

        mockMvc.perform(get(BASE_URL + "/p1/stock")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(7))
                .andExpect(jsonPath("$.reserved").value(3));
    }

    @Test
    void getStock_whenNotFound_shouldReturn404() throws Exception {
        when(inventoryService.getStock("missing"))
                .thenThrow(new InventoryItemNotFoundException("missing"));

        mockMvc.perform(get(BASE_URL + "/missing/stock")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isNotFound());
    }

    // ── getActiveReservations ─────────────────────────────────────────────────

    @Test
    void getActiveReservations_asManager_shouldReturn200() throws Exception {
        when(inventoryService.getActiveReservations())
                .thenReturn(List.of(
                        ReservationResponse.builder()
                                .orderId("o1").productId("p1").quantity(2)
                                .status(ReservationStatus.RESERVED).build()));

        mockMvc.perform(get(BASE_URL + "/reservations")
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("o1"))
                .andExpect(jsonPath("$[0].status").value("RESERVED"));
    }
}