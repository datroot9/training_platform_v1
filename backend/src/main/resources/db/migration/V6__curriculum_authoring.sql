ALTER TABLE curricula
ADD COLUMN status ENUM('DRAFT', 'PUBLISHED') NOT NULL DEFAULT 'DRAFT' AFTER description,
ADD COLUMN published_at DATETIME NULL AFTER status;

ALTER TABLE learning_materials
ADD COLUMN sort_order INT NULL AFTER curriculum_id;

UPDATE learning_materials lm
INNER JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY curriculum_id ORDER BY id) AS rn
    FROM learning_materials
) t ON lm.id = t.id
SET lm.sort_order = t.rn;

ALTER TABLE learning_materials
MODIFY COLUMN sort_order INT NOT NULL;

ALTER TABLE learning_materials
ADD CONSTRAINT uk_learning_materials_curriculum_sort UNIQUE (curriculum_id, sort_order);

ALTER TABLE task_templates
ADD COLUMN learning_material_id BIGINT NULL AFTER curriculum_id,
ADD CONSTRAINT fk_task_templates_learning_material FOREIGN KEY (learning_material_id) REFERENCES learning_materials(id) ON DELETE SET NULL;

CREATE INDEX idx_task_templates_learning_material ON task_templates(learning_material_id);
