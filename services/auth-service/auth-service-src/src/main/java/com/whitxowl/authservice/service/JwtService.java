package com.whitxowl.authservice.service;

import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface JwtService {

    String generateAccessToken(UUID userId, String email, List<String> roles);

    String generateRefreshToken();

    Claims parseAccessToken(String token);

    UUID getUserId(Claims claims);

    String getEmail(Claims claims);

    List<String> getRoles(Claims claims);

    Duration getAccessTtl();

    Duration getRefreshTtl();
}