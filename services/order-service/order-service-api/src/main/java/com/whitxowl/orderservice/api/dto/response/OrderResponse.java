package com.whitxowl.orderservice.api.dto.response;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class OrderResponse {

    private UUID        id;
    private String      userId;
    private String      productId;
    private int         quantity;
    private OrderStatus status;
    private Instant     createdAt;
    private Instant     updatedAt;
}