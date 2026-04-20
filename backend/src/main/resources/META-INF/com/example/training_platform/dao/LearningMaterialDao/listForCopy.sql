select
  id,
  sort_order,
  file_name,
  storage_path,
  file_size_bytes,
  uploaded_at
from learning_materials
where curriculum_id = /* curriculumId */0
order by sort_order asc, id asc
