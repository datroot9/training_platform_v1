package com.example.training_platform.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyAggregationSchedulerTest {

    @Mock
    private ReportingService reportingService;

    @Test
    void runWeeklyAggregation_callsReportingServiceForPreviousWeekGeneration() {
        WeeklyAggregationScheduler scheduler = new WeeklyAggregationScheduler(reportingService);

        scheduler.runWeeklyAggregation();

        verify(reportingService).generatePreviousWeekForAllActiveAssignments(any());
    }
}
