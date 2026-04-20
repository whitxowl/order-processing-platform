package com.whitxowl.inventoryservice.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetStockRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity can't be less then 0")
    private Integer quantity;
}