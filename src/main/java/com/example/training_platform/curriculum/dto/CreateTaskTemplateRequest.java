package com.example.training_platform.curriculum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskTemplateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Long learningMaterialId
) {
}
