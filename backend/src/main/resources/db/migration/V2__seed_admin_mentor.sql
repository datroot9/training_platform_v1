INSERT INTO users (email, full_name, role, mentor_id, is_active)
VALUES ('admin@training.local', 'System Mentor Admin', 'MENTOR', NULL, 1)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    role = VALUES(role),
    mentor_id = VALUES(mentor_id),
    is_active = VALUES(is_active);
