package com.example.training_platform.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCurriculumVersionRequest(
        @NotBlank @Size(max = 64) String versionLabel,
        @Size(max = 255) String name,
        String description
) {
}
