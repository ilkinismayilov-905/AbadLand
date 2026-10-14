# Step 1 — Skeleton, full data model, seed, login (≈ 45 min)
Read `CLAUDE.md`, `docs/SPEC.md` (Entities, Enums, Roles, Seed).

- Spring Boot project (Maven wrapper, Java 21, deps from CLAUDE.md). H2 in-memory default, `ddl-auto=create`, H2 console off. Profile `pg` for PostgreSQL via env vars. `spring.servlet.multipart.max-file-size=5MB`.
- **All** entities + enums + repositories from SPEC now (including farm, calendar, shares) — later steps only add services and pages.
- `StatusLabels` bean: Azerbaijani label + Tailwind badge classes for every enum.
- `DataSeeder` (CommandLineRunner): everything in SPEC → Seed (crops with all fields, calendar templates, ≥ 15 plots, demo PlotClimate, competitions, 8 applications with history, 2 farm projects with tasks/workforce/reports, sample AI analysis, 1 share offering).
- `SecurityConfig`: form login `/giris`, logout, BCrypt. Public: `/`, `/xerite`, `/saheler/**`, `/muqayise`, `/musabiqeler`, `/uygunluq/**`, `/pay-layiheleri` (list + detail GET), `/qeydiyyat`, static, `/api/v1/plots/**`, `/api/v1/competitions`, swagger. `/kabinet/**` INVESTOR; `/idare/**` OPERATOR/COMMISSION/ADMIN; `/api/v1/external/**` API-key filter. After login: investor → `/kabinet`, staff → `/idare`.
- `templates/layout.html` fragments: header "AgroInvest Qarabağ", nav (Xəritə, Sahələr, Müsabiqələr, Uyğunluq testi, Pay layihələri), login or user + role + notification badge + logout; staff see "İdarəetmə". Tailwind CDN, green/earth palette, flash area.
- Pages: `/` (hero + KPIs), `/giris` (demo accounts box), `/qeydiyyat` (name, email, phone, password, type, FİN or VÖEN+company, consent), placeholders `/kabinet`, `/idare`.
- `AuditService.log(action, entity, id, details)` on login and registration.

**Done when:** app starts, 5 demo users log in to the right page, registration works, investor gets 403 on `/idare`, H2 has all seed rows (log counts).
