package com.whitxowl.authservice.repository;

import com.whitxowl.authservice.config.TestConfig;
import com.whitxowl.authservice.domain.entity.RefreshTokenEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestConfig.class)
@DataJpaTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void findByTokenHash_shouldReturnToken_whenExists() {
        UserEntity user = userRepository.save(UserEntity.builder()
                .email("user@example.com")
                .passwordHash("hash")
                .build());

        String tokenHash = "abc123hash";
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        refreshTokenRepository.save(token);

        Optional<RefreshTokenEntity> found = refreshTokenRepository.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(tokenHash);
    }

    @Test
    void findByTokenHash_shouldReturnEmpty_whenNotExists() {
        Optional<RefreshTokenEntity> found = refreshTokenRepository.findByTokenHash("nonexistent");
        assertThat(found).isEmpty();
    }
}