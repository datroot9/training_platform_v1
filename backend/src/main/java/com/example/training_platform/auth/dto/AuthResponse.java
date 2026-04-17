package com.example.training_platform.auth.dto;

public record AuthResponse(
        Long userId,
        String email,
        String role,
        boolean mustChangePassword,
        String tokenType,
        long expiresInSeconds,
        String accessToken,
        String refreshToken
) {
}
