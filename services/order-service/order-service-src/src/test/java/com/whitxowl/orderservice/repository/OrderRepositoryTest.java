package com.whitxowl.orderservice.repository;

import com.whitxowl.orderservice.api.dto.enums.OrderStatus;
import com.whitxowl.orderservice.config.TestConfig;
import com.whitxowl.orderservice.domain.entity.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestConfig.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findAllByUserId_shouldReturnOnlyUserOrders() {
        repository.save(order("user-1", "product-1", OrderStatus.NEW));
        repository.save(order("user-1", "product-2", OrderStatus.RESERVED));
        repository.save(order("user-2", "product-3", OrderStatus.NEW));

        Page<OrderEntity> result = repository.findAllByUserId("user-1", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(o -> o.getUserId().equals("user-1"));
    }

    @Test
    void findAllByUserId_shouldReturnEmpty_whenUserHasNoOrders() {
        repository.save(order("user-1", "product-1", OrderStatus.NEW));

        Page<OrderEntity> result = repository.findAllByUserId("user-2", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllOrders_withPagination() {
        repository.save(order("user-1", "product-1", OrderStatus.NEW));
        repository.save(order("user-2", "product-2", OrderStatus.RESERVED));
        repository.save(order("user-3", "product-3", OrderStatus.CANCELLED));

        Page<OrderEntity> page0 = repository.findAll(PageRequest.of(0, 2));
        Page<OrderEntity> page1 = repository.findAll(PageRequest.of(1, 2));

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page0.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findById_shouldReturnOrder_whenExists() {
        OrderEntity saved = repository.save(order("user-1", "product-1", OrderStatus.NEW));

        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        assertThat(repository.findById(java.util.UUID.randomUUID())).isEmpty();
    }

    private OrderEntity order(String userId, String productId, OrderStatus status) {
        return OrderEntity.builder()
                .userId(userId)
                .productId(productId)
                .quantity(1)
                .status(status)
                .build();
    }
}