select
  curriculum_group_id,
  name,
  description,
  status
from curricula
where id = /* sourceCurriculumId */0
  and created_by = /* creatorId */0
limit 1
