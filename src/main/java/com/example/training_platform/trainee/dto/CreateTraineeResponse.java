package com.example.training_platform.trainee.dto;

public record CreateTraineeResponse(
        Long userId,
        String email,
        String fullName,
        String role,
        String temporaryPassword,
        boolean mustChangePassword
) {
}
