select
  id,
  email,
  full_name,
  role,
  password_hash,
  must_change_password,
  password_updated_at,
  mentor_id,
  is_active,
  created_at,
  updated_at
from users
where id = /* id */0
