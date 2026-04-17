SET @mentor_id := (
    SELECT id
    FROM users
    WHERE email = 'admin@training.local'
    LIMIT 1
);

INSERT INTO curricula (name, description, status, published_at, created_by)
SELECT
    'Backend API Fundamentals',
    'MVP curriculum for Spring Boot API development, security basics, and clean service structure.',
    'PUBLISHED',
    CURRENT_TIMESTAMP,
    @mentor_id
FROM dual
WHERE @mentor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM curricula
      WHERE name = 'Backend API Fundamentals'
        AND created_by = @mentor_id
  );

INSERT INTO curricula (name, description, status, created_by)
SELECT
    'Curriculum Authoring Demo',
    'Draft curriculum for testing PDF upload, template ordering, and publish flow.',
    'DRAFT',
    @mentor_id
FROM dual
WHERE @mentor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM curricula
      WHERE name = 'Curriculum Authoring Demo'
        AND created_by = @mentor_id
  );

SET @published_curriculum_id := (
    SELECT id
    FROM curricula
    WHERE name = 'Backend API Fundamentals'
      AND created_by = @mentor_id
    LIMIT 1
);

SET @draft_curriculum_id := (
    SELECT id
    FROM curricula
    WHERE name = 'Curriculum Authoring Demo'
      AND created_by = @mentor_id
    LIMIT 1
);

INSERT INTO learning_materials (curriculum_id, sort_order, file_name, storage_path, file_size_bytes)
SELECT
    @published_curriculum_id,
    1,
    '01-rest-api-overview.pdf',
    'seed/backend-api-fundamentals/01-rest-api-overview.pdf',
    1048576
FROM dual
WHERE @published_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM learning_materials
      WHERE curriculum_id = @published_curriculum_id
        AND sort_order = 1
  );

INSERT INTO learning_materials (curriculum_id, sort_order, file_name, storage_path, file_size_bytes)
SELECT
    @published_curriculum_id,
    2,
    '02-auth-and-jwt.pdf',
    'seed/backend-api-fundamentals/02-auth-and-jwt.pdf',
    1536000
FROM dual
WHERE @published_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM learning_materials
      WHERE curriculum_id = @published_curriculum_id
        AND sort_order = 2
  );

INSERT INTO learning_materials (curriculum_id, sort_order, file_name, storage_path, file_size_bytes)
SELECT
    @draft_curriculum_id,
    1,
    '01-curriculum-authoring-intro.pdf',
    'seed/curriculum-authoring-demo/01-curriculum-authoring-intro.pdf',
    716800
FROM dual
WHERE @draft_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM learning_materials
      WHERE curriculum_id = @draft_curriculum_id
        AND sort_order = 1
  );

SET @published_material_1 := (
    SELECT id
    FROM learning_materials
    WHERE curriculum_id = @published_curriculum_id
      AND sort_order = 1
    LIMIT 1
);

SET @published_material_2 := (
    SELECT id
    FROM learning_materials
    WHERE curriculum_id = @published_curriculum_id
      AND sort_order = 2
    LIMIT 1
);

SET @draft_material_1 := (
    SELECT id
    FROM learning_materials
    WHERE curriculum_id = @draft_curriculum_id
      AND sort_order = 1
    LIMIT 1
);

INSERT INTO task_templates (curriculum_id, learning_material_id, sort_order, title, description)
SELECT
    @published_curriculum_id,
    @published_material_1,
    1,
    'Build First CRUD Endpoint',
    'Implement a basic CRUD endpoint with validation and error handling.'
FROM dual
WHERE @published_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM task_templates
      WHERE curriculum_id = @published_curriculum_id
        AND sort_order = 1
  );

INSERT INTO task_templates (curriculum_id, learning_material_id, sort_order, title, description)
SELECT
    @published_curriculum_id,
    @published_material_2,
    2,
    'Add JWT Authentication',
    'Protect mentor routes using JWT and role-based authorization.'
FROM dual
WHERE @published_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM task_templates
      WHERE curriculum_id = @published_curriculum_id
        AND sort_order = 2
  );

INSERT INTO task_templates (curriculum_id, learning_material_id, sort_order, title, description)
SELECT
    @draft_curriculum_id,
    @draft_material_1,
    1,
    'Create Curriculum via API',
    'Use mentor API to create curriculum, upload one PDF, and verify detail response.'
FROM dual
WHERE @draft_curriculum_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM task_templates
      WHERE curriculum_id = @draft_curriculum_id
        AND sort_order = 1
  );
