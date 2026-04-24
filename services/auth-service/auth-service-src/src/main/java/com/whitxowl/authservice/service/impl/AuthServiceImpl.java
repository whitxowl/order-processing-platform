package com.whitxowl.authservice.service.impl;

import com.whitxowl.authservice.api.constant.RoleConstant;
import com.whitxowl.authservice.api.dto.request.LoginRequest;
import com.whitxowl.authservice.api.dto.request.RefreshRequest;
import com.whitxowl.authservice.api.dto.request.RegisterRequest;
import com.whitxowl.authservice.api.dto.request.VerifyEmailRequest;
import com.whitxowl.authservice.api.dto.response.TokenPairResponse;
import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.domain.entity.EmailVerificationTokenEntity;
import com.whitxowl.authservice.domain.entity.RefreshTokenEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import com.whitxowl.authservice.exception.EmailNotVerifiedException;
import com.whitxowl.authservice.exception.InvalidTokenException;
import com.whitxowl.authservice.exception.UserAlreadyExistsException;
import com.whitxowl.authservice.kafka.producer.UserCreatedEventProducer;
import com.whitxowl.authservice.mapper.UserMapper;
import com.whitxowl.authservice.repository.EmailVerificationTokenRepository;
import com.whitxowl.authservice.repository.RefreshTokenRepository;
import com.whitxowl.authservice.repository.UserRepository;
import com.whitxowl.authservice.security.CustomUserDetails;
import com.whitxowl.authservice.service.AuthService;
import com.whitxowl.authservice.service.JwtService;
import com.whitxowl.authservice.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int VERIFICATION_TOKEN_BYTES = 32;
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserCreatedEventProducer eventProducer;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(request.getEmail());
        }

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user.addRole(RoleConstant.ROLE_USER);

        userRepository.save(user);

        String verificationToken = TokenUtil.generateSecureHex(VERIFICATION_TOKEN_BYTES);
        EmailVerificationTokenEntity tokenEntity = EmailVerificationTokenEntity.builder()
                .user(user)
                .token(verificationToken)
                .expiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL))
                .build();
        emailVerificationTokenRepository.save(tokenEntity);

        eventProducer.produce(user);

        log.info("User registered: {}. Verification token hash: {}", user.getEmail(), TokenUtil.sha256(verificationToken));

        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        if (!userDetails.isEmailVerified()) {
            throw new EmailNotVerifiedException(userDetails.getEmail());
        }

        return generateTokenPair(userDetails.getId(), userDetails.getEmail(),
                userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
    }

    @Override
    @Transactional
    public TokenPairResponse refresh(RefreshRequest request) {
        String tokenHash = TokenUtil.sha256(request.getRefreshToken());

        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!tokenEntity.isUsable()) {
            throw new InvalidTokenException("Refresh token is expired or revoked");
        }

        tokenEntity.setRevoked(true);

        UserEntity user = tokenEntity.getUser();
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getRole())
                .toList();

        return generateTokenPair(user.getId(), user.getEmail(), roles);
    }

    @Override
    @Transactional
    public void verify(VerifyEmailRequest request) {
        EmailVerificationTokenEntity tokenEntity = emailVerificationTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Verification token not found"));

        if (!tokenEntity.isUsable()) {
            throw new InvalidTokenException("Verification token is expired or already used");
        }

        tokenEntity.setConfirmedAt(Instant.now());

        UserEntity user = tokenEntity.getUser();
        user.setEmailVerified(true);

        log.info("Email verified for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = TokenUtil.sha256(request.getRefreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.setRevoked(true));
    }

    private TokenPairResponse generateTokenPair(UUID userId, String email, List<String> roles) {
        String accessToken = jwtService.generateAccessToken(userId, email, roles);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshTokenEntity refreshEntity = RefreshTokenEntity.builder()
                .user(userRepository.getReferenceById(userId))
                .tokenHash(TokenUtil.sha256(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtService.getRefreshTtl()))
                .build();
        refreshTokenRepository.save(refreshEntity);

        return TokenPairResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .accessExpiresIn(jwtService.getAccessTtl().toSeconds())
                .refreshExpiresIn(jwtService.getRefreshTtl().toSeconds())
                .build();
    }
}