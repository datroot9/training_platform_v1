package com.example.training_platform.account;

import java.util.List;

import com.example.training_platform.auth.AuthService;
import com.example.training_platform.auth.PasswordHashService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHashService passwordHashService;
    private final AuthService authService;

    public AccountService(JdbcTemplate jdbcTemplate, PasswordHashService passwordHashService, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHashService = passwordHashService;
        this.authService = authService;
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        List<String> rows = jdbcTemplate.query(
                "select password_hash from users where id = ? and is_active = 1",
                (rs, i) -> rs.getString("password_hash"),
                userId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        String currentHash = rows.get(0);
        if (!passwordHashService.matches(oldPassword, currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        String newHash = passwordHashService.hash(newPassword);
        jdbcTemplate.update(
                "update users set password_hash = ?, must_change_password = 0, password_updated_at = CURRENT_TIMESTAMP where id = ?",
                newHash,
                userId
        );
        authService.revokeAllRefreshTokens(userId);
    }
}
