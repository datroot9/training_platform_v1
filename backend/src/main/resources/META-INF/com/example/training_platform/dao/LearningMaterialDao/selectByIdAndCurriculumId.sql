select
  id,
  curriculum_id,
  sort_order,
  file_name,
  storage_path,
  file_size_bytes,
  uploaded_at
from learning_materials
where id = /* materialId */0
  and curriculum_id = /* curriculumId */0
limit 1
