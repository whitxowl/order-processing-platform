package com.whitxowl.inventoryservice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StockResponse {

    private String  productId;
    private int     quantity;   // физический остаток на складе
    private int     reserved;   // зарезервировано под активные заказы
    private int     available;  // quantity - reserved, реально доступно
}