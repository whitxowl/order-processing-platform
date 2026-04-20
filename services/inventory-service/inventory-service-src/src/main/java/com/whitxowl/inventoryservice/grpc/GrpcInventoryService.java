package com.whitxowl.inventoryservice.grpc;

import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
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
    public void checkAndReserve(ReserveRequest request,
                                StreamObserver<ReserveResponse> responseObserver) {
        String orderId   = request.getOrderId();
        String productId = request.getProductId();
        int    quantity  = request.getQuantity();

        log.info("gRPC checkAndReserve [orderId={}, productId={}, quantity={}]",
                orderId, productId, quantity);

        try {
            inventoryService.reserve(orderId, productId, quantity);
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(true)
                    .build());

        } catch (DuplicateReservationException e) {
            log.warn("gRPC checkAndReserve: duplicate reservation [orderId={}]", orderId);
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(true)
                    .build());

        } catch (Exception e) {
            log.warn("gRPC checkAndReserve failed [orderId={}]: {}", orderId, e.getMessage());
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(false)
                    .setReason(e.getMessage())
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
