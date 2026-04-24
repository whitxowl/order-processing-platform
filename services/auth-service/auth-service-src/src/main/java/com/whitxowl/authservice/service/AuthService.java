package com.whitxowl.authservice.service;

import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    TokenPairResponse login(LoginRequest request);

    TokenPairResponse refresh(RefreshRequest request);

    void verify(VerifyEmailRequest request);

    void logout(RefreshRequest request);
}