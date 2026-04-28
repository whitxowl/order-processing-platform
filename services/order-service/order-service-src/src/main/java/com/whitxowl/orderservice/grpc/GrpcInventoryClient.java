package com.whitxowl.orderservice.grpc;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import com.whitxowl.inventoryservice.grpc.CancelRequest;
import com.whitxowl.inventoryservice.grpc.CancelResponse;
import com.whitxowl.inventoryservice.grpc.CheckStockRequest;
import com.whitxowl.inventoryservice.grpc.CheckStockResponse;
import com.whitxowl.inventoryservice.grpc.ConfirmRequest;
import com.whitxowl.inventoryservice.grpc.ConfirmResponse;
import com.whitxowl.inventoryservice.grpc.InventoryServiceGrpc;
import com.whitxowl.orderservice.exception.InventoryGrpcException;

@Slf4j
@Component
public class GrpcInventoryClient {

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    public boolean checkStock(String productId, int quantity) {
        log.info("gRPC checkStock [productId={}, quantity={}]", productId, quantity);

        try {
            CheckStockResponse response = stub.checkStock(
                    CheckStockRequest.newBuilder()
                            .setProductId(productId)
                            .setQuantity(quantity)
                            .build()
            );
            log.info("gRPC checkStock result [productId={}, available={}, inStock={}]",
                    productId, response.getAvailable(), response.getInStock());
            return response.getAvailable();

        } catch (StatusRuntimeException e) {
            log.error("gRPC checkStock failed [productId={}]: {}", productId, e.getStatus());
            throw new InventoryGrpcException(
                    "Failed to check stock for productId=%s".formatted(productId), e);
        }
    }

    public void confirmReservation(String orderId) {
        log.info("gRPC confirmReservation [orderId={}]", orderId);

        try {
            ConfirmResponse response = stub.confirmReservation(
                    ConfirmRequest.newBuilder()
                            .setOrderId(orderId)
                            .build()
            );
            if (!response.getSuccess()) {
                log.warn("gRPC confirmReservation returned failure [orderId={}]: {}",
                        orderId, response.getReason());
                throw new InventoryGrpcException(
                        "Confirm reservation failed [orderId=%s]: %s"
                                .formatted(orderId, response.getReason()));
            }
            log.info("gRPC confirmReservation succeeded [orderId={}]", orderId);

        } catch (StatusRuntimeException e) {
            log.error("gRPC confirmReservation failed [orderId={}]: {}", orderId, e.getStatus());
            throw new InventoryGrpcException(
                    "Failed to confirm reservation [orderId=%s]".formatted(orderId), e);
        }
    }

    public void cancelReservation(String orderId) {
        log.info("gRPC cancelReservation [orderId={}]", orderId);

        try {
            CancelResponse response = stub.cancelReservation(
                    CancelRequest.newBuilder()
                            .setOrderId(orderId)
                            .build()
            );
            if (!response.getSuccess()) {
                log.warn("gRPC cancelReservation returned failure [orderId={}]: {}",
                        orderId, response.getReason());
            } else {
                log.info("gRPC cancelReservation succeeded [orderId={}]", orderId);
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC cancelReservation failed [orderId={}]: {}", orderId, e.getStatus());
            throw new InventoryGrpcException(
                    "Failed to cancel reservation [orderId=%s]".formatted(orderId), e);
        }
    }
}