package com.whitxowl.inventoryservice.service;

import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.domain.entity.InventoryItemEntity;
import com.whitxowl.inventoryservice.domain.entity.ReservationEntity;
import com.whitxowl.inventoryservice.exception.DuplicateReservationException;
import com.whitxowl.inventoryservice.exception.InsufficientStockException;
import com.whitxowl.inventoryservice.exception.InventoryItemNotFoundException;
import com.whitxowl.inventoryservice.exception.ReservationNotFoundException;
import com.whitxowl.inventoryservice.mapper.InventoryMapper;
import com.whitxowl.inventoryservice.repository.InventoryItemRepository;
import com.whitxowl.inventoryservice.repository.ReservationRepository;
import com.whitxowl.inventoryservice.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryServiceImpl service;

    // ── setStock ─────────────────────────────────────────────────────────────

    @Test
    void setStock_shouldCreateNewItem_whenProductNotExists() {
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryMapper.toStockResponse(any())).thenReturn(
                StockResponse.builder().productId("p1").quantity(10).build());

        StockResponse response = service.setStock("p1", 10);

        assertThat(response.getQuantity()).isEqualTo(10);
        verify(inventoryItemRepository).save(any());
    }

    @Test
    void setStock_shouldUpdateExistingItem_whenProductExists() {
        InventoryItemEntity existing = InventoryItemEntity.builder()
                .productId("p1").quantity(5).build();
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryMapper.toStockResponse(any())).thenReturn(
                StockResponse.builder().productId("p1").quantity(20).build());

        service.setStock("p1", 20);

        assertThat(existing.getQuantity()).isEqualTo(20);
        verify(inventoryItemRepository).save(existing);
    }

    @Test
    void setStock_shouldThrow_whenQuantityBelowReserved() {
        InventoryItemEntity existing = InventoryItemEntity.builder()
                .productId("p1").quantity(10).reserved(5).build();
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.setStock("p1", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    // ── getStock ─────────────────────────────────────────────────────────────

    @Test
    void getStock_shouldThrow_whenProductNotFound() {
        when(inventoryItemRepository.findByProductId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStock("missing"))
                .isInstanceOf(InventoryItemNotFoundException.class);
    }

    // ── reserve (via doReserve) ───────────────────────────────────────────────

    @Test
    void doReserve_shouldReserveStock_whenSufficientQuantity() {
        InventoryItemEntity item = InventoryItemEntity.builder()
                .productId("p1").quantity(10).reserved(0).build();
        when(reservationRepository.existsByOrderId("o1")).thenReturn(false);
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryMapper.toReservationResponse(any())).thenReturn(
                ReservationResponse.builder().orderId("o1").status(ReservationStatus.RESERVED).build());

        ReservationResponse response = service.reserve("o1", "p1", 3);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(item.getReserved()).isEqualTo(3);
    }

    @Test
    void doReserve_shouldThrow_whenInsufficientStock() {
        InventoryItemEntity item = InventoryItemEntity.builder()
                .productId("p1").quantity(2).reserved(0).build();
        when(reservationRepository.existsByOrderId("o1")).thenReturn(false);
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.reserve("o1", "p1", 5))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void doReserve_shouldThrowDuplicate_whenOrderIdExists() {
        when(reservationRepository.existsByOrderId("o1")).thenReturn(true);

        assertThatThrownBy(() -> service.reserve("o1", "p1", 3))
                .isInstanceOf(DuplicateReservationException.class);

        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void doReserve_shouldThrow_whenItemNotFound() {
        when(reservationRepository.existsByOrderId("o1")).thenReturn(false);
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserve("o1", "p1", 3))
                .isInstanceOf(InventoryItemNotFoundException.class);
    }

    // ── confirmReservation ────────────────────────────────────────────────────

    @Test
    void confirmReservation_shouldConfirm_whenStatusIsReserved() {
        ReservationEntity reservation = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(3)
                .status(ReservationStatus.RESERVED).build();
        InventoryItemEntity item = InventoryItemEntity.builder()
                .productId("p1").quantity(10).reserved(3).build();

        when(reservationRepository.findByOrderId("o1")).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryMapper.toReservationResponse(any())).thenReturn(
                ReservationResponse.builder().status(ReservationStatus.CONFIRMED).build());

        service.confirmReservation("o1");

        assertThat(item.getReserved()).isZero();
        assertThat(item.getQuantity()).isEqualTo(7);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirmReservation_shouldBeIdempotent_whenAlreadyConfirmed() {
        ReservationEntity reservation = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(3)
                .status(ReservationStatus.CONFIRMED).build();
        when(reservationRepository.findByOrderId("o1")).thenReturn(Optional.of(reservation));
        when(inventoryMapper.toReservationResponse(any())).thenReturn(
                ReservationResponse.builder().status(ReservationStatus.CONFIRMED).build());

        service.confirmReservation("o1");

        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void confirmReservation_shouldThrow_whenStatusIsCancelled() {
        ReservationEntity reservation = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(3)
                .status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findByOrderId("o1")).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.confirmReservation("o1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── cancelReservation ─────────────────────────────────────────────────────

    @Test
    void cancelReservation_shouldCancel_whenStatusIsReserved() {
        ReservationEntity reservation = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(3)
                .status(ReservationStatus.RESERVED).build();
        InventoryItemEntity item = InventoryItemEntity.builder()
                .productId("p1").quantity(10).reserved(3).build();

        when(reservationRepository.findByOrderId("o1")).thenReturn(Optional.of(reservation));
        when(inventoryItemRepository.findByProductId("p1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventoryMapper.toReservationResponse(any())).thenReturn(
                ReservationResponse.builder().status(ReservationStatus.CANCELLED).build());

        service.cancelReservation("o1");

        assertThat(item.getReserved()).isZero();
        assertThat(item.getQuantity()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_shouldBeIdempotent_whenAlreadyCancelled() {
        ReservationEntity reservation = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(3)
                .status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findByOrderId("o1")).thenReturn(Optional.of(reservation));
        when(inventoryMapper.toReservationResponse(any())).thenReturn(
                ReservationResponse.builder().status(ReservationStatus.CANCELLED).build());

        service.cancelReservation("o1");

        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void cancelReservation_shouldThrow_whenNotFound() {
        when(reservationRepository.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelReservation("missing"))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    // ── getActiveReservations ─────────────────────────────────────────────────

    @Test
    void getActiveReservations_shouldReturnOnlyReserved() {
        ReservationEntity r1 = ReservationEntity.builder()
                .orderId("o1").productId("p1").quantity(1)
                .status(ReservationStatus.RESERVED).build();
        when(reservationRepository.findAllByStatus(ReservationStatus.RESERVED))
                .thenReturn(List.of(r1));
        when(inventoryMapper.toReservationResponse(r1)).thenReturn(
                ReservationResponse.builder().orderId("o1").status(ReservationStatus.RESERVED).build());

        List<ReservationResponse> result = service.getActiveReservations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderId()).isEqualTo("o1");
    }
}