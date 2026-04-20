package com.example.training_platform.account;

import com.example.training_platform.auth.AuthService;
import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.entity.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final UserDao userDao;
    private final PasswordHashService passwordHashService;
    private final AuthService authService;

    public AccountService(UserDao userDao, PasswordHashService passwordHashService, AuthService authService) {
        this.userDao = userDao;
        this.passwordHashService = passwordHashService;
        this.authService = authService;
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = userDao.selectActiveUserById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String currentHash = user.getPasswordHash();
        if (!passwordHashService.matches(oldPassword, currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        String newHash = passwordHashService.hash(newPassword);
        user.setPasswordHash(newHash);
        user.setMustChangePassword(false);
        user.setPasswordUpdatedAt(java.time.LocalDateTime.now());
        userDao.update(user);
        authService.revokeAllRefreshTokens(userId);
    }
}
