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
where assignment_id = /* assignmentId */0
  and id = /* taskId */0
limit 1
