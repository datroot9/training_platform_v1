package com.example.training_platform.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCurriculumRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description
) {
}
