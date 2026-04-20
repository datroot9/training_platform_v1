select
  id,
  email,
  role,
  password_hash,
  is_active,
  must_change_password
from users
where email = /* email */'a@b.c'
