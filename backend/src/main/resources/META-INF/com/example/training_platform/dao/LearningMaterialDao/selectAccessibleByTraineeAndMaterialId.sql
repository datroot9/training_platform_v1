select
  lm.id,
  lm.curriculum_id,
  lm.sort_order,
  lm.file_name,
  lm.storage_path,
  lm.file_size_bytes,
  lm.uploaded_at
from learning_materials lm
join trainee_curriculum_assignments a on a.curriculum_id = lm.curriculum_id
where a.trainee_id = /* traineeId */0
  and a.status = 'ACTIVE'
  and lm.id = /* materialId */0
limit 1
