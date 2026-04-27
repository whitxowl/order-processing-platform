package com.whitxowl.userservice.repository;

import com.whitxowl.userservice.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsById(UUID id);

    Optional<UserEntity> findByEmail(String email);
}