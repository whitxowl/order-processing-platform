package com.whitxowl.inventoryservice.repository;

import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import com.whitxowl.inventoryservice.domain.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    List<ReservationEntity> findAllByStatus(ReservationStatus status);
}