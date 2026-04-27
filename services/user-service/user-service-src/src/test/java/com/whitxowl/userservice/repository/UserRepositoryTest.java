package com.whitxowl.userservice.repository;

import com.whitxowl.userservice.config.TestConfig;
import com.whitxowl.userservice.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    void save_shouldPersistUser() {
        UserEntity user = UserEntity.builder()
                .id(userId)
                .email("test@example.com")
                .build();

        UserEntity saved = repository.save(user);

        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void existsById_shouldReturnTrue_whenExists() {
        repository.save(UserEntity.builder()
                .id(userId)
                .email("test@example.com")
                .build());

        assertThat(repository.existsById(userId)).isTrue();
    }

    @Test
    void existsById_shouldReturnFalse_whenNotExists() {
        assertThat(repository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void findByEmail_shouldReturnUser_whenExists() {
        repository.save(UserEntity.builder()
                .id(userId)
                .email("find@example.com")
                .build());

        Optional<UserEntity> found = repository.findByEmail("find@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(userId);
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenNotExists() {
        Optional<UserEntity> found = repository.findByEmail("nobody@example.com");
        assertThat(found).isEmpty();
    }
}