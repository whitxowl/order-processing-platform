package com.whitxowl.authservice.repository;

import com.whitxowl.authservice.config.TestConfig;
import com.whitxowl.authservice.domain.entity.EmailVerificationTokenEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestConfig.class)
@DataJpaTest
class EmailVerificationTokenRepositoryTest {

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void findByToken_shouldReturnToken_whenExists() {
        UserEntity user = userRepository.save(UserEntity.builder()
                .email("verify@example.com")
                .passwordHash("hash")
                .build());

        String tokenValue = "verification-token-123";
        EmailVerificationTokenEntity token = EmailVerificationTokenEntity.builder()
                .user(user)
                .token(tokenValue)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationTokenEntity> found = emailVerificationTokenRepository.findByToken(tokenValue);

        assertThat(found).isPresent();
        assertThat(found.get().getToken()).isEqualTo(tokenValue);
    }
}