package com.whitxowl.notificationservice.repository;

import com.whitxowl.notificationservice.config.TestConfig;
import com.whitxowl.notificationservice.domain.document.UserEmailCacheDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(TestConfig.class)
class UserEmailCacheRepositoryTest {

    @Autowired
    private UserEmailCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByUserId_shouldReturnDocument_whenExists() {
        repository.save(UserEmailCacheDocument.builder()
                .userId("user-1")
                .email("user@example.com")
                .build());

        Optional<UserEmailCacheDocument> result = repository.findByUserId("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenNotExists() {
        Optional<UserEmailCacheDocument> result = repository.findByUserId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void save_shouldUpdateEmail_whenUserIdAlreadyCached() {
        repository.save(UserEmailCacheDocument.builder()
                .userId("user-1")
                .email("old@example.com")
                .build());

        repository.save(UserEmailCacheDocument.builder()
                .userId("user-1")
                .email("new@example.com")
                .build());

        Optional<UserEmailCacheDocument> result = repository.findByUserId("user-1");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("new@example.com");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void save_shouldPersistMultipleUsers() {
        repository.save(UserEmailCacheDocument.builder().userId("u1").email("u1@example.com").build());
        repository.save(UserEmailCacheDocument.builder().userId("u2").email("u2@example.com").build());

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByUserId("u2").map(UserEmailCacheDocument::getEmail))
                .contains("u2@example.com");
    }
}