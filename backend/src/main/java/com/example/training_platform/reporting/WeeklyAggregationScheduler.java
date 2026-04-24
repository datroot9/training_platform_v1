package com.example.training_platform.reporting;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklyAggregationScheduler {

    private final ReportingService reportingService;

    public WeeklyAggregationScheduler(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @Scheduled(cron = "${app.reporting.weekly-aggregation-cron:0 5 0 * * MON}")
    public void runWeeklyAggregation() {
        reportingService.generatePreviousWeekForAllActiveAssignments(LocalDate.now());
    }
}
