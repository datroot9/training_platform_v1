package com.example.training_platform.reporting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DailyReportTaskHourInputRequest(
        @NotNull Long taskId,
        @NotNull
        @DecimalMin(value = "0.25", message = "hours must be at least 0.25")
        Double hours,
        @Size(max = 500) String notes
) {
}
