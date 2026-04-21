select
  id,
  daily_report_id,
  resource_type,
  resource_label,
  resource_url,
  created_at
from daily_report_resources
where daily_report_id = /* dailyReportId */0
order by id asc
