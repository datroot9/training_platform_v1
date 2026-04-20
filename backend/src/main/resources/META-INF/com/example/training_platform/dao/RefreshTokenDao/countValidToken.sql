select count(*)
from refresh_tokens
where user_id = /* userId */0
  and token_hash = /* tokenHash */''
  and revoked_at is null
  and expires_at > current_timestamp
