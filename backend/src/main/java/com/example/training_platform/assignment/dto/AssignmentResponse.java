package com.example.training_platform.assignment.dto;

import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        Long traineeId,
        Long curriculumId,
        String curriculumName,
        String status,
        LocalDateTime assignedAt,
        LocalDateTime endedAt,
        int generatedTaskCount
) {
}
