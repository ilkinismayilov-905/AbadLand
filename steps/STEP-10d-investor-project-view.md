# Step 10d — Investor view of funded projects: "what has the owner done, where is the process" (≈ 35 min)
Do after 10c. If `docs/SPEC.md` has no "Investor project view" part yet, append the part below to the "Private owners" section, then implement. Do not change existing features.

## Investor project view (SPEC addition)
**Access:** owner, staff, and investors with an ACCEPTED offer on the listing. Everyone else → 404.
**Entry points:** `/kabinet/investisiyalarim` (card per funded project with status badge, progress %, last report date, "Bax" button) and the notification links.

### Page `/kabinet/investisiya/{projectId}` — sections in this order
1. **Header:** listing title, plot code + district, category, owner card (name, verified badge, experience), risk badge (Plan üzrə / Risk var), simulation note.
2. **"Mənim payım":** invested amount, share % (EQUITY) or revenue share % + years (REVENUE_SHARE), date of investment, expected annual return from the plot's ROI base scenario × share, and **distributed so far** (Σ revenue × share, simulated).
3. **"Proses harada?" stepper** derived from CalendarTasks and status: Maliyyələşdi → Torpaq hazırlığı → Əkin / tikinti → Qulluq və suvarma → İlk məhsul → Gəlir bölgüsü. Current stage highlighted, done stages with ✓ and date, delayed stage red with "N gün gecikir". Overall progress % = done tasks / all tasks of the current season.
4. **Pul necə xərclənir:** funded total vs cumulative spent (bar), spending by category (CSS horizontal bars from all MonthlyReports), plan vs actual line: time-proportional plan vs spent per month (simple 12-column CSS chart).
5. **Aylıq hesabatlar timeline:** newest first; each entry: month, status (Qəbul edilib / Düzəliş tələb olunur / Gecikir), work text, tasks completed, spent, workers, photo thumbnails (click → lightbox), receipts downloadable, operator comment if any. Missing months shown as red "Hesabat təqdim edilməyib".
6. **Foto qalereya:** all report photos grouped by month (before/after feeling).
7. **Əkin təqvimi** (read-only, existing component) and **İşçi heyəti** totals with local %.
8. **Sahə:** existing satellite/3D buttons for the plot.
9. **Sual-cavab:** simple thread — investor asks, owner answers (ProjectMessage: project, author, text ≤ 1000, createdAt). Owner sees unanswered questions on `/sahibkar` dashboard. Staff can read.

### Monthly report addition
Add `revenueThisMonth` (AZN, default 0) to MonthlyReport. Investors' "distributed so far" uses accepted reports only.

### Notifications to the project's investors
New monthly report submitted · report accepted / needs fix · task becomes delayed · risk status changes · owner answered a question · monthly report overdue (6th of the month).

## Tasks
- Entities/fields: ProjectMessage, MonthlyReport.revenueThisMonth; access check helper `ProjectAccess.canView(user, project)` used by every project endpoint and file download.
- Page and components above (reuse calendar, workforce, satellite/3D; CSS bars only, no chart library); responsive at 375 / 768 / 1280.
- `/kabinet/investisiyalarim` list update; `/sahibkar` dashboard block "Cavab gözləyən suallar".
- Seed: the IN_PROGRESS private project gets Elvin and Rəşad as accepted investors, 3 monthly reports with photos (placeholder images) and one with revenue, 2 Q&A messages (one unanswered).
- Tests: access control (non-investor 404), distributed-so-far calculation, stepper stage from tasks.

## Done when
Elvin opens "İnvestisiyalarım" → the funded project shows his share, the process stepper (current stage + one red delayed stage), spent vs funded, monthly reports with photos, gallery and the missing month in red; he asks a question → sahibkar@demo.az sees it in the panel and answers → Elvin gets a notification. A user who did not invest gets 404 on the same URL.
