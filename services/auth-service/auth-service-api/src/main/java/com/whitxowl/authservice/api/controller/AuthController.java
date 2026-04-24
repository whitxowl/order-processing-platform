package com.whitxowl.authservice.api.controller;

import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.whitxowl.authservice.api.constant.ApiConstant.AUTH_URL;

@Tag(name = "Auth Controller", description = "Авторизация и аутентификация")
@RequestMapping(AUTH_URL)
public interface AuthController {

    @Operation(summary = "Register", description = "Регистрация нового пользователя по email и паролю")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "409", description = "Пользователь с таким email уже существует"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request);

    @Operation(summary = "Login", description = "Аутентификация по email и паролю")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная аутентификация"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "401", description = "Неверный email или пароль"),
            @ApiResponse(responseCode = "403", description = "Email не подтверждён или аккаунт заблокирован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/login")
    ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "Refresh tokens", description = "Обновление пары токенов по refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токены успешно обновлены"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "401", description = "Невалидный или просроченный refresh token"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/refresh")
    ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request);

    @Operation(summary = "Verify email", description = "Подтверждение email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email успешно подтверждён"),
            @ApiResponse(responseCode = "400", description = "Невалидный или просроченный токен"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/verify")
    ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request);

    @Operation(summary = "Logout", description = "Отзыв refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Успешный выход"),
            @ApiResponse(responseCode = "401", description = "Пользователь не аутентифицирован"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request);
}