package com.example.training_platform.reporting.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewWeeklySummaryRequest(
        @NotNull
        @DecimalMin(value = "0.0", message = "mentorGrade must be >= 0")
        @DecimalMax(value = "10.0", message = "mentorGrade must be <= 10")
        Double mentorGrade,
        @NotBlank String mentorFeedback,
        Boolean finalizeWeek
) {
}
