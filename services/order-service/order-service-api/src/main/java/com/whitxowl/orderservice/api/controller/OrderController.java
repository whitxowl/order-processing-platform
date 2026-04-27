package com.whitxowl.orderservice.api.controller;

import com.whitxowl.orderservice.api.dto.request.CreateOrderRequest;
import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

import static com.whitxowl.orderservice.api.constant.ApiConstant.ORDER_URL;
import static com.whitxowl.orderservice.api.constant.RoleConstant.ROLE_ADMIN;
import static com.whitxowl.orderservice.api.constant.RoleConstant.ROLE_MANAGER;
import static com.whitxowl.orderservice.api.constant.RoleConstant.ROLE_USER;

@Tag(name = "Order Controller", description = "Управление заказами")
@RequestMapping(ORDER_URL)
public interface OrderController {

    @Operation(
            summary = "Create order",
            description = "Оформить заказ. Доступно ROLE_USER"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Заказ создан, ожидает резервирования"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured(ROLE_USER)
    @PostMapping
    ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    );

    @Operation(
            summary = "Get order by ID",
            description = "Получить заказ по ID. Доступно владельцу заказа (ROLE_USER) и персоналу"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные заказа"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Заказ не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_USER, ROLE_MANAGER, ROLE_ADMIN})
    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "UUID заказа", required = true)
            @PathVariable("id") UUID id
    );

    @Operation(
            summary = "Get my orders",
            description = "Получить список своих заказов с пагинацией. Доступно ROLE_USER"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список заказов текущего пользователя"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры пагинации"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured(ROLE_USER)
    @GetMapping("/my")
    ResponseEntity<List<OrderResponse>> getMyOrders(
            @Parameter(description = "Номер страницы (с 0)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Размер страницы (1–100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "Cancel order",
            description = """
                    Отменить заказ. Допустимо только в статусах NEW или RESERVED.
                    ROLE_USER может отменить только свой заказ.
                    ROLE_MANAGER и ROLE_ADMIN могут отменить любой заказ.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ отменён"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Заказ не найден"),
            @ApiResponse(responseCode = "409", description = "Заказ нельзя отменить в текущем статусе"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_USER, ROLE_MANAGER, ROLE_ADMIN})
    @PutMapping("/{id}/cancel")
    ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "UUID заказа", required = true)
            @PathVariable("id") UUID id
    );

    @Operation(
            summary = "Get all orders",
            description = "Получить все заказы платформы с пагинацией. Доступно ROLE_MANAGER, ROLE_ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список всех заказов"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры пагинации"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @GetMapping("/all")
    ResponseEntity<List<OrderResponse>> getAllOrders(
            @Parameter(description = "Номер страницы (с 0)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Размер страницы (1–100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    );
}