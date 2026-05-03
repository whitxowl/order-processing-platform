package com.whitxowl.authservice.controller;

import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.config.TestConfig;
import com.whitxowl.authservice.service.AuthService;
import com.whitxowl.authservice.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Set;
import java.util.UUID;

import static com.whitxowl.authservice.api.constant.ApiConstant.AUTH_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
class AuthControllerImplTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Регистрация: Успех (201)")
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@example.com");
        request.setPassword("password123");

        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("valid@example.com")
                .roles(Set.of("ROLE_USER"))
                .emailVerified(false)
                .build();

        when(authService.register(any())).thenReturn(response);

        webTestClient.post().uri(AUTH_URL + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.email").isEqualTo("valid@example.com")
                .jsonPath("$.emailVerified").isEqualTo(false);
    }

    @Test
    @DisplayName("Регистрация: Ошибка валидации пароля (400)")
    void register_InvalidPassword_Returns400() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("valid@example.com");
        request.setPassword("short"); // меньше 8 символов

        webTestClient.post().uri(AUTH_URL + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Регистрация: Невалидный email (400)")
    void register_InvalidEmail_Returns400() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("password123");

        webTestClient.post().uri(AUTH_URL + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Логин: Успех (200)")
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("any_pass");

        TokenPairResponse response = TokenPairResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .accessExpiresIn(900L)
                .refreshExpiresIn(604800L)
                .build();

        when(authService.login(any())).thenReturn(response);

        webTestClient.post().uri(AUTH_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("access")
                .jsonPath("$.refreshToken").isEqualTo("refresh")
                .jsonPath("$.accessExpiresIn").isEqualTo(900)
                .jsonPath("$.refreshExpiresIn").isEqualTo(604800);
    }

    @Test
    @DisplayName("Логин: Невалидный email (400)")
    void login_InvalidEmail_Returns400() {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("password");

        webTestClient.post().uri(AUTH_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── verify ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Подтверждение почты: Успех (204)")
    void verify_Success() {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("valid-token");

        webTestClient.post().uri(AUTH_URL + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("Подтверждение почты: Пустой токен (400)")
    void verify_EmptyToken_Returns400() {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("");

        webTestClient.post().uri(AUTH_URL + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── refresh ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Refresh: Ошибка если токен пустой (400)")
    void refresh_EmptyToken_Returns400() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("");

        webTestClient.post().uri(AUTH_URL + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }
}