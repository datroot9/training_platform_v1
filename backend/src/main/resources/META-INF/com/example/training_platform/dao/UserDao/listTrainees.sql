select
  u.id,
  u.email,
  u.full_name,
  u.is_active,
  u.mentor_id,
  u.created_at,
  a.id as active_assignment_id,
  c.name as active_curriculum_name,
  case when a.id is null then null else (
    select count(*) from tasks t_done
    where t_done.assignment_id = a.id and t_done.status = 'DONE'
  ) end as completed_task_count,
  case when a.id is null then null else (
    select count(*) from tasks t_all
    where t_all.assignment_id = a.id
  ) end as total_task_count
from users u
left join trainee_curriculum_assignments a
  on a.trainee_id = u.id and a.status = 'ACTIVE'
left join curricula c on c.id = a.curriculum_id
where u.role = 'TRAINEE'
  and u.mentor_id = /* mentorId */0
/*%if keyword != null */
  and (
    lower(u.email) like lower(concat('%', /* keyword */'', '%'))
    or lower(u.full_name) like lower(concat('%', /* keyword */'', '%'))
  )
/*%end */
/*%if active != null */
  and u.is_active = /* active */1
/*%end */
order by
/*%if sortBy == "email" */
u.email
/*%elseif sortBy == "full_name" */
u.full_name
/*%else */
u.created_at
/*%end */
/*%if sortDir == "asc" */
asc
/*%else */
desc
/*%end */
limit /* limit */10
offset /* offset */0
