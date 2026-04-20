select
  id,
  curriculum_id,
  learning_material_id,
  sort_order,
  title,
  description,
  estimated_days,
  created_at,
  updated_at
from task_templates
where id = /* templateId */0
  and curriculum_id = /* curriculumId */0
limit 1
