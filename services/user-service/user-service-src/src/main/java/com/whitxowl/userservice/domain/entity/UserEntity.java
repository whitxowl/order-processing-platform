package com.whitxowl.userservice.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private Set<RoleEntity> roles = new HashSet<>();

    public void addRole(String role) {
        RoleEntity roleEntity = new RoleEntity();
        roleEntity.setUser(this);
        roleEntity.setRole(role);
        this.roles.add(roleEntity);
    }

    public void removeRole(String role) {
        this.roles.removeIf(r -> r.getRole().equals(role));
    }

    public Set<String> getRoleNames() {
        return roles.stream()
                .map(RoleEntity::getRole)
                .collect(Collectors.toSet());
    }
}