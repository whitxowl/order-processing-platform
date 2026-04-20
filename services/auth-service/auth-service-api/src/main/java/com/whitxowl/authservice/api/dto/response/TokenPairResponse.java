package com.whitxowl.authservice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class TokenPairResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}
