ALTER TABLE daily_reports
    ADD COLUMN fresher_label VARCHAR(255) NULL AFTER report_date,
    ADD COLUMN training_day_index INT NULL AFTER fresher_label;

CREATE TABLE daily_report_resources (
    id BIGINT NOT NULL AUTO_INCREMENT,
    daily_report_id BIGINT NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_label VARCHAR(255) NULL,
    resource_url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_report_resource_type UNIQUE (daily_report_id, resource_type),
    CONSTRAINT fk_daily_report_resources_report FOREIGN KEY (daily_report_id) REFERENCES daily_reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE weekly_performance_summaries
    ADD COLUMN review_status ENUM('PENDING', 'REVIEWED') NOT NULL DEFAULT 'PENDING' AFTER average_daily_hours,
    ADD COLUMN mentor_feedback TEXT NULL AFTER review_status,
    ADD COLUMN mentor_grade DECIMAL(4,2) NULL AFTER mentor_feedback,
    ADD COLUMN reviewed_at DATETIME NULL AFTER mentor_grade,
    ADD COLUMN finalized_at DATETIME NULL AFTER reviewed_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER generated_at;

CREATE INDEX idx_weekly_summary_assignment_week_end
    ON weekly_performance_summaries(assignment_id, week_end);

CREATE INDEX idx_daily_report_resources_report_id
    ON daily_report_resources(daily_report_id);
