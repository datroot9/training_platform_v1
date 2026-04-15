package com.example.training_platform.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.training_platform.auth.dto.AuthResponse;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final PasswordHashService passwordHashService;

    public AuthService(JdbcTemplate jdbcTemplate, JwtService jwtService, PasswordHashService passwordHashService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.passwordHashService = passwordHashService;
    }

    public AuthResponse login(String email, String password) {
        UserAuthProjection user = findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!user.active()) {
            throw new AccessDeniedException("Account is deactivated");
        }
        if (!passwordHashService.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!passwordHashService.isBcrypt(user.passwordHash())) {
            String upgradedHash = passwordHashService.hash(password);
            jdbcTemplate.update(
                    "update users set password_hash = ?, password_updated_at = CURRENT_TIMESTAMP where id = ?",
                    upgradedHash,
                    user.id()
            );
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
        int validRows = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and token_hash = ? and revoked_at is null and expires_at > CURRENT_TIMESTAMP",
                Integer.class,
                userId,
                tokenHash
        );
        if (validRows <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token revoked or expired");
        }

        jdbcTemplate.update(
                "update refresh_tokens set revoked_at = CURRENT_TIMESTAMP where user_id = ? and token_hash = ? and revoked_at is null",
                userId,
                tokenHash
        );

        UserAuthProjection user = findUserById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!user.active()) {
            throw new AccessDeniedException("Account is deactivated");
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
        jdbcTemplate.update(
                "update refresh_tokens set revoked_at = CURRENT_TIMESTAMP where user_id = ? and token_hash = ? and revoked_at is null",
                userId,
                tokenHash
        );
    }

    public void revokeAllRefreshTokens(Long userId) {
        jdbcTemplate.update(
                "update refresh_tokens set revoked_at = CURRENT_TIMESTAMP where user_id = ? and revoked_at is null",
                userId
        );
    }

    private AuthResponse issueTokens(UserAuthProjection user, boolean includeMustChangePassword) {
        String accessToken = jwtService.createAccessToken(user.id(), user.email(), user.role());
        String refreshToken = jwtService.createRefreshToken(user.id(), user.email(), user.role());
        String refreshHash = passwordHashService.sha256(refreshToken);

        Claims refreshClaims = jwtService.parseClaims(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(refreshClaims.getExpiration().toInstant(), ZoneOffset.UTC);

        jdbcTemplate.update(
                "insert into refresh_tokens (user_id, token_hash, expires_at) values (?, ?, ?)",
                user.id(),
                refreshHash,
                Timestamp.valueOf(expiresAt)
        );

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
        List<UserAuthProjection> rows = jdbcTemplate.query(
                "select id, email, role, password_hash, is_active, must_change_password from users where email = ?",
                this::mapUserAuthProjection,
                email
        );
        return rows.stream().findFirst();
    }

    public java.util.Optional<UserAuthProjection> findUserById(Long userId) {
        List<UserAuthProjection> rows = jdbcTemplate.query(
                "select id, email, role, password_hash, is_active, must_change_password from users where id = ?",
                this::mapUserAuthProjection,
                userId
        );
        return rows.stream().findFirst();
    }

    private UserAuthProjection mapUserAuthProjection(ResultSet rs, int rowNum) throws SQLException {
        return new UserAuthProjection(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("password_hash"),
                rs.getBoolean("is_active"),
                rs.getBoolean("must_change_password")
        );
    }
}
