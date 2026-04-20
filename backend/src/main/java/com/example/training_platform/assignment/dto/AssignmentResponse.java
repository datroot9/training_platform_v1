package com.example.training_platform.assignment.dto;

import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        Long traineeId,
        Long curriculumId,
        String curriculumName,
        String curriculumDescription,
        String mentorName,
        String mentorEmail,
        Integer totalEstimatedDays,
        String status,
        LocalDateTime assignedAt,
        LocalDateTime endedAt,
        int generatedTaskCount
) {
}
