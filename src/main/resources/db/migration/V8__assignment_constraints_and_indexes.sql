ALTER TABLE trainee_curriculum_assignments
ADD COLUMN active_trainee_id BIGINT
    GENERATED ALWAYS AS (
        CASE
            WHEN status = 'ACTIVE' THEN trainee_id
            ELSE NULL
        END
    ) STORED;

ALTER TABLE trainee_curriculum_assignments
ADD CONSTRAINT uk_assignments_single_active_per_trainee UNIQUE (active_trainee_id);

CREATE INDEX idx_assignments_trainee_status
    ON trainee_curriculum_assignments(trainee_id, status);

CREATE INDEX idx_tasks_assignment_status
    ON tasks(assignment_id, status);

CREATE INDEX idx_learning_materials_curriculum_id_id
    ON learning_materials(curriculum_id, id);
