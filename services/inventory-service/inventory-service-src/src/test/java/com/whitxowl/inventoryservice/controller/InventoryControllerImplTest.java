package com.whitxowl.inventoryservice.controller;

import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import com.whitxowl.inventoryservice.api.dto.request.SetStockRequest;
import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.config.TestConfig;
import com.whitxowl.inventoryservice.exception.InventoryItemNotFoundException;
import com.whitxowl.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@Testcontainers
class InventoryControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InventoryService inventoryService;

    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String ADMIN_ROLE   = "ROLE_ADMIN";
    private static final String USER_ROLE    = "ROLE_USER";
    private static final String BASE_URL     = "/api/v1/inventory";

    // ── setStock ─────────────────────────────────────────────────────────────

    @Test
    void setStock_asManager_shouldReturn200() {
        when(inventoryService.setStock(eq("p1"), eq(10)))
                .thenReturn(StockResponse.builder()
                        .productId("p1").quantity(10).reserved(0).available(10).build());

        webTestClient.put().uri(BASE_URL + "/p1/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetStockRequest(10))
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productId").isEqualTo("p1")
                .jsonPath("$.quantity").isEqualTo(10);
    }

    @Test
    void setStock_withoutAuth_shouldReturn401() {
        webTestClient.put().uri(BASE_URL + "/p1/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetStockRequest(10))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void setStock_asUser_shouldReturn403() {
        webTestClient.put().uri(BASE_URL + "/p1/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetStockRequest(10))
                .header("X-User-Id", "user-1")
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void setStock_withInvalidQuantity_shouldReturn400() {
        webTestClient.put().uri(BASE_URL + "/p1/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetStockRequest(-1))
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.quantity").exists();
    }

    // ── getStock ─────────────────────────────────────────────────────────────

    @Test
    void getStock_asAdmin_shouldReturn200() {
        when(inventoryService.getStock("p1"))
                .thenReturn(StockResponse.builder()
                        .productId("p1").quantity(10).reserved(3).available(7).build());

        webTestClient.get().uri(BASE_URL + "/p1/stock")
                .header("X-User-Id", "admin-1")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.available").isEqualTo(7)
                .jsonPath("$.reserved").isEqualTo(3);
    }

    @Test
    void getStock_whenNotFound_shouldReturn404() {
        when(inventoryService.getStock("missing"))
                .thenThrow(new InventoryItemNotFoundException("missing"));

        webTestClient.get().uri(BASE_URL + "/missing/stock")
                .header("X-User-Id", "admin-1")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── getActiveReservations ─────────────────────────────────────────────────

    @Test
    void getActiveReservations_asManager_shouldReturn200() {
        when(inventoryService.getActiveReservations())
                .thenReturn(List.of(
                        ReservationResponse.builder()
                                .orderId("o1").productId("p1").quantity(2)
                                .status(ReservationStatus.RESERVED).build()));

        webTestClient.get().uri(BASE_URL + "/reservations")
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].orderId").isEqualTo("o1")
                .jsonPath("$[0].status").isEqualTo("RESERVED");
    }
}