package com.whitxowl.inventoryservice.api.dto.response;

import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class ReservationResponse {

    private Long              id;
    private String            orderId;
    private String            productId;
    private int               quantity;
    private ReservationStatus status;
    private Instant           createdAt;
    private Instant           updatedAt;
}