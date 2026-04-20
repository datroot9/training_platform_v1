ALTER TABLE task_templates
ADD COLUMN estimated_days INT NULL AFTER description;

ALTER TABLE tasks
ADD COLUMN estimated_days INT NULL AFTER description;

ALTER TABLE trainee_curriculum_assignments
DROP COLUMN estimated_completion_days;
