package com.whitxowl.notificationservice.repository;

import com.whitxowl.notificationservice.config.TestConfig;
import com.whitxowl.notificationservice.domain.NotificationStatus;
import com.whitxowl.notificationservice.domain.NotificationType;
import com.whitxowl.notificationservice.domain.document.NotificationDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(TestConfig.class)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private NotificationDocument buildDoc(String referenceId, NotificationType type,
                                          String email, NotificationStatus status) {
        return NotificationDocument.builder()
                .referenceId(referenceId)
                .type(type)
                .recipientEmail(email)
                .status(status)
                .sentAt(Instant.now())
                .build();
    }

    // ── existsByReferenceIdAndType ─────────────────────────────────────────────

    @Test
    void existsByReferenceIdAndType_shouldReturnTrue_whenDocumentExists() {
        repository.save(buildDoc("ref-1", NotificationType.ORDER_CREATED, "a@b.com", NotificationStatus.SENT));

        assertThat(repository.existsByReferenceIdAndType("ref-1", NotificationType.ORDER_CREATED)).isTrue();
    }

    @Test
    void existsByReferenceIdAndType_shouldReturnFalse_whenTypeDiffers() {
        repository.save(buildDoc("ref-1", NotificationType.ORDER_CREATED, "a@b.com", NotificationStatus.SENT));

        assertThat(repository.existsByReferenceIdAndType("ref-1", NotificationType.ORDER_RESERVED)).isFalse();
    }

    @Test
    void existsByReferenceIdAndType_shouldReturnFalse_whenReferenceIdDiffers() {
        repository.save(buildDoc("ref-1", NotificationType.ORDER_CREATED, "a@b.com", NotificationStatus.SENT));

        assertThat(repository.existsByReferenceIdAndType("ref-999", NotificationType.ORDER_CREATED)).isFalse();
    }

    // ── findByReferenceIdAndType ───────────────────────────────────────────────

    @Test
    void findByReferenceIdAndType_shouldReturnDocument_whenExists() {
        repository.save(buildDoc("ord-1", NotificationType.ORDER_CREATED, "buyer@example.com", NotificationStatus.SENT));

        Optional<NotificationDocument> result =
                repository.findByReferenceIdAndType("ord-1", NotificationType.ORDER_CREATED);

        assertThat(result).isPresent();
        assertThat(result.get().getRecipientEmail()).isEqualTo("buyer@example.com");
        assertThat(result.get().getType()).isEqualTo(NotificationType.ORDER_CREATED);
    }

    @Test
    void findByReferenceIdAndType_shouldReturnEmpty_whenNotExists() {
        Optional<NotificationDocument> result =
                repository.findByReferenceIdAndType("nonexistent", NotificationType.ROLE_CHANGED);

        assertThat(result).isEmpty();
    }

    // ── findAllByRecipientEmail ────────────────────────────────────────────────

    @Test
    void findAllByRecipientEmail_shouldReturnAllNotificationsForEmail() {
        repository.save(buildDoc("ord-1", NotificationType.ORDER_CREATED, "user@example.com", NotificationStatus.SENT));
        repository.save(buildDoc("ord-1", NotificationType.ORDER_RESERVED, "user@example.com", NotificationStatus.SENT));
        repository.save(buildDoc("ord-2", NotificationType.ORDER_CREATED, "other@example.com", NotificationStatus.SENT));

        List<NotificationDocument> results = repository.findAllByRecipientEmail("user@example.com");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(NotificationDocument::getRecipientEmail)
                .containsOnly("user@example.com");
    }

    @Test
    void findAllByRecipientEmail_shouldReturnEmptyList_whenNoNotificationsForEmail() {
        List<NotificationDocument> results = repository.findAllByRecipientEmail("nobody@example.com");

        assertThat(results).isEmpty();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_shouldPersistAllFields() {
        NotificationDocument doc = NotificationDocument.builder()
                .referenceId("ref-save")
                .type(NotificationType.USER_CREATED)
                .recipientEmail("new@example.com")
                .status(NotificationStatus.FAILED)
                .failureReason("SMTP timeout")
                .sentAt(Instant.now())
                .build();

        NotificationDocument saved = repository.save(doc);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReferenceId()).isEqualTo("ref-save");
        assertThat(saved.getType()).isEqualTo(NotificationType.USER_CREATED);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("SMTP timeout");
    }
}