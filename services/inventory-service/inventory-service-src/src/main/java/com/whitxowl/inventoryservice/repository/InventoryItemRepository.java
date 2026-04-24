package com.whitxowl.inventoryservice.repository;

import com.whitxowl.inventoryservice.domain.entity.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, Long> {

    Optional<InventoryItemEntity> findByProductId(String productId);

    boolean existsByProductId(String productId);
}