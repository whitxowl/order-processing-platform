package com.whitxowl.inventoryservice.controller;

import com.whitxowl.inventoryservice.api.controller.InventoryController;
import com.whitxowl.inventoryservice.api.dto.request.SetStockRequest;
import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InventoryControllerImpl implements InventoryController {

    private final InventoryService inventoryService;

    @Override
    public ResponseEntity<StockResponse> setStock(String productId, SetStockRequest request) {
        StockResponse response = inventoryService.setStock(productId, request.getQuantity());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<StockResponse> getStock(String productId) {
        StockResponse response = inventoryService.getStock(productId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<ReservationResponse>> getActiveReservations() {
        List<ReservationResponse> response = inventoryService.getActiveReservations();
        return ResponseEntity.ok(response);
    }
}