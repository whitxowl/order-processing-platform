package com.whitxowl.inventoryservice.api.controller;

import com.whitxowl.inventoryservice.api.dto.request.SetStockRequest;
import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static com.whitxowl.inventoryservice.api.constant.ApiConstant.INVENTORY_URL;
import static com.whitxowl.inventoryservice.api.constant.RoleConstant.ROLE_ADMIN;
import static com.whitxowl.inventoryservice.api.constant.RoleConstant.ROLE_MANAGER;

@Tag(name = "Inventory Controller", description = "Управление складскими остатками")
@RequestMapping(INVENTORY_URL)
public interface InventoryController {

    @Operation(
            summary = "Set stock",
            description = "Установить количество товара на складе. Доступно ROLE_MANAGER, ROLE_ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Остаток обновлён"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден в inventory"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PutMapping("/{productId}/stock")
    ResponseEntity<StockResponse> setStock(
            @Parameter(description = "ID товара (MongoDB ObjectId из product-service)", required = true)
            @PathVariable("productId") String productId,
            @Valid @RequestBody SetStockRequest request
    );

    @Operation(
            summary = "Get stock",
            description = "Получить текущий остаток по товару. Доступно ROLE_MANAGER, ROLE_ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные об остатке"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден в inventory"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @GetMapping("/{productId}/stock")
    ResponseEntity<StockResponse> getStock(
            @Parameter(description = "ID товара", required = true)
            @PathVariable("productId") String productId
    );

    @Operation(
            summary = "Get active reservations",
            description = "Список всех активных резерваций (статус RESERVED). Доступно ROLE_MANAGER, ROLE_ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список резерваций"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @GetMapping("/reservations")
    ResponseEntity<List<ReservationResponse>> getActiveReservations();
}