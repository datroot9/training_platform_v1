select
  t.id,
  t.assignment_id,
  t.task_template_id,
  tt.sort_order,
  t.title,
  t.description,
  t.estimated_days,
  t.status,
  t.started_at,
  t.completed_at,
  t.created_at,
  t.updated_at,
  lm.id as learning_material_id,
  lm.file_name as learning_material_file_name
from tasks t
join task_templates tt on tt.id = t.task_template_id
left join learning_materials lm on lm.id = tt.learning_material_id
where t.assignment_id = /* assignmentId */0
order by tt.sort_order asc, t.id asc
