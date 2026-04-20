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
where curriculum_id = /* curriculumId */0
order by sort_order asc, id asc
