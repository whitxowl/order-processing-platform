package com.whitxowl.authservice.controller;

import com.whitxowl.authservice.api.controller.AuthController;
import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private final AuthService authService;

    @Override
    public ResponseEntity<UserResponse> register(RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<TokenPairResponse> login(LoginRequest request) {
        TokenPairResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<TokenPairResponse> refresh(RefreshRequest request) {
        TokenPairResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> verify(VerifyEmailRequest request) {
        authService.verify(request);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}