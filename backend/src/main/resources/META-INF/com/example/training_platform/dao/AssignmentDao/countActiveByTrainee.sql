select count(*)
from trainee_curriculum_assignments
where trainee_id = /* traineeId */0
  and status = 'ACTIVE'
