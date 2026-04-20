select
  id,
  email,
  full_name,
  is_active,
  mentor_id,
  created_at
from users
where role = 'TRAINEE'
  and mentor_id = /* mentorId */0
/*%if keyword != null */
  and (
    lower(email) like lower(concat('%', /* keyword */'', '%'))
    or lower(full_name) like lower(concat('%', /* keyword */'', '%'))
  )
/*%end */
/*%if active != null */
  and is_active = /* active */1
/*%end */
order by
/*%if sortBy == "email" */
email
/*%elseif sortBy == "full_name" */
full_name
/*%else */
created_at
/*%end */
/*%if sortDir == "asc" */
asc
/*%else */
desc
/*%end */
limit /* limit */10
offset /* offset */0
