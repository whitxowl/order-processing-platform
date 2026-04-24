package com.whitxowl.authservice.service;

import com.whitxowl.authservice.config.JwtProperties;
import com.whitxowl.authservice.service.impl.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceImplTest {

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private JwtServiceImpl jwtService;

    private final String secret = Base64.getEncoder().encodeToString(
            "my-super-secret-key-32-characters-long-minimum".getBytes()
    );
    private final String issuer = "test-app";

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getSecret()).thenReturn(secret);
        lenient().when(jwtProperties.getIssuer()).thenReturn(issuer);
        lenient().when(jwtProperties.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(jwtProperties.getRefreshTtl()).thenReturn(Duration.ofDays(7));

        ReflectionTestUtils.invokeMethod(jwtService, "init");
    }

    @Test
    void generateAccessToken_ShouldCreateValidToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        List<String> roles = List.of("ROLE_USER");

        String token = jwtService.generateAccessToken(userId, email, roles);

        assertThat(token).isNotBlank();

        Claims claims = jwtService.parseAccessToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuer()).isEqualTo(issuer);
        assertThat(claims.get("email")).isEqualTo(email);
    }

    @Test
    void generateRefreshToken_ShouldReturnUuid() {
        String token = jwtService.generateRefreshToken();

        assertThat(token).isNotNull();
        assertThat(UUID.fromString(token)).isNotNull();
    }

    @Test
    void parseAccessToken_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(Exception.class, () -> jwtService.parseAccessToken("invalid.token.string"));
    }

    @Test
    void getUserId_ShouldReturnCorrectUuid() {
        UUID userId = UUID.randomUUID();
        Claims claims = Jwts.claims().subject(userId.toString()).build();

        UUID result = jwtService.getUserId(claims);

        assertThat(result).isEqualTo(userId);
    }
}