package com.example.training_platform.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DailyReportResourceInputRequest(
        @NotBlank
        @Size(max = 50)
        String type,
        @Size(max = 255)
        String label,
        @NotBlank
        @Size(max = 500)
        String url
) {
}
