package com.example.training_platform.curriculum.dto;

import java.time.LocalDateTime;

public record CurriculumResponse(
        Long id,
        Long curriculumGroupId,
        String versionLabel,
        String name,
        String description,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
