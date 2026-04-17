ALTER TABLE users
ADD COLUMN password_hash VARCHAR(255) NULL AFTER role;

UPDATE users
SET password_hash = SHA2('${admin_default_password}', 256)
WHERE email = 'admin@training.local';
