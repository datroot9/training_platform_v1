select
  dr.id,
  dr.assignment_id,
  dr.report_date,
  dr.fresher_label,
  dr.training_day_index,
  dr.status,
  dr.what_done,
  dr.planned_tomorrow,
  dr.blockers,
  dr.mentor_feedback,
  dr.mentor_grade,
  dr.submitted_at,
  dr.reviewed_at,
  dr.created_at,
  dr.updated_at
from daily_reports dr
join trainee_curriculum_assignments a on a.id = dr.assignment_id
where a.trainee_id = /* traineeId */0
/*%if assignmentId != null */
  and dr.assignment_id = /* assignmentId */0
/*%end */
/*%if fromDate != null */
  and dr.report_date >= /* fromDate */'2026-01-01'
/*%end */
/*%if toDate != null */
  and dr.report_date <= /* toDate */'2026-01-31'
/*%end */
order by dr.report_date desc, dr.id desc
