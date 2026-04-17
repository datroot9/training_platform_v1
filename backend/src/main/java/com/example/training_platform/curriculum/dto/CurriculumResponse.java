package com.example.training_platform.curriculum.dto;

import java.time.LocalDateTime;

public record CurriculumResponse(
        Long id,
        String name,
        String description,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
