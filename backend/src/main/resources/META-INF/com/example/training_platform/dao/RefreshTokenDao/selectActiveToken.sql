select
  id,
  user_id,
  token_hash,
  expires_at,
  revoked_at,
  created_at,
  updated_at
from refresh_tokens
where user_id = /* userId */0
  and token_hash = /* tokenHash */''
  and revoked_at is null
limit 1
