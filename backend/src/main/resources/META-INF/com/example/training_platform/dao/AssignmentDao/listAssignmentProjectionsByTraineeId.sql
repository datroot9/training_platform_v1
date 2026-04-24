select
  a.id,
  a.trainee_id,
  a.curriculum_id,
  c.name as curriculum_name,
  c.description as curriculum_description,
  c.version_label as curriculum_version_label,
  m.full_name as mentor_name,
  m.email as mentor_email,
  a.status,
  a.assigned_at,
  a.ended_at
from trainee_curriculum_assignments a
join curricula c on c.id = a.curriculum_id
join users t on t.id = a.trainee_id
left join users m on m.id = t.mentor_id
where a.trainee_id = /* traineeId */0
order by a.assigned_at desc, a.id desc
