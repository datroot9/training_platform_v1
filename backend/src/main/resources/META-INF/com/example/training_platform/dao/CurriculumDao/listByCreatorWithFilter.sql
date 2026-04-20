select
  id,
  name,
  description,
  status,
  published_at,
  created_by,
  curriculum_group_id,
  version_label,
  created_at,
  updated_at
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
order by
/*%if sortBy == "name" */
name
/*%elseif sortBy == "status" */
status
/*%elseif sortBy == "created_at" */
created_at
/*%elseif sortBy == "published_at" */
published_at
/*%elseif sortBy == "version_label" */
version_label
/*%else */
updated_at
/*%end */
/*%if sortDir == "asc" */
asc
/*%else */
desc
/*%end */
limit /* limit */10
offset /* offset */0
