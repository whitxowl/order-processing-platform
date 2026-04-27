package com.whitxowl.orderservice.grpc;

import com.whitxowl.inventoryservice.grpc.CancelRequest;
import com.whitxowl.inventoryservice.grpc.CancelResponse;
import com.whitxowl.inventoryservice.grpc.CheckStockRequest;
import com.whitxowl.inventoryservice.grpc.CheckStockResponse;
import com.whitxowl.inventoryservice.grpc.ConfirmRequest;
import com.whitxowl.inventoryservice.grpc.ConfirmResponse;
import com.whitxowl.inventoryservice.grpc.InventoryServiceGrpc;
import com.whitxowl.orderservice.exception.InventoryGrpcException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcInventoryClientTest {

    @Mock
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    private GrpcInventoryClient client;

    @BeforeEach
    void setUp() {
        client = new GrpcInventoryClient();
        ReflectionTestUtils.setField(client, "stub", stub);
    }

    // ── checkStock ────────────────────────────────────────────────────────────

    @Test
    void checkStock_shouldReturnTrue_whenStockAvailable() {
        when(stub.checkStock(any(CheckStockRequest.class)))
                .thenReturn(CheckStockResponse.newBuilder()
                        .setAvailable(true).setInStock(5).build());

        boolean result = client.checkStock("product-1", 3);

        assertThat(result).isTrue();
        verify(stub).checkStock(CheckStockRequest.newBuilder()
                .setProductId("product-1").setQuantity(3).build());
    }

    @Test
    void checkStock_shouldReturnFalse_whenStockInsufficient() {
        when(stub.checkStock(any(CheckStockRequest.class)))
                .thenReturn(CheckStockResponse.newBuilder()
                        .setAvailable(false).setInStock(1).build());

        boolean result = client.checkStock("product-1", 10);

        assertThat(result).isFalse();
    }

    @Test
    void checkStock_shouldThrowInventoryGrpcException_whenStatusRuntimeException() {
        when(stub.checkStock(any(CheckStockRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.checkStock("product-1", 3))
                .isInstanceOf(InventoryGrpcException.class)
                .hasMessageContaining("product-1");
    }

    // ── confirmReservation ────────────────────────────────────────────────────

    @Test
    void confirmReservation_shouldSucceed_whenResponseIsSuccess() {
        when(stub.confirmReservation(any(ConfirmRequest.class)))
                .thenReturn(ConfirmResponse.newBuilder()
                        .setSuccess(true).build());

        client.confirmReservation("order-1");

        verify(stub).confirmReservation(ConfirmRequest.newBuilder()
                .setOrderId("order-1").build());
    }

    @Test
    void confirmReservation_shouldThrow_whenResponseIsFailure() {
        when(stub.confirmReservation(any(ConfirmRequest.class)))
                .thenReturn(ConfirmResponse.newBuilder()
                        .setSuccess(false).setReason("reservation not found").build());

        assertThatThrownBy(() -> client.confirmReservation("order-1"))
                .isInstanceOf(InventoryGrpcException.class)
                .hasMessageContaining("order-1");
    }

    @Test
    void confirmReservation_shouldThrow_whenStatusRuntimeException() {
        when(stub.confirmReservation(any(ConfirmRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        assertThatThrownBy(() -> client.confirmReservation("order-1"))
                .isInstanceOf(InventoryGrpcException.class);
    }

    // ── cancelReservation ─────────────────────────────────────────────────────

    @Test
    void cancelReservation_shouldSucceed_whenResponseIsSuccess() {
        when(stub.cancelReservation(any(CancelRequest.class)))
                .thenReturn(CancelResponse.newBuilder()
                        .setSuccess(true).build());

        client.cancelReservation("order-1");

        verify(stub).cancelReservation(CancelRequest.newBuilder()
                .setOrderId("order-1").build());
    }

    @Test
    void cancelReservation_shouldNotThrow_whenResponseIsFailure() {
        when(stub.cancelReservation(any(CancelRequest.class)))
                .thenReturn(CancelResponse.newBuilder()
                        .setSuccess(false).setReason("reservation not found").build());

        client.cancelReservation("order-1");
    }

    @Test
    void cancelReservation_shouldThrow_whenStatusRuntimeException() {
        when(stub.cancelReservation(any(CancelRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> client.cancelReservation("order-1"))
                .isInstanceOf(InventoryGrpcException.class);
    }
}