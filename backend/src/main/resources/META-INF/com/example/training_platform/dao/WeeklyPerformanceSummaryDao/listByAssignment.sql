select
  id,
  assignment_id,
  week_start,
  week_end,
  summary_text,
  completion_rate,
  average_daily_hours,
  review_status,
  mentor_feedback,
  mentor_grade,
  reviewed_at,
  finalized_at,
  generated_at,
  updated_at
from weekly_performance_summaries
where assignment_id = /* assignmentId */0
order by week_start desc, id desc
