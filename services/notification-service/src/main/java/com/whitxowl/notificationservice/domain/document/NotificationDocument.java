package com.whitxowl.notificationservice.domain.document;

import com.whitxowl.notificationservice.domain.NotificationStatus;
import com.whitxowl.notificationservice.domain.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notifications")
@CompoundIndex(name = "idx_ref_type", def = "{'referenceId': 1, 'type': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDocument {

    @Id
    private String id;

    @Indexed
    private String referenceId;

    private NotificationType type;

    private NotificationStatus status;

    @Indexed
    private String recipientEmail;

    private String failureReason;

    @CreatedDate
    private Instant sentAt;
}