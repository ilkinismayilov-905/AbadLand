# Step 7 — After the lease: farm management + planting calendar (≈ 50 min)
Read `docs/SPEC.md` → "Farm project", "Planting calendar".

- `ApplicationService.decide` APPROVED → create FarmProject; notify "Təsərrüfat layihəniz yaradıldı".
- `CalendarService`: generate CalendarTasks from templates when a CropPlanItem is added; compute status on read; on first DELAYED → notification to investor.
- **Investor `/kabinet/teserrufat/{id}`**:
  1. **Öhdəliklər** — bars: investment, jobs, time, local %; risk badge.
  2. **Əkin planı** — table + form (crop, area, season year); total area ≤ plot area; first row pre-filled.
  3. **Əkin təqvimi** — per crop a 12-month CSS timeline (planned bars, ✓ done, red delayed) + task list with "İcra olundu" (date + note).
  4. **İşçi heyəti** — table + form (role, count, local); totals vs committed.
  5. **Rüblük hesabat** — form (period, invested to date, note, ≤ 3 photos) + history with status.
- **Staff `/idare/monitorinq`**: table (number, investor, plot, district, investment %, jobs %, local %, delayed tasks, last report, risk); detail read-only + satellite image + reports with "Qəbul et" / "Düzəliş istə" (comment). Audit + notifications.
- Dashboard: +KPIs "Aktiv təsərrüfat layihəsi", "Faktiki iş yerləri / vəd edilən", "Gecikən əkin tapşırıqları".

**Done when:** after approval Elvin adds Fındıq 40 ha → calendar tasks appear; marks soil prep done; adds workers + report; operator sees percentages, delays and risk; the seeded AT_RISK project shows a red delayed task.
