# PROGRESS

| Step | Title | Time | Status |
|---|---|---|---|
| 1 | Skeleton, full data model, seed, login | 0:45 | ✅ |
| 2 | Map, catalog, fruit filter, passport, ROI + prediction, compare | 1:25 | ✅ |
| 3 | Matching quiz | 0:40 | ✅ |
| 4 | Application, review cycle, notifications | 1:30 | ✅ |
| 5 | Competition approval, EKTİS mock, dashboard, audit, API | 0:50 | ⬜ |
| 6 | Satellite, real climate, AI land analysis | 1:00 | ⬜ |
| 7 | Farm management + planting calendar | 0:50 | ⬜ |
| 8 | OPTIONAL: co-investment shares (simulation) | 0:35 | ⬜ |
| 9 | Polish, README, demo test | 0:30 | ⬜ |

Core (1–7 + 9) ≈ 7:30 · with Step 8 ≈ 8:05

## Log

### Step 1 — 2026-10-14
- Boot 3.5.7 + wrapper; all 21 entities + enums + repos; `StatusLabels` (AZ labels + badges); `AuditService`; `CalendarService.generate` (used by seeder, reuse in Step 7).
- `DataSeeder`: 9 users, 8 crops, 32 calendar templates, 16 plots + climate, 4 competitions (incl. closed `MUS-2025-02` holding the 2 farm plots XCL-001/XCV-001), 8 applications + history/audit/notifications/EKTİS logs, 2 farms (Rəşad ACTIVE, Tural AT_RISK with delayed tasks), sample AI for FZL-001, 1 offering (40% sold). Counts logged at startup.
- Security: `/giris` login → investor `/kabinet`, staff `/idare`; logout POST `/cixis`; `/api/v1/external/**` needs `X-API-Key`. Pages: `/`, `/giris`, `/qeydiyyat`, placeholder `/kabinet`, `/idare`, AZ 403/404.
- `Step1SmokeTest` (MockMvc): logins, 403, registration validation, seed counts. Note: seed runs ~1 s after Tomcat starts.

### Step 2 — 2026-10-14
- `SuitabilityService` (RULE + import of latest AI analysis; EXPERT > AI > RULE on read; `recompute(plot)` / `applyAi()` ready for Step 6), `RoiService` (payback, yield, 3 scenarios, 3 risks), `PlotService` (filter, open competition, days left), `Fmt` bean (`@fmt.money`).
- Pages: `/saheler` (filters + "Hansı meyvə üçün?" ≥ 60 sorted, compare checkboxes), `/xerite` (Leaflet, Xəritə/Peyk, legend, popups), `/saheler/{id}` passport (questions grid, chips, ROI box `?crop=`, `?score=` badge, mini map, competition card), `/muqayise`, `/musabiqeler`.
- SPEC change: RULE gives −20 for non-recommended crops (instead of +10, which always clamped away) and scores only crops of the plot's activity; AI `notRecommended` names → score 20. FZL-001 chips: Nar 85 · Qoz 80 · Fındıq 78 · Üzüm 72 · Alma 20.
- Thymeleaf 3.1 blocks `T(java.*)` in templates — put helpers in beans/records instead.

### Step 3 — 2026-10-14
- `MatchingService`: pure `score(plot, firstCrop, answers)` per SPEC table (6 components + ✓/✗ reasons in SPEC wording); `match()` over plots in open PUBLISHED competitions. Budget "2M+" = no limit; experience collected but not scored (SPEC has no component).
- `/uygunluq` (GET, shareable URL): 5 radio-card questions → results with score circle, top 4 reasons, deadline, "Pasport" (`?score=`), compare checkbox, "Bal necə hesablanır?". `edit=1` reopens the filled quiz.
- Demo answers → FZL-001 100, AGD-002 95, FZL-002 94, LCN-001 94. Tests: `MatchingServiceTest` (100, budget boundaries, FZL-002 reasons), `Step3QuizTest`.
- Dev: `./mvnw spring-boot:run` uses DevTools live reload (templates from `src/`, Java restart on compile).

### Step 4 — 2026-10-14
- `ApplicationService` = only place status changes: `checkSubmission` (all rule errors at once), `submit` (number `QAR-<year>-<max+1>`, synchronized), `startReview`, `requestInfo`, `respond`, `decide` (→ `EktisService.sendDecision` no-op stub, FarmProject on APPROVED). Every change → StatusHistory + AuditLog + investor Notification; operators notified on submit/reply. ADMIN may do any role's action.
- `StorageService` (PDF/JPG/PNG by extension + magic bytes, ≤ 5 MB, UUID names in `uploads/`), `/fayl/{id}` download (owner or staff, else 404). Over-limit upload → flash.
- Pages: `/kabinet` (profile edit + applications), `/kabinet/muraciet/yeni`, `/kabinet/muraciet/{id}` (steps, info-reply card, decision, docs, history), `/idare/muracietler` (+ status filter, masked FİN/VÖEN), `/idare/muracietler/{id}` (role/status buttons), `/bildirisler`. Login returns to the saved page (only `/kabinet/…`, `/idare/…`, `/pay-layiheleri`, `/bildirisler`).
- `Step4ApplicationFlowTest` runs demo steps 6–9 on its own H2 DB (`jdbc:h2:mem:step4`) — keep separate DB names in tests that write data.
