package com.whitxowl.inventoryservice.service.impl;

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
import com.whitxowl.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ReservationRepository   reservationRepository;
    private final InventoryMapper         inventoryMapper;

    // eventProducer удалён: публикация inventory.reserved теперь выполняется
    // топологией OrderReservationTopology через возвращаемое значение mapValues().

    @Override
    @Transactional
    public StockResponse setStock(String productId, int quantity) {
        InventoryItemEntity item = inventoryItemRepository.findByProductId(productId)
                .orElseGet(() -> InventoryItemEntity.builder()
                        .productId(productId)
                        .build());

        if (quantity < item.getReserved()) {
            throw new IllegalArgumentException(
                    "Cannot set quantity=%d below current reserved=%d [productId=%s]"
                            .formatted(quantity, item.getReserved(), productId));
        }

        item.setQuantity(quantity);
        InventoryItemEntity saved = inventoryItemRepository.save(item);

        log.info("Stock updated [productId={}]: quantity={}", productId, quantity);
        return inventoryMapper.toStockResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse getStock(String productId) {
        InventoryItemEntity item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));

        return inventoryMapper.toStockResponse(item);
    }

    @Override
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public ReservationResponse reserve(String orderId, String productId, int quantity) {
        return doReserve(orderId, productId, quantity);
    }

    @Transactional
    protected ReservationResponse doReserve(String orderId, String productId, int quantity) {
        if (reservationRepository.existsByOrderId(orderId)) {
            log.warn("Duplicate order.created event ignored [orderId={}]", orderId);
            throw new DuplicateReservationException(orderId);
        }

        InventoryItemEntity item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));

        if (item.getAvailable() < quantity) {
            log.warn("Insufficient stock [productId={}]: requested={}, available={}",
                    productId, quantity, item.getAvailable());
            throw new InsufficientStockException(productId, quantity, item.getAvailable());
        }

        item.setReserved(item.getReserved() + quantity);
        inventoryItemRepository.save(item);

        ReservationEntity reservation = ReservationEntity.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(ReservationStatus.RESERVED)
                .build();

        ReservationEntity saved = reservationRepository.save(reservation);

        log.info("Stock reserved [orderId={}, productId={}, quantity={}]",
                orderId, productId, quantity);

        return inventoryMapper.toReservationResponse(saved);
    }

    @Override
    @Transactional
    public ReservationResponse confirmReservation(String orderId) {
        ReservationEntity reservation = getReservationByOrderId(orderId);

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            log.warn("Reservation already confirmed, skipping [orderId={}]", orderId);
            return inventoryMapper.toReservationResponse(reservation);
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot confirm cancelled reservation [orderId=%s]".formatted(orderId));
        }

        InventoryItemEntity item = inventoryItemRepository.findByProductId(reservation.getProductId())
                .orElseThrow(() -> new InventoryItemNotFoundException(reservation.getProductId()));

        item.setReserved(item.getReserved() - reservation.getQuantity());
        item.setQuantity(item.getQuantity() - reservation.getQuantity());
        inventoryItemRepository.save(item);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        ReservationEntity saved = reservationRepository.save(reservation);

        log.info("Reservation confirmed [orderId={}]", orderId);
        return inventoryMapper.toReservationResponse(saved);
    }

    @Override
    @Transactional
    public ReservationResponse cancelReservation(String orderId) {
        ReservationEntity reservation = getReservationByOrderId(orderId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.warn("Reservation already cancelled, skipping [orderId={}]", orderId);
            return inventoryMapper.toReservationResponse(reservation);
        }

        InventoryItemEntity item = inventoryItemRepository.findByProductId(reservation.getProductId())
                .orElseThrow(() -> new InventoryItemNotFoundException(reservation.getProductId()));

        item.setReserved(item.getReserved() - reservation.getQuantity());
        inventoryItemRepository.save(item);

        reservation.setStatus(ReservationStatus.CANCELLED);
        ReservationEntity saved = reservationRepository.save(reservation);

        log.info("Reservation cancelled (saga compensation) [orderId={}]", orderId);
        return inventoryMapper.toReservationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getActiveReservations() {
        return reservationRepository.findAllByStatus(ReservationStatus.RESERVED)
                .stream()
                .map(inventoryMapper::toReservationResponse)
                .toList();
    }

    private ReservationEntity getReservationByOrderId(String orderId) {
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ReservationNotFoundException(orderId));
    }
}