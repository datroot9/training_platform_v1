package com.example.training_platform.curriculum.dto;

import java.time.LocalDateTime;

public record LearningMaterialResponse(
        Long id,
        Long curriculumId,
        String fileName,
        String storagePath,
        long fileSizeBytes,
        int sortOrder,
        LocalDateTime uploadedAt
) {
}
