select count(*)
from curricula
where id = /* curriculumId */0
  and created_by = /* creatorId */0
  and status = 'DRAFT'
