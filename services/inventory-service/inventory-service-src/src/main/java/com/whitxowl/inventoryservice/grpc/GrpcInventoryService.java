package com.whitxowl.inventoryservice.grpc;

import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.exception.InventoryItemNotFoundException;
import com.whitxowl.inventoryservice.exception.ReservationNotFoundException;
import com.whitxowl.inventoryservice.service.InventoryService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void checkStock(CheckStockRequest request,
                           StreamObserver<CheckStockResponse> responseObserver) {
        String productId = request.getProductId();
        int    quantity  = request.getQuantity();

        log.info("gRPC checkStock [productId={}, quantity={}]", productId, quantity);

        try {
            StockResponse stock = inventoryService.getStock(productId);
            boolean available = stock.getAvailable() >= quantity;

            responseObserver.onNext(CheckStockResponse.newBuilder()
                    .setAvailable(available)
                    .setInStock(stock.getAvailable())
                    .build());

        } catch (InventoryItemNotFoundException e) {
            log.warn("gRPC checkStock: item not found [productId={}]", productId);
            responseObserver.onNext(CheckStockResponse.newBuilder()
                    .setAvailable(false)
                    .setInStock(0)
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void confirmReservation(ConfirmRequest request,
                                   StreamObserver<ConfirmResponse> responseObserver) {
        String orderId = request.getOrderId();

        log.info("gRPC confirmReservation [orderId={}]", orderId);

        try {
            inventoryService.confirmReservation(orderId);
            responseObserver.onNext(ConfirmResponse.newBuilder()
                    .setSuccess(true)
                    .build());

        } catch (ReservationNotFoundException | IllegalStateException e) {
            log.warn("gRPC confirmReservation failed [orderId={}]: {}", orderId, e.getMessage());
            responseObserver.onNext(ConfirmResponse.newBuilder()
                    .setSuccess(false)
                    .setReason(e.getMessage())
                    .build());
        }

        responseObserver.onCompleted();
    }

    @Override
    public void cancelReservation(CancelRequest request,
                                  StreamObserver<CancelResponse> responseObserver) {
        String orderId = request.getOrderId();

        log.info("gRPC cancelReservation [orderId={}]", orderId);

        try {
            inventoryService.cancelReservation(orderId);
            responseObserver.onNext(CancelResponse.newBuilder()
                    .setSuccess(true)
                    .build());

        } catch (ReservationNotFoundException e) {
            log.warn("gRPC cancelReservation failed [orderId={}]: {}", orderId, e.getMessage());
            responseObserver.onNext(CancelResponse.newBuilder()
                    .setSuccess(false)
                    .setReason(e.getMessage())
                    .build());
        }

        responseObserver.onCompleted();
    }
}