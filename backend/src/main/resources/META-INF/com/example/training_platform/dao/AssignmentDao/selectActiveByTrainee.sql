select
  id,
  trainee_id,
  curriculum_id,
  assigned_by,
  status,
  assigned_at,
  ended_at
from trainee_curriculum_assignments
where trainee_id = /* traineeId */0
  and status = 'ACTIVE'
order by assigned_at desc
limit 1
