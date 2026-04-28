package com.whitxowl.notificationservice.repository;

import com.whitxowl.notificationservice.domain.NotificationType;
import com.whitxowl.notificationservice.domain.document.NotificationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    boolean existsByReferenceIdAndType(String referenceId, NotificationType type);

    Optional<NotificationDocument> findByReferenceIdAndType(String referenceId, NotificationType type);

    List<NotificationDocument> findAllByRecipientEmail(String recipientEmail);
}