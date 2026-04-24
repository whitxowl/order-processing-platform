package com.whitxowl.inventoryservice.repository;

import com.whitxowl.inventoryservice.config.TestConfig;
import com.whitxowl.inventoryservice.domain.entity.InventoryItemEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class InventoryItemRepositoryTest {

    @Autowired
    private InventoryItemRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByProductId_shouldReturnItem_whenExists() {
        InventoryItemEntity item = repository.save(InventoryItemEntity.builder()
                .productId("product-1")
                .quantity(10)
                .build());

        Optional<InventoryItemEntity> found = repository.findByProductId("product-1");

        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo("product-1");
        assertThat(found.get().getQuantity()).isEqualTo(10);
    }

    @Test
    void findByProductId_shouldReturnEmpty_whenNotExists() {
        Optional<InventoryItemEntity> found = repository.findByProductId("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByProductId_shouldReturnTrue_whenExists() {
        repository.save(InventoryItemEntity.builder()
                .productId("product-2")
                .quantity(5)
                .build());

        assertThat(repository.existsByProductId("product-2")).isTrue();
    }

    @Test
    void existsByProductId_shouldReturnFalse_whenNotExists() {
        assertThat(repository.existsByProductId("nonexistent")).isFalse();
    }

    @Test
    void save_shouldPersistVersionField() {
        InventoryItemEntity item = repository.saveAndFlush(InventoryItemEntity.builder()
                .productId("product-3")
                .quantity(20)
                .build());

        assertThat(item.getVersion()).isZero();

        item.setQuantity(25);

        repository.saveAndFlush(item);
        entityManager.clear();

        InventoryItemEntity reloaded = repository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }
}