package com.whitxowl.notificationservice.repository;

import com.whitxowl.notificationservice.domain.document.UserEmailCacheDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserEmailCacheRepository extends MongoRepository<UserEmailCacheDocument, String> {

    Optional<UserEmailCacheDocument> findByUserId(String userId);
}