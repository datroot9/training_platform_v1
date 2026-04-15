UPDATE users
SET
    password_hash = '${admin_default_password_bcrypt}',
    must_change_password = 1,
    password_updated_at = CURRENT_TIMESTAMP
WHERE email = 'admin@training.local';
