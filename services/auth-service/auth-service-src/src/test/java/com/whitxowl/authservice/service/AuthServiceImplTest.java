package com.whitxowl.authservice.service;

import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.domain.entity.EmailVerificationTokenEntity;
import com.whitxowl.authservice.domain.entity.RefreshTokenEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import com.whitxowl.authservice.exception.EmailNotVerifiedException;
import com.whitxowl.authservice.exception.UserAlreadyExistsException;
import com.whitxowl.authservice.kafka.producer.UserCreatedEventProducer;
import com.whitxowl.authservice.mapper.UserMapper;
import com.whitxowl.authservice.repository.EmailVerificationTokenRepository;
import com.whitxowl.authservice.repository.RefreshTokenRepository;
import com.whitxowl.authservice.repository.UserRepository;
import com.whitxowl.authservice.security.CustomUserDetails;
import com.whitxowl.authservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserCreatedEventProducer eventProducer;

    @InjectMocks private AuthServiceImpl authService;

    @Test
    void register_shouldSaveUserAndProduceEvent_whenEmailNotExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("StrongPass123!");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPasswordHash");
        when(userMapper.toUserResponse(any(UserEntity.class)))
                .thenReturn(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("newuser@example.com")
                        .roles(Set.of("ROLE_USER"))
                        .emailVerified(false)
                        .build());

        UserResponse response = authService.register(request);

        assertThat(response).isNotNull();
        verify(userRepository).save(any(UserEntity.class));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationTokenEntity.class));
        verify(eventProducer).produce(any(UserEntity.class));
    }

    @Test
    void register_shouldThrowUserAlreadyExistsException_whenEmailTaken() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password");

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void login_shouldReturnTokenPair_whenCredentialsCorrectAndEmailVerified() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        UserEntity userEntity = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .emailVerified(true)
                .enabled(true)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(userEntity);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("valid.access.token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token-uuid");
        when(jwtService.getAccessTtl()).thenReturn(java.time.Duration.ofMinutes(15));  // ← добавить
        when(jwtService.getRefreshTtl()).thenReturn(java.time.Duration.ofDays(7));

        TokenPairResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("valid.access.token");
        assertThat(response.getAccessExpiresIn()).isEqualTo(900);      // ← добавить
        assertThat(response.getRefreshExpiresIn()).isEqualTo(604800);  // ← добавить
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void login_shouldThrowEmailNotVerifiedException_whenEmailNotConfirmed() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unverified@example.com");
        request.setPassword("password");

        UserEntity userEntity = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("unverified@example.com")
                .emailVerified(false)
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(userEntity);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void refresh_shouldGenerateNewTokens_whenRefreshTokenIsValid() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("any-refresh-token");

        UserEntity user = UserEntity.builder().id(UUID.randomUUID()).email("user@example.com").build();

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .user(user)
                .tokenHash("any-hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken()).thenReturn("new-refresh-token");
        when(jwtService.getAccessTtl()).thenReturn(java.time.Duration.ofMinutes(15));  // ← добавить
        when(jwtService.getRefreshTtl()).thenReturn(java.time.Duration.ofDays(7));

        TokenPairResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("new.access.token");
        assertThat(response.getAccessExpiresIn()).isEqualTo(900);      // ← добавить
        assertThat(response.getRefreshExpiresIn()).isEqualTo(604800);  // ← добавить
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
    }

    @Test
    void verify_shouldMarkEmailAsVerified_whenTokenIsValid() {
        VerifyEmailRequest request = new VerifyEmailRequest();
        request.setToken("any-verification-token");

        UserEntity user = UserEntity.builder().email("user@example.com").build();
        EmailVerificationTokenEntity tokenEntity = EmailVerificationTokenEntity.builder()
                .user(user)
                .token("any-verification-token")
                .expiresAt(Instant.now().plusSeconds(86400))
                .confirmedAt(null)
                .build();

        when(emailVerificationTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(tokenEntity));

        authService.verify(request);

        assertThat(tokenEntity.getConfirmedAt()).isNotNull();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void logout_shouldRevokeRefreshToken() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("any-token-to-logout");

        String tokenHash = "dafa416607b27022e4d1cbdcd812469078184993cb96168f2abe8cb8d6f92a40";

        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
                .tokenHash(tokenHash)
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(tokenEntity));

        authService.logout(request);

        assertThat(tokenEntity.isRevoked()).isTrue();

        verify(refreshTokenRepository, never()).save(any(RefreshTokenEntity.class));
        verify(refreshTokenRepository).findByTokenHash(anyString());
    }
}