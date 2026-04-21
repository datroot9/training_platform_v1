select
  id,
  daily_report_id,
  task_id,
  hours,
  notes,
  created_at
from daily_report_task_hours
where daily_report_id = /* dailyReportId */0
order by id asc
