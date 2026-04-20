-- Allow NULL on insert; application sets curriculum_group_id = id immediately after (see CurriculumService.create).
-- NOT NULL on curriculum_group_id caused INSERT to fail before the follow-up UPDATE, resulting in HTTP 500.

ALTER TABLE curricula
MODIFY COLUMN curriculum_group_id BIGINT NULL;
