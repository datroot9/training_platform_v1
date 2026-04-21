package com.example.training_platform.reporting.dto;

public record DailyReportResourceResponse(
        Long id,
        String type,
        String label,
        String url
) {
}
