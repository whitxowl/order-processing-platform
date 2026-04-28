package com.whitxowl.notificationservice.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_email_cache")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEmailCacheDocument {

    @Id
    private String userId;

    private String email;

    @LastModifiedDate
    private Instant cachedAt;
}