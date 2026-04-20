select
  id,
  assignment_id,
  task_template_id,
  title,
  description,
  estimated_days,
  status,
  started_at,
  completed_at,
  created_at,
  updated_at
from tasks
where id = /* id */0
