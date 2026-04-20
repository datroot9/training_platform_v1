package com.example.training_platform.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.training_platform.auth.dto.AuthResponse;
import com.example.training_platform.dao.RefreshTokenDao;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.projection.AuthUserProjection;
import com.example.training_platform.entity.RefreshTokenEntity;
import com.example.training_platform.entity.UserEntity;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserDao userDao;
    private final RefreshTokenDao refreshTokenDao;
    private final JwtService jwtService;
    private final PasswordHashService passwordHashService;

    public AuthService(
            UserDao userDao,
            RefreshTokenDao refreshTokenDao,
            JwtService jwtService,
            PasswordHashService passwordHashService
    ) {
        this.userDao = userDao;
        this.refreshTokenDao = refreshTokenDao;
        this.jwtService = jwtService;
        this.passwordHashService = passwordHashService;
    }

    public AuthResponse login(String email, String password) {
        UserAuthProjection user = findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }
        if (!passwordHashService.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!passwordHashService.isBcrypt(user.passwordHash())) {
            String upgradedHash = passwordHashService.hash(password);
            UserEntity persisted = userDao.selectById(user.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
            persisted.setPasswordHash(upgradedHash);
            persisted.setPasswordUpdatedAt(LocalDateTime.now());
            userDao.update(persisted);
        }
        return issueTokens(user, true);
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseClaims(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        Long userId = Long.valueOf(claims.getSubject());
        String tokenHash = passwordHashService.sha256(refreshToken);
        long validRows = refreshTokenDao.countValidToken(userId, tokenHash);
        if (validRows <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token revoked or expired");
        }

        refreshTokenDao.selectActiveToken(userId, tokenHash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenDao.update(token);
        });

        UserAuthProjection user = findUserById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!user.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }
        return issueTokens(user, false);
    }

    public void logout(String refreshToken) {
        Claims claims = jwtService.parseClaims(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            return;
        }
        Long userId = Long.valueOf(claims.getSubject());
        String tokenHash = passwordHashService.sha256(refreshToken);
        refreshTokenDao.selectActiveToken(userId, tokenHash).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenDao.update(token);
        });
    }

    public void revokeAllRefreshTokens(Long userId) {
        List<RefreshTokenEntity> activeTokens = refreshTokenDao.selectAllActiveTokensByUser(userId);
        if (activeTokens.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (RefreshTokenEntity token : activeTokens) {
            token.setRevokedAt(now);
        }
        refreshTokenDao.batchUpdate(activeTokens);
    }

    private AuthResponse issueTokens(UserAuthProjection user, boolean includeMustChangePassword) {
        String accessToken = jwtService.createAccessToken(user.id(), user.email(), user.role());
        String refreshToken = jwtService.createRefreshToken(user.id(), user.email(), user.role());
        String refreshHash = passwordHashService.sha256(refreshToken);

        Claims refreshClaims = jwtService.parseClaims(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(refreshClaims.getExpiration().toInstant(), ZoneOffset.UTC);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUserId(user.id());
        refreshTokenEntity.setTokenHash(refreshHash);
        refreshTokenEntity.setExpiresAt(expiresAt);
        refreshTokenDao.insert(refreshTokenEntity);

        Claims accessClaims = jwtService.parseClaims(accessToken);
        long expiresIn = (accessClaims.getExpiration().toInstant().toEpochMilli() - Instant.now().toEpochMilli()) / 1000;

        return new AuthResponse(
                user.id(),
                user.email(),
                user.role(),
                includeMustChangePassword && user.mustChangePassword(),
                "Bearer",
                Math.max(expiresIn, 0),
                accessToken,
                refreshToken
        );
    }

    public record UserAuthProjection(Long id, String email, String role, String passwordHash, boolean active,
                                     boolean mustChangePassword) {
    }

    public java.util.Optional<UserAuthProjection> findUserByEmail(String email) {
        return userDao.selectAuthUserByEmail(email).map(this::mapUserAuthProjection);
    }

    public java.util.Optional<UserAuthProjection> findUserById(Long userId) {
        return userDao.selectAuthUserById(userId).map(this::mapUserAuthProjection);
    }

    private UserAuthProjection mapUserAuthProjection(AuthUserProjection projection) {
        return new UserAuthProjection(
                projection.getId(),
                projection.getEmail(),
                projection.getRole(),
                projection.getPasswordHash(),
                projection.isActive(),
                projection.isMustChangePassword()
        );
    }
}
