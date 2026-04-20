package com.whitxowl.inventoryservice.grpc;

import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.exception.InsufficientStockException;
import com.whitxowl.inventoryservice.exception.ReservationNotFoundException;
import com.whitxowl.inventoryservice.service.InventoryService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcInventoryServiceTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private StreamObserver<ReserveResponse> reserveObserver;

    @Mock
    private StreamObserver<ConfirmResponse> confirmObserver;

    @Mock
    private StreamObserver<CancelResponse> cancelObserver;

    @InjectMocks
    private GrpcInventoryService grpcService;

    // ── checkAndReserve ───────────────────────────────────────────────────────

    @Test
    void checkAndReserve_shouldReturnSuccess_whenReserved() {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setOrderId("o1").setProductId("p1").setQuantity(3).build();

        grpcService.checkAndReserve(request, reserveObserver);

        ArgumentCaptor<ReserveResponse> captor = ArgumentCaptor.forClass(ReserveResponse.class);
        verify(reserveObserver).onNext(captor.capture());
        verify(reserveObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isTrue();
    }

    @Test
    void checkAndReserve_shouldReturnSuccess_whenDuplicateReservation() {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setOrderId("o1").setProductId("p1").setQuantity(3).build();
        doThrow(new DuplicateReservationException("o1"))
                .when(inventoryService).reserve("o1", "p1", 3);

        grpcService.checkAndReserve(request, reserveObserver);

        ArgumentCaptor<ReserveResponse> captor = ArgumentCaptor.forClass(ReserveResponse.class);
        verify(reserveObserver).onNext(captor.capture());
        verify(reserveObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isTrue();
    }

    @Test
    void checkAndReserve_shouldReturnFailure_whenInsufficientStock() {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setOrderId("o1").setProductId("p1").setQuantity(100).build();
        doThrow(new InsufficientStockException("p1", 100, 5))
                .when(inventoryService).reserve("o1", "p1", 100);

        grpcService.checkAndReserve(request, reserveObserver);

        ArgumentCaptor<ReserveResponse> captor = ArgumentCaptor.forClass(ReserveResponse.class);
        verify(reserveObserver).onNext(captor.capture());
        verify(reserveObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getReason()).isNotBlank();
    }

    // ── confirmReservation ────────────────────────────────────────────────────

    @Test
    void confirmReservation_shouldReturnSuccess_whenConfirmed() {
        ConfirmRequest request = ConfirmRequest.newBuilder().setOrderId("o1").build();

        grpcService.confirmReservation(request, confirmObserver);

        ArgumentCaptor<ConfirmResponse> captor = ArgumentCaptor.forClass(ConfirmResponse.class);
        verify(confirmObserver).onNext(captor.capture());
        verify(confirmObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isTrue();
    }

    @Test
    void confirmReservation_shouldReturnFailure_whenNotFound() {
        ConfirmRequest request = ConfirmRequest.newBuilder().setOrderId("missing").build();
        doThrow(new ReservationNotFoundException("missing"))
                .when(inventoryService).confirmReservation("missing");

        grpcService.confirmReservation(request, confirmObserver);

        ArgumentCaptor<ConfirmResponse> captor = ArgumentCaptor.forClass(ConfirmResponse.class);
        verify(confirmObserver).onNext(captor.capture());
        verify(confirmObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getReason()).contains("missing");
    }

    @Test
    void confirmReservation_shouldReturnFailure_whenIllegalState() {
        ConfirmRequest request = ConfirmRequest.newBuilder().setOrderId("o1").build();
        doThrow(new IllegalStateException("Cannot confirm cancelled reservation [orderId=o1]"))
                .when(inventoryService).confirmReservation("o1");

        grpcService.confirmReservation(request, confirmObserver);

        ArgumentCaptor<ConfirmResponse> captor = ArgumentCaptor.forClass(ConfirmResponse.class);
        verify(confirmObserver).onNext(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
    }

    // ── cancelReservation ─────────────────────────────────────────────────────

    @Test
    void cancelReservation_shouldReturnSuccess_whenCancelled() {
        CancelRequest request = CancelRequest.newBuilder().setOrderId("o1").build();

        grpcService.cancelReservation(request, cancelObserver);

        ArgumentCaptor<CancelResponse> captor = ArgumentCaptor.forClass(CancelResponse.class);
        verify(cancelObserver).onNext(captor.capture());
        verify(cancelObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isTrue();
    }

    @Test
    void cancelReservation_shouldReturnFailure_whenNotFound() {
        CancelRequest request = CancelRequest.newBuilder().setOrderId("missing").build();
        doThrow(new ReservationNotFoundException("missing"))
                .when(inventoryService).cancelReservation("missing");

        grpcService.cancelReservation(request, cancelObserver);

        ArgumentCaptor<CancelResponse> captor = ArgumentCaptor.forClass(CancelResponse.class);
        verify(cancelObserver).onNext(captor.capture());
        verify(cancelObserver).onCompleted();
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getReason()).contains("missing");
    }
}