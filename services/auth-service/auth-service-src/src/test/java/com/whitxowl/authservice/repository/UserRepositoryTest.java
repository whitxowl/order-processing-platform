package com.whitxowl.authservice.repository;

import com.whitxowl.authservice.config.TestConfig;
import com.whitxowl.authservice.domain.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestConfig.class)
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void existsByEmail_shouldReturnTrue_whenUserExists() {
        UserEntity user = UserEntity.builder()
                .email("existing@example.com")
                .passwordHash("hash123")
                .build();
        userRepository.save(user);

        boolean exists = userRepository.existsByEmail("existing@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_shouldReturnFalse_whenUserNotExists() {
        boolean exists = userRepository.existsByEmail("notfound@example.com");
        assertThat(exists).isFalse();
    }

    @Test
    void findByEmail_shouldReturnUser_whenExists() {
        UserEntity saved = userRepository.save(UserEntity.builder()
                .email("find@example.com")
                .passwordHash("hash456")
                .emailVerified(true)
                .enabled(true)
                .build());

        Optional<UserEntity> found = userRepository.findByEmail("find@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("find@example.com");
        assertThat(found.get().getId()).isNotNull();
    }
}