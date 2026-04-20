package com.example.training_platform.curriculum.dto;

import java.time.LocalDateTime;

public record TaskTemplateResponse(
        Long id,
        Long curriculumId,
        Long learningMaterialId,
        int sortOrder,
        String title,
        String description,
        Integer estimatedDays,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
