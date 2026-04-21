package com.example.training_platform.trainee.dto;

import java.time.LocalDateTime;

public record TraineeResponse(
        Long id,
        String email,
        String fullName,
        boolean active,
        Long mentorId,
        LocalDateTime createdAt,
        Long activeAssignmentId,
        String activeCurriculumName,
        Integer completedTaskCount,
        Integer totalTaskCount
) {
}
