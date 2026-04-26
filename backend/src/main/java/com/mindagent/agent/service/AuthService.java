package com.mindagent.agent.service;

import com.mindagent.agent.config.AuthProperties;
import com.mindagent.agent.dto.auth.LoginResponse;
import com.mindagent.agent.entity.AppUser;
import com.mindagent.agent.entity.RefreshToken;
import com.mindagent.agent.repository.AppUserRepository;
import com.mindagent.agent.repository.RefreshTokenRepository;
import com.mindagent.agent.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       AuthProperties authProperties) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authProperties = authProperties;
    }

    public Mono<LoginResponse> login(String username, String password) {
        return Mono.fromCallable(() -> appUserRepository.findByUsername(username).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> validatePassword(user, password))
                .map(this::issueTokenPair);
    }

    public Mono<LoginResponse> refresh(String rawRefreshToken) {
        return Mono.fromCallable(() -> refreshInternal(rawRefreshToken))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<AppUser> validatePassword(AppUser user, String password) {
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return Mono.error(new IllegalArgumentException("Invalid username or password"));
        }
        return Mono.just(user);
    }

    private LoginResponse issueTokenPair(AppUser user) {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtTokenService.issueAccessToken(user);
        String refreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername(), user.getRole());
    }

    private LoginResponse refreshInternal(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        String incomingHash = hashRefreshToken(rawRefreshToken.trim());
        RefreshToken existing = refreshTokenRepository.findByTokenHashAndRevokedFalse(incomingHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new IllegalArgumentException("Refresh token expired");
        }

        AppUser user = appUserRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String newAccessToken = jwtTokenService.issueAccessToken(user);
        String newRefreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), newRefreshToken);

        return new LoginResponse(newAccessToken, newRefreshToken, user.getId(), user.getUsername(), user.getRole());
    }

    private void saveRefreshToken(Long userId, String rawRefreshToken) {
        RefreshToken record = new RefreshToken();
        record.setUserId(userId);
        record.setTokenHash(hashRefreshToken(rawRefreshToken));
        record.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1L, authProperties.getRefreshExpiresDays())));
        refreshTokenRepository.save(record);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }
}
