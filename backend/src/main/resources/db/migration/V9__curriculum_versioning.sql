ALTER TABLE curricula
ADD COLUMN curriculum_group_id BIGINT NULL AFTER created_by;

ALTER TABLE curricula
ADD COLUMN version_label VARCHAR(64) NOT NULL DEFAULT '1.0' AFTER curriculum_group_id;

UPDATE curricula
SET curriculum_group_id = id
WHERE curriculum_group_id IS NULL;

ALTER TABLE curricula
MODIFY COLUMN curriculum_group_id BIGINT NOT NULL;

ALTER TABLE curricula
ADD CONSTRAINT uk_curricula_group_version UNIQUE (curriculum_group_id, version_label);

CREATE INDEX idx_curricula_curriculum_group_id ON curricula (curriculum_group_id);
