package com.whitxowl.authservice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private Set<String> roles;
    private boolean emailVerified;
}
