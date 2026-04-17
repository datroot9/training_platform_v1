package com.example.training_platform.trainee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTraineeRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 255) String fullName
) {
}
