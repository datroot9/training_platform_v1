package com.example.training_platform.assignment.dto;

import java.time.LocalDateTime;

public record AssignmentTaskResponse(
        Long id,
        Long assignmentId,
        Long taskTemplateId,
        int sortOrder,
        String title,
        String description,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
