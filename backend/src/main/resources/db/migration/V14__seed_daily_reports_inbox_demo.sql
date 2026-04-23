INSERT INTO daily_reports (
    assignment_id,
    report_date,
    status,
    fresher_label,
    training_day_index,
    what_done,
    planned_tomorrow,
    blockers,
    submitted_at,
    reviewed_at
)
SELECT
    a.id,
    DATE_SUB(CURDATE(), INTERVAL sample_days.days_ago DAY) AS report_date,
    CASE
        WHEN sample_days.days_ago = 2 THEN 'DRAFT'
        ELSE 'SUBMITTED'
    END AS status,
    u.full_name,
    GREATEST(1, DATEDIFF(DATE_SUB(CURDATE(), INTERVAL sample_days.days_ago DAY), DATE(a.assigned_at)) + 1),
    CONCAT('Seeded daily report #', sample_days.days_ago, ' for inbox demo on active assignment.'),
    'Continue assignment tasks and update test coverage for reporting flows.',
    CASE
        WHEN sample_days.days_ago = 1 THEN 'Waiting for mentor feedback on API contract.'
        ELSE 'No blockers'
    END AS blockers,
    CASE
        WHEN sample_days.days_ago = 2 THEN NULL
        ELSE DATE_ADD(DATE_SUB(CURDATE(), INTERVAL sample_days.days_ago DAY), INTERVAL 17 HOUR)
    END AS submitted_at,
    NULL
FROM trainee_curriculum_assignments a
JOIN users u ON u.id = a.trainee_id
JOIN (
    SELECT 0 AS days_ago
    UNION ALL SELECT 1
    UNION ALL SELECT 2
) sample_days
WHERE a.status = 'ACTIVE'
  AND u.role = 'TRAINEE'
  AND u.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM daily_reports dr
      WHERE dr.assignment_id = a.id
        AND dr.report_date = DATE_SUB(CURDATE(), INTERVAL sample_days.days_ago DAY)
  );

INSERT INTO daily_report_resources (
    daily_report_id,
    resource_type,
    resource_label,
    resource_url
)
SELECT
    dr.id,
    'GITHUB',
    'Seeded demo pull request',
    CONCAT('https://github.com/example/training-platform/pull/', dr.id)
FROM daily_reports dr
JOIN trainee_curriculum_assignments a ON a.id = dr.assignment_id
JOIN users u ON u.id = a.trainee_id
WHERE a.status = 'ACTIVE'
  AND u.role = 'TRAINEE'
  AND dr.what_done LIKE 'Seeded daily report #%'
  AND NOT EXISTS (
      SELECT 1
      FROM daily_report_resources drr
      WHERE drr.daily_report_id = dr.id
        AND drr.resource_type = 'GITHUB'
  );

INSERT INTO daily_report_task_hours (
    daily_report_id,
    task_id,
    hours,
    notes
)
SELECT
    dr.id,
    first_task.task_id,
    2.00,
    'Seeded demo effort for inbox report list.'
FROM daily_reports dr
JOIN trainee_curriculum_assignments a ON a.id = dr.assignment_id
JOIN (
    SELECT assignment_id, MIN(id) AS task_id
    FROM tasks
    GROUP BY assignment_id
) first_task ON first_task.assignment_id = dr.assignment_id
WHERE a.status = 'ACTIVE'
  AND dr.what_done LIKE 'Seeded daily report #%'
  AND NOT EXISTS (
      SELECT 1
      FROM daily_report_task_hours drth
      WHERE drth.daily_report_id = dr.id
        AND drth.task_id = first_task.task_id
  );
