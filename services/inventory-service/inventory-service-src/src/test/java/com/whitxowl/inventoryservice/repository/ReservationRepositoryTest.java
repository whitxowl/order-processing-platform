package com.whitxowl.inventoryservice.repository;

import com.whitxowl.inventoryservice.api.dto.enums.ReservationStatus;
import com.whitxowl.inventoryservice.config.TestConfig;
import com.whitxowl.inventoryservice.domain.entity.ReservationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByOrderId_shouldReturnReservation_whenExists() {
        repository.save(ReservationEntity.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(3)
                .status(ReservationStatus.RESERVED)
                .build());

        Optional<ReservationEntity> found = repository.findByOrderId("order-1");

        assertThat(found).isPresent();
        assertThat(found.get().getOrderId()).isEqualTo("order-1");
        assertThat(found.get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void existsByOrderId_shouldReturnTrue_whenExists() {
        repository.save(ReservationEntity.builder()
                .orderId("order-2")
                .productId("product-1")
                .quantity(1)
                .build());

        assertThat(repository.existsByOrderId("order-2")).isTrue();
    }

    @Test
    void existsByOrderId_shouldReturnFalse_whenNotExists() {
        assertThat(repository.existsByOrderId("nonexistent")).isFalse();
    }

    @Test
    void findAllByStatus_shouldReturnOnlyMatchingReservations() {
        repository.save(ReservationEntity.builder()
                .orderId("order-3").productId("p1").quantity(1)
                .status(ReservationStatus.RESERVED).build());
        repository.save(ReservationEntity.builder()
                .orderId("order-4").productId("p2").quantity(2)
                .status(ReservationStatus.CANCELLED).build());
        repository.save(ReservationEntity.builder()
                .orderId("order-5").productId("p3").quantity(3)
                .status(ReservationStatus.RESERVED).build());

        List<ReservationEntity> reserved = repository.findAllByStatus(ReservationStatus.RESERVED);

        assertThat(reserved).hasSize(2)
                .extracting(ReservationEntity::getOrderId)
                .containsExactlyInAnyOrder("order-3", "order-5");
    }
}