CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role ENUM('MENTOR', 'TRAINEE') NOT NULL,
    mentor_id BIGINT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_mentor FOREIGN KEY (mentor_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE curricula (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_curricula_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE learning_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    curriculum_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_materials_curriculum FOREIGN KEY (curriculum_id) REFERENCES curricula(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    curriculum_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_templates_curriculum_sort UNIQUE (curriculum_id, sort_order),
    CONSTRAINT fk_task_templates_curriculum FOREIGN KEY (curriculum_id) REFERENCES curricula(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trainee_curriculum_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trainee_id BIGINT NOT NULL,
    curriculum_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    status ENUM('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assignments_trainee FOREIGN KEY (trainee_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignments_curriculum FOREIGN KEY (curriculum_id) REFERENCES curricula(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    task_template_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status ENUM('NOT_STARTED', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'NOT_STARTED',
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_assignment FOREIGN KEY (assignment_id) REFERENCES trainee_curriculum_assignments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_template FOREIGN KEY (task_template_id) REFERENCES task_templates(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    status ENUM('DRAFT', 'SUBMITTED', 'APPROVED', 'REVISION_REQUIRED') NOT NULL DEFAULT 'DRAFT',
    what_done TEXT NULL,
    planned_tomorrow TEXT NULL,
    blockers TEXT NULL,
    mentor_feedback TEXT NULL,
    mentor_grade DECIMAL(4,2) NULL,
    submitted_at DATETIME NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_reports_assignment_date UNIQUE (assignment_id, report_date),
    CONSTRAINT fk_daily_reports_assignment FOREIGN KEY (assignment_id) REFERENCES trainee_curriculum_assignments(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE daily_report_task_hours (
    id BIGINT NOT NULL AUTO_INCREMENT,
    daily_report_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    hours DECIMAL(5,2) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_report_task UNIQUE (daily_report_id, task_id),
    CONSTRAINT fk_daily_report_task_hours_report FOREIGN KEY (daily_report_id) REFERENCES daily_reports(id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_report_task_hours_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    body TEXT NOT NULL,
    status ENUM('OPEN', 'ANSWERED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiries_from_user FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inquiries_to_user FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inquiries_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE weekly_performance_summaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    summary_text TEXT NULL,
    completion_rate DECIMAL(5,2) NULL,
    average_daily_hours DECIMAL(5,2) NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_weekly_summary_assignment_week_start UNIQUE (assignment_id, week_start),
    CONSTRAINT fk_weekly_summary_assignment FOREIGN KEY (assignment_id) REFERENCES trainee_curriculum_assignments(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_tasks_assignment_id ON tasks(assignment_id);
CREATE INDEX idx_daily_reports_assignment_report_date ON daily_reports(assignment_id, report_date);
CREATE INDEX idx_daily_report_task_hours_task_id ON daily_report_task_hours(task_id);
