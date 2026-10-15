# PROGRESS

| Step | Title | Time | Status |
|---|---|---|---|
| 1 | Skeleton, full data model, seed, login | 0:45 | ✅ |
| 2 | Map, catalog, fruit filter, passport, ROI + prediction, compare | 1:25 | ✅ |
| 3 | Matching quiz | 0:40 | ✅ |
| 4 | Application, review cycle, notifications | 1:30 | ✅ |
| 5 | Competition approval, EKTİS mock, dashboard, audit, API | 0:50 | ✅ |
| 6 | Satellite, real climate, AI land analysis | 1:00 | ✅ |
| 6b | 3D satellite view of a plot | 0:25 | ✅ |
| 7 | Farm management + planting calendar | 0:50 | ✅ |
| 7b | Open ascending auctions (lease / sale, simulation) | 1:00 | ✅ |
| 8 | OPTIONAL: co-investment shares (simulation) | 0:35 | ⏭ skipped (nav link hidden; seed offering kept) |
| 9 | Polish, README, demo test | 0:30 | ✅ |

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

### Step 5 — 2026-10-14
- EKTİS: `EktisClient` → `MockEktisClient` (300 ms, runtime fail flag) / `HttpEktisClient` stub by `ektis.mode`; `EktisService` builds SPEC payload, logs every call (IntegrationLog + audit `EKTIS_SEND`), sets `ektisRef`, admin `retry`. `/idare/inteqrasiya`: mode badge, masked pretty JSON, retry + mock-fail toggle (admin).
- `CompetitionService` (create → PENDING_APPROVAL, approve/reject with comment, code `MUS-<year>-NN`), `/idare/musabiqeler`. `DashboardService` + `/idare` (5 KPIs, statuses, districts with CSS bars). `/idare/audit` (C/A, last 200).
- REST `/api/v1` (DTO records): plots, plot detail with suitability, competitions, application status (owner/staff, 401 if anonymous), external decisions (`X-API-Key`). Swagger group `v1` shows only `/api/v1/**`.
- Tests: `Step5AdminEktisTest` (own DB `step5`). Step 4 test now also expects the `EKTIS_SEND` audit row.

### Step 6 — 2026-10-14
- Map: Esri "Peyk" layer with required credit; passport mini map opens on Peyk, polygon outline only. `SatelliteImageService` (Esri export bbox+15% → `uploads/satellite/{code}.jpg`, `/saheler/{id}/peyk.jpg`), `ClimateService` (Open-Meteo elevation + 2025 archive → PlotClimate, recompute RULE; admin button on `/idare`). Shared `HttpClient` bean.
- AI: `AiClient` / `ClaudeAiClient` (raw HTTP per CLAUDE.md; image first; effort low, max_tokens 4000, stop_reason checks, refusal fallback for Sonnet 5.5 — see SPEC), `AiAnalysisService` (cache per plot, 5/user/day via audit log, admin re-run, running-lock, AI suitability rows, audit `AI_ANALYSIS`). Passport: climate cards, "Peyk və AI analizi" section, ROI pre-selects AI top crop (real analyses only).
- Verified live: satellite image, Open-Meteo refresh for 16 plots, demo filters unchanged. Real Claude call NOT verified (no `ANTHROPIC_API_KEY` in this environment) — covered by unit tests on request/response shape and a fake-client flow test.
- Demo prep: admin → "İqlim məlumatını yenilə"; set key + `AI_MODEL=claude-sonnet-5-5`, run LCN-001 analysis once (cached).

### Step 6b — 2026-10-14
- Integrated the kit's `plot3d.js` (unchanged): `layout :: scripts3d` (three.js r128 + plot3d.js) on passport and `/xerite` only; `PlotController.viewerData` (name, subtitle, purpose, geoJson, applyUrl); `map.js` `open3d()` uses the Leaflet `ACTIVITY_COLORS`. Passport toggle "Xəritə | Peyk | 3D" (switch-back deferred one tick — Leaflet ignores changes during its own click); map popup "3D görünüş".
- `app.css` fixes without editing the viewer: `.p3d [hidden]` (walk pad showed in orbit mode), phone spacing for hint/pad (`.p3d.p3d` to beat the injected style).
- Verified in Chrome (playwright-cli): FZL-001 terrain + outline + label + orbit, LCN-001 mountains (centre 863 m — the SPEC polygon lies in the river valley, not ~1 400 m), walk W/Shift/pad, Gün/Qürub, Esc/Bağla restore scroll, "Müraciət et" → login → form, map popup, blocked tiles → offline message.

### Step 7 — 2026-10-14
- `FarmService`: progress (investment/jobs/time/local %) + SPEC risk rule with reasons, status saved on read; task status on read with one notification on first DELAYED; crop plan (≤ plot area → `CalendarService.generate`), mark done (date ≤ today), workforce, quarterly report (`YYYY-Qn`, ≤ 3 JPG/PNG), staff review (accept / needs fix + comment). All audited + notified. Approval now notifies "Təsərrüfat layihəniz yaradıldı".
- Pages: `/kabinet/teserrufat/{id}` (5 sections, shared `farm-fragments.html`), Kabinet "Təsərrüfatlarım", link from approved application; `/idare/monitorinq` (+ detail with satellite image, report review); dashboard +3 farm KPIs.
- Timeline = one CSS grid per task row (auto-placement mixed rows). "İcra olundu" only for tasks already started.
- Tests: `FarmProgressTest` (percentages, risk, 14-day grace), `Step7FarmTest` (demo steps 10–11 on DB `step7`).

### Step 9 — 2026-10-14
- Polish: AZ binding messages (`messages.properties`), 500 + generic error pages, all pages checked at 375 px in Chrome (farm task table now scrolls), "Pay layihələri" nav hidden until Step 8. `PlotService`/`MatchingService` read-only transactional (lazy collections outside web requests).
- `DemoPathTest`: SPEC demo steps 1–11 through services (fake AI). 40 tests green. README rewritten (run, env, accounts, pages, mermaid, PostgreSQL/EKTİS, roadmap). `spring-boot.run.profiles` is now a pom property → `-Dspring-boot.run.profiles=pg` works.
- HTTP demo walk on the running app: every step OK; slowest = approve 0.31 s (mock EKTİS 300 ms). No FİN/VÖEN/key in logs. Real Claude call still unverified here (no key).
- Note: after adding a NEW file under `src/main/resources` (e.g. messages.properties) do a full restart, not a DevTools restart (Boot caches the "file missing" check).

### Extra — Admin user management (2026-10-14)
- `/idare/istifadeciler` (ADMIN only, SecurityConfig + service check): users table (role, active, last login), "Yeni işçi" (OPERATOR/COMMISSION/ADMIN; 12-char temp password via SecureRandom, shown once as flash, never logged/audited), activate/deactivate (not self). Audit `USER_CREATED` / `USER_DISABLED` / `USER_ENABLED`.
- `User.active` + `lastLoginAt`; disabled users can't log in (`/giris?deaktiv`) and their open sessions are expired via `SessionRegistry`. `/qeydiyyat` `@InitBinder` disallows `role`, `id`, `passwordHash`, `active`.
- `UserAdminTest` (DB `useradmin`): 44 tests green. SPEC Pages + User entity updated.

### Step 7b — 2026-10-14
- SPEC "Open auction" appended (seed deviation: new plots `AGD-003`, `TRT-003` (sale, our TRT-002 is in a PUBLISHED competition), `CBR-003` (confirmed last week)). `Auction`, `AuctionParticipant`, `Bid`, `DealType`, `AuctionStatus`, `PlotStatus` (+ `Plot.status`); `FarmProject.application` optional + `auction` (`sourceLabel()`).
- `AuctionService` owns all status changes: `tick(now)` on read + `@Scheduled` 30 s; create/approve/reject/cancel/confirm (EKTİS `SEND_AUCTION_RESULT`, plot LEASED/SOLD, LEASE → FarmProject); register + simulated deposit; `placeBid` under `PESSIMISTIC_WRITE` lock (min, self-outbid, deposit, LIVE, anti-sniping +120 s, "Təklifiniz keçildi"); demo bot (`app.auction.demo-bot`, dev profile only).
- Pages `/herraclar`, `/herraclar/{id}` (live panel, `auction-live.js` polls `/api/v1/auctions/{id}/state` every 2 s, CSRF meta), `/protokol` (print CSS, winner name staff-only), `/kabinet/herraclarim`, `/idare/herraclar`; nav + staff nav; passport auction card; dashboard KPIs. 3D viewer data moved to `Viewer3dData`.
- Tests: `AuctionTest` (5, fresh DB each) + seed-count fixes → 49 green. Verified live in two Chrome sessions: deposit → +1 step → other window updates ~2 s, "Təklifiniz keçildi", demo bot answers.

### Rebrand — AgroInvest (2026-10-15)
- All user-facing "AqroInvest" → "AgroInvest" (titles, header/footer, Swagger, README/PLAN/CLAUDE/steps/SPEC). EKTİS payloads now carry `source: "AGROINVEST_QARABAG"` (SPEC updated). Kept internal: package `az.aqroinvest`, `AqroInvestApplication`, groupId/artifactId, `spring.application.name`, H2/PG DB names, `docs/pitch/AqroInvest_*.pdf` filenames.
- Header: white, sticky, `#DDE3DC` border, shadow on scroll, logo 44/36 px, active nav pill (`currentPath` from `GlobalModelAdvice`), mobile ☰ menu. Footer `#1E4A28` + white logo. Logo above login/register. Favicons + `site.webmanifest` (`/img/**` permitted). Empty states use `layout :: emptyIcon`.

### UI improvements — 2026-10-15
- Quiz: budget labels were empty — SpEL `budgets[b]` treats a bare identifier as the string key `'b'`; labels now come from ordered maps in `MatchingController`. New: "Digər məbləğ", "Digər" + text (OTHER), "1–5 ha" and "Özüm yazım" (min/max), all validated in `Answers.invalid()`, all in the shareable URL. Radio-card options with ✓, smooth reveal, "N / 5 cavablandırılıb", sticky submit on mobile (`quiz.js`). SPEC updated.
- Home: sections from one `DashboardService.home()` call (hero with a sample plot, stats, how it works, features, sources, potential partners with the "rəsmi tərəfdaşlıq deyil" note, FAQ, CTA); richer footer. Later removed: closing-soon plots, region overview, auction teaser (alternating white/gray bands now).
- Catalog/map filter: one `layout :: plotFilter(action, side)` component — search `q`, fruit, sort, Kart/Xəritə toggle, activity chips, "Ətraflı filtrlər" with badge, removable active chips, auto-apply (`filters.js`), mobile bottom sheet; `/xerite` uses the sidebar version. Header collapses below 900 px, shows the user's name; shared `.card/.field/.btn-*` scale (14 px radius, 44 px controls).
- Tests: OTHER/custom budget/custom range + page tests → 54 green. Checked 375/768/1280 px: no horizontal scroll; demo answers still put FZL-001 first.

### Deploy (Render) — 2026-10-15
- Multi-stage `Dockerfile` (maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre, non-root `app`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`), `.dockerignore`, `server.port=${PORT:8080}`. Docker stays optional for local runs.
- Verified locally: `docker run --memory=512m -e PORT=10000` → Tomcat on 10000, all main pages 200, max heap 384 MB, ~400 MiB used after start. README "Deploy (Render)" added.
- Memory tuning: `-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k`, Tomcat `threads.max=20`, `accept-count=50`. In-container walk (`--memory=512m`, PORT=10000): Elvin login → FZL-001 passport → AI (fake key → 401 handled, satellite image fetched) → 3D → application QAR-2026-000008 → operator review → commission approve (EKTİS ref). docker stats: 377 MiB after start → 412 MiB (80 %) after the walk; cgroup peak 419 MiB, heap max 308 MB.

### Languages az / en / ru — 2026-10-15
- `I18n` (static `t()`, `stored()`/`render()` for notifications, `js()` dictionary for static JS), `WebConfig` (MessageSource from `I18n.BASENAMES`, `lang` cookie, `?lang=`), header switcher AZ | EN | RU, `<html lang>` per language. Enum labels + crop names in `i18n/enums*`; pages in `i18n/{common,plots,apply,staff,auction}*` (~1 000 keys × 3). Notifications stored as key + args (crop names via `I18n.keyArg`).
- Kept Azerbaijani on purpose: seed data, user text, audit details, EKTİS payloads, AI analysis text, Swagger annotations. CLAUDE.md rule 3 and SPEC "Languages" updated.
- Tests 56 green (+`I18nTest`). Crawled every page as anon/investor/farm owner/operator/commission/admin in en+ru: no missing keys, no 500; 375 px sweep in ru clean (passport columns got `min-w-0`).

### Rebrand — AbadLand (2026-10-15)
- Brand "AgroInvest" → "AbadLand" (common.brand az/en/ru, manifest, Swagger, docs, EKTİS source `ABADLAND_QARABAG`). Logo PNGs: only the top word redrawn ("AbadLand", Avenir Next Bold, same color #35693C / white, same cap height); icon and "Qarabağ" pixels unchanged, canvas 640→679 px wide. File names `agroinvest-*.png` and the Render URL kept.

### AI prompt v2 wired in — 2026-10-15
- New `prompts/land-analysis.txt` (stricter rules, risk levels, observations, on-site checks) now matches the code: `AiView` reads `risks` as objects **or** legacy strings, plus `observations` / `recommendedChecks`; passport shows level badges and the new sections (az/en/ru).
- Satellite image: square Web Mercator extent + yellow plot outline (`SatelliteImageService.withOutline`), cache `{code}-v2.jpg`. User text built from `prompts/land-analysis-user.txt` incl. the crop requirement table. `max_tokens` 8000, timeout 120 s. SPEC updated.
- Tests 60 green (+`AiViewAndOutlineTest`; Step6 fake AI uses the new shape). Outline checked on real Esri images (FZL-001, LCN-001). No real Claude call made with the new prompt yet.
- Task 07 (AI prompt upgrade) completed: `AiResultValidator` (keys, scores 0–100, LOW|MEDIUM|HIGH, unknown crop code → null) + one retry with the "Cavab etibarlı JSON deyildi…" note, then FAILED with a polite message; 4 px outline; "Sahədə yoxlayın". Not applied on purpose: `temperature 0.2` (400 on Sonnet 5.x/Haiku 5.5) and `max_tokens 1500` (truncates with thinking) — kept no temperature / 8000. Tests 65 green (+`AiResultValidatorTest`, fake client, no network).

### Owner module reverted; staff plot listings — 2026-10-15
- Reverted to `4cc54e5` (before Step 10a); owner module kept only in branch `sahibkar-modul`; Render startup fix (CDS + C1 JIT) cherry-picked; owner docs removed.
- Staff listings: `listing/` (`PlotListingService`, `Geo`, `Districts`), `/idare/elanlar` list (filters, Aqrar payı KPI, unpublish guards) and the 4-step wizard `/idare/elan/...` (polygon drawing reused from the backup branch, autosave, photos with cover + reorder, ANAMA certificate). Only ADMIN publishes: climate + RULE suitability, optional add to a pending competition or auction form.
- Drafts hidden from catalog / map / API / pickers; catalog cover photo + "Yeni" badge, passport gallery. Seed plots get a category and stay PUBLISHED. i18n bundle `listing` (az/en/ru).
- Tests 67 green (+`PlotListingTest`). Browser: operator draws 24,7 ha in Füzuli → FZL-003 draft with 3 photos → admin publishes into MUS-2026-05 → catalog card with cover + "Yeni", passport with photos, live climate, ROI, AI, 3D → commission approves → unpublish disabled with reason.
