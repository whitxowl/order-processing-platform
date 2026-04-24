package com.whitxowl.inventoryservice.service;

import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;

import java.util.List;

public interface InventoryService {

    StockResponse setStock(String productId, int quantity);

    StockResponse getStock(String productId);

    ReservationResponse reserve(String orderId, String productId, int quantity);

    ReservationResponse confirmReservation(String orderId);

    ReservationResponse cancelReservation(String orderId);

    List<ReservationResponse> getActiveReservations();
}
