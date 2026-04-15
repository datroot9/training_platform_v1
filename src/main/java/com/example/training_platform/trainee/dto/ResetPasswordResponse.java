package com.example.training_platform.trainee.dto;

public record ResetPasswordResponse(
        Long userId,
        String email,
        String temporaryPassword,
        boolean mustChangePassword
) {
}
