select count(*)
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
