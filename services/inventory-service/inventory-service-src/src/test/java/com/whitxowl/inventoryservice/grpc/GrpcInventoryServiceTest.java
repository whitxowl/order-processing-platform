package com.whitxowl.inventoryservice.grpc;

import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.exception.InventoryItemNotFoundException;
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

    @Mock private InventoryService inventoryService;
    @Mock private StreamObserver<CheckStockResponse> checkObserver;
    @Mock private StreamObserver<ConfirmResponse>    confirmObserver;
    @Mock private StreamObserver<CancelResponse>     cancelObserver;

    @InjectMocks
    private GrpcInventoryService grpcService;

    // ── checkStock ────────────────────────────────────────────────────────────

    @Test
    void checkStock_shouldReturnAvailableTrue_whenSufficientStock() {
        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setProductId("p1").setQuantity(3).build();
        when(inventoryService.getStock("p1")).thenReturn(
                StockResponse.builder().productId("p1").quantity(10).reserved(2).available(8).build());

        grpcService.checkStock(request, checkObserver);

        ArgumentCaptor<CheckStockResponse> captor = ArgumentCaptor.forClass(CheckStockResponse.class);
        verify(checkObserver).onNext(captor.capture());
        verify(checkObserver).onCompleted();
        assertThat(captor.getValue().getAvailable()).isTrue();
        assertThat(captor.getValue().getInStock()).isEqualTo(8);
    }

    @Test
    void checkStock_shouldReturnAvailableFalse_whenInsufficientStock() {
        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setProductId("p1").setQuantity(10).build();
        when(inventoryService.getStock("p1")).thenReturn(
                StockResponse.builder().productId("p1").quantity(5).reserved(3).available(2).build());

        grpcService.checkStock(request, checkObserver);

        ArgumentCaptor<CheckStockResponse> captor = ArgumentCaptor.forClass(CheckStockResponse.class);
        verify(checkObserver).onNext(captor.capture());
        verify(checkObserver).onCompleted();
        assertThat(captor.getValue().getAvailable()).isFalse();
        assertThat(captor.getValue().getInStock()).isEqualTo(2);
    }

    @Test
    void checkStock_shouldReturnUnavailable_whenItemNotFound() {
        CheckStockRequest request = CheckStockRequest.newBuilder()
                .setProductId("missing").setQuantity(1).build();
        when(inventoryService.getStock("missing"))
                .thenThrow(new InventoryItemNotFoundException("missing"));

        grpcService.checkStock(request, checkObserver);

        ArgumentCaptor<CheckStockResponse> captor = ArgumentCaptor.forClass(CheckStockResponse.class);
        verify(checkObserver).onNext(captor.capture());
        verify(checkObserver).onCompleted();
        assertThat(captor.getValue().getAvailable()).isFalse();
        assertThat(captor.getValue().getInStock()).isZero();
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
        verify(confirmObserver).onCompleted();
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