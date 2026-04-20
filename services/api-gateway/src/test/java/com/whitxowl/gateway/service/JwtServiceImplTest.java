package com.whitxowl.gateway.service;

import com.whitxowl.gateway.config.JwtProperties;
import com.whitxowl.gateway.service.impl.JwtServiceImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            "test-secret-key-for-unit-tests!!".getBytes()
    );
    private final String issuer = "auth-service";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getSecret()).thenReturn(secret);
        lenient().when(jwtProperties.getIssuer()).thenReturn(issuer);

        ReflectionTestUtils.invokeMethod(jwtService, "init");

        signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    private String buildToken(String subject, String issuer, List<String> roles, Date expiration) {
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .claim("roles", roles)
                .claim("email", subject + "@test.com")
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    @Test
    void parse_validToken_returnsClaims() {
        String token = buildToken(
                "user-123",
                issuer,
                List.of("ROLE_USER"),
                new Date(System.currentTimeMillis() + 60_000)
        );

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user-123");
        assertThat(claims.getIssuer()).isEqualTo(issuer);
    }

    @Test
    void parse_expiredToken_throwsException() {
        String token = buildToken(
                "user-123",
                issuer,
                List.of("ROLE_USER"),
                new Date(System.currentTimeMillis() - 1000)
        );

        assertThrows(ExpiredJwtException.class, () -> jwtService.parse(token));
    }

    @Test
    void parse_wrongIssuer_throwsException() {
        String token = buildToken(
                "user-123",
                "wrong-issuer",
                List.of("ROLE_USER"),
                new Date(System.currentTimeMillis() + 60_000)
        );

        assertThrows(IncorrectClaimException.class, () -> jwtService.parse(token));
    }

    @Test
    void parse_wrongSignature_throwsException() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                Base64.getEncoder().encodeToString("another-secret-key-totally-diff!!".getBytes())
        ));
        String token = Jwts.builder()
                .subject("user-123")
                .issuer(issuer)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertThrows(SecurityException.class, () -> jwtService.parse(token));
    }

    @Test
    void parse_malformedToken_throwsException() {
        assertThrows(MalformedJwtException.class, () -> jwtService.parse("not.a.jwt"));
    }

    @Test
    void getRoles_returnsRolesFromClaims() {
        String token = buildToken(
                "user-123",
                issuer,
                List.of("ROLE_USER", "ROLE_ADMIN"),
                new Date(System.currentTimeMillis() + 60_000)
        );

        Claims claims = jwtService.parse(token);

        assertThat(jwtService.getRoles(claims))
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void getRoles_noRolesClaim_returnsNull() {
        String token = Jwts.builder()
                .subject("user-123")
                .issuer(issuer)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        Claims claims = jwtService.parse(token);

        assertNull(jwtService.getRoles(claims));
    }
}
