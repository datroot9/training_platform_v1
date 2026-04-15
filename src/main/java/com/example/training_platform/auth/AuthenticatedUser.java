package com.example.training_platform.auth;

public record AuthenticatedUser(Long userId, String email, String role) {
}
