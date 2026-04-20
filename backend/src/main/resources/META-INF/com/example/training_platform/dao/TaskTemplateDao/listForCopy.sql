select
  learning_material_id,
  sort_order,
  title,
  description,
  estimated_days
from task_templates
where curriculum_id = /* curriculumId */0
order by sort_order asc, id asc
