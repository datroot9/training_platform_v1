package com.example.training_platform.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.jdbc.entity.NamingType;

@Data
@NoArgsConstructor
@Entity(naming = NamingType.SNAKE_LOWER_CASE)
@Table(name = "weekly_performance_summaries")
public class WeeklyPerformanceSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long assignmentId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String summaryText;
    private Double completionRate;
    private Double averageDailyHours;
    private String reviewStatus;
    private String mentorFeedback;
    private Double mentorGrade;
    private LocalDateTime reviewedAt;
    private LocalDateTime finalizedAt;
    @Column(insertable = false, updatable = false)
    private LocalDateTime generatedAt;
    @Column(insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
