select count(*)
from curricula
where created_by = /* creatorId */0
/*%if keyword != null */
  and (
    lower(name) like lower(concat('%', /* keyword */'', '%'))
    or lower(coalesce(description, '')) like lower(concat('%', /* keyword */'', '%'))
  )
/*%end */
/*%if status != null */
  and status = /* status */'DRAFT'
/*%end */
