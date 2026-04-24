package com.example.training_platform.reporting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WeeklySummaryResponse(
        Long id,
        Long assignmentId,
        LocalDate weekStart,
        LocalDate weekEnd,
        List<String> accomplishments,
        List<String> difficulties,
        String summaryText,
        Double completionRate,
        Double averageDailyHours,
        String reviewStatus,
        String mentorFeedback,
        Double mentorGrade,
        LocalDateTime reviewedAt,
        LocalDateTime finalizedAt,
        LocalDateTime generatedAt
) {
}
