package com.example.training_platform.reporting.dto;

public record DailyReportTaskHourResponse(
        Long id,
        Long taskId,
        Double hours,
        String notes
) {
}
