package com.example.training_platform.reporting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyReportResponse(
        Long id,
        Long assignmentId,
        LocalDate reportDate,
        String status,
        String fresherLabel,
        Integer trainingDayIndex,
        String whatDone,
        String plannedTomorrow,
        String blockers,
        List<DailyReportResourceResponse> resources,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        List<DailyReportTaskHourResponse> taskHours
) {
}
