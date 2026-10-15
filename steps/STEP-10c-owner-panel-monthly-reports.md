# Step 10c — Owner panel analytics and monthly reports (≈ 45 min)
Read the "Owner panel" and "Monthly reports" parts of the SPEC section.
- `/sahibkar` dashboard and per-listing analytics exactly as in SPEC (CSS bars for 30-day views, conversion %, district benchmark AZN/ha, completeness checklist). Owner can run AI analysis and open 3D for own plots.
- MonthlyReport: form `/sahibkar/layihe/{id}/hesabat/yeni` (month, work text, this month's CalendarTasks as checkboxes → DONE, spending by category with live total, workers, ≥ 2 photos, receipts), history list with status; investors of the project see reports read-only; OPERATOR review (Qəbul et / Düzəliş istə).
- Due-date logic (5th of next month) computed on read + a daily `@Scheduled` job sending reminder (1st) and overdue (6th) notifications once.
- `/idare/monitorinq`: add Mənbə filter (Dövlət / Özəl), columns "Aylıq hesabat" (Vaxtında / Gecikir N gün) and "Xərclənən / investisiya %"; extend the risk rule. Keep existing columns.
- Tests: due/overdue calculation, risk rule extension, report validation (≥ 2 photos, breakdown sums to total).
**Done when:** sahibkar@demo.az sees the overdue report warning, submits a report with tasks ticked and photos → operator accepts → task delays drop in Monitorinq; the investor of that project sees the report and spent vs funded %.
