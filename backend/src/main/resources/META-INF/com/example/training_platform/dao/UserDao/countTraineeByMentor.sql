select count(*)
from users
where id = /* traineeId */0
  and role = 'TRAINEE'
  and mentor_id = /* mentorId */0
