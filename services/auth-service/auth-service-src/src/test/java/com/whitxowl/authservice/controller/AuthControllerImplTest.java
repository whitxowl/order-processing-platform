package com.whitxowl.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.service.AuthService;
import com.whitxowl.authservice.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static com.whitxowl.authservice.api.constant.ApiConstant.AUTH_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthControllerImpl.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerImplTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("Регистрация: Успех (201)")
    void register_Success() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@example.com");
        request.setPassword("password123"); // 11 символов, проходит min=8

        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("valid@example.com")
                .roles(Set.of("ROLE_USER"))
                .emailVerified(false)
                .build();

        when(authService.register(any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("valid@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("Регистрация: Ошибка валидации пароля (400)")
    void register_InvalidPassword_Returns400() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@example.com");
        request.setPassword("short"); // Меньше 8 символов

        // When & Then
        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        // Сервис даже не будет вызван, так как Spring Validation сработает раньше
    }

    @Test
    @DisplayName("Логин: Успех (200)")
    void login_Success() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("any_pass");

        TokenPairResponse response = TokenPairResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .expiresIn(3600L)
                .build();

        when(authService.login(any())).thenReturn(response);

        // When & Then
        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("Логин: Невалидный email (400)")
    void login_InvalidEmail_Returns400() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("password");

        // When & Then
        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Подтверждение почты: Успех (204)")
    void verify_Success() throws Exception {
        // When & Then
        mockMvc.perform(post(AUTH_URL + "/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"valid-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Refresh: Ошибка если токен пустой (400)")
    void refresh_EmptyToken_Returns400() throws Exception {
        // Given
        String emptyJson = "{\"refreshToken\": \"\"}";

        // When & Then
        mockMvc.perform(post(AUTH_URL + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyJson))
                .andExpect(status().isBadRequest());
    }
}