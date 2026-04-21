select
  id,
  assignment_id,
  report_date,
  fresher_label,
  training_day_index,
  status,
  what_done,
  planned_tomorrow,
  blockers,
  mentor_feedback,
  mentor_grade,
  submitted_at,
  reviewed_at,
  created_at,
  updated_at
from daily_reports
where assignment_id = /* assignmentId */0
  and report_date >= /* fromDate */'2026-01-01'
  and report_date <= /* toDate */'2026-01-07'
order by report_date asc, id asc
