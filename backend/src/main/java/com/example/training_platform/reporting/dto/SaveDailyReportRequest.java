package com.example.training_platform.reporting.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveDailyReportRequest(
        @NotBlank
        @Size(max = 255)
        String fresherLabel,
        @NotNull
        @Min(1)
        Integer trainingDayIndex,
        @NotBlank String whatDone,
        @NotBlank String plannedTomorrow,
        @NotBlank String blockers,
        @Valid List<DailyReportResourceInputRequest> resources,
        @Valid List<DailyReportTaskHourInputRequest> taskHours
) {
}
