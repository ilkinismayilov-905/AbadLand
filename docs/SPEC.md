# SPEC — single source of truth (final MVP)

## Demo path (must always work)
1. Elvin (investor) takes the 5-question quiz → `FZL-001` ranks first with reasons.
2. Catalog filter "Hansı meyvə üçün?" = Nar → only plots suitable for pomegranate.
3. `FZL-001` passport → demining, water, road; ROI + **prediction** (3 scenarios, yield, risks) for Fındıq.
4. `LCN-001` (Laçın, Şərqi Zəngəzur) passport → satellite view → AI analysis (crops, risks).
5. Compare `FZL-001` and `FZL-002`.
6. Apply (form + business plan PDF) → number `QAR-2026-000xxx`.
7. Operator: "Baxışa başla" → "Əlavə məlumat istə" (torpaq analizi).
8. Elvin: notification → uploads PDF + reply.
9. Commission: "Müsbət qərar" with reason → EKTİS mock log SUCCESS; dashboard changes.
10. Elvin → "Təsərrüfatım": crop plan (Fındıq 40 ha) → **planting calendar** generated; adds workers.
11. Operator → Monitorinq: promised vs actual jobs, calendar delays, risk badge.
12. (Optional, Step 8) Elvin opens a **share offering**; Rəşad buys shares (simulation).

## Roles & demo users (password `Demo1234`)
| Role | Email | Can |
|---|---|---|
| INVESTOR | elvin@demo.az, rashad@demo.az, + 4 more | browse, quiz, apply, track, respond, manage farm, offer/buy shares |
| OPERATOR | operator@demo.az | review applications, request info, create competitions, monitor farms |
| COMMISSION | komissiya@demo.az | approve competitions, decide applications |
| ADMIN | admin@demo.az | everything + audit + integration log + re-run AI |

## Enums
- `Role`: INVESTOR, OPERATOR, COMMISSION, ADMIN
- `EconomicRegion`: QARABAG (Qarabağ), SHERQI_ZANGEZUR (Şərqi Zəngəzur)
- `ActivityType`: CROP_FARMING (Əkinçilik), LIVESTOCK (Heyvandarlıq), HORTICULTURE (Bağçılıq), OTHER (Digər)
- `DeminingStatus`: CLEARED (Tam təmizlənib), PARTIALLY_CLEARED (Qismən təmizlənib), IN_PROGRESS (Təmizlənir)
- `CompetitionStatus`: PENDING_APPROVAL (Təsdiq gözləyir), PUBLISHED (Dərc edilib), REJECTED (Rədd edilib), CLOSED (Bağlanıb)
- `ApplicationStatus`: SUBMITTED (Qəbul edilib), UNDER_REVIEW (Baxılır), INFO_REQUESTED (Əlavə məlumat tələb olunur), DECIDED (Qərar verilib)
- `Decision`: APPROVED (Müsbət), REJECTED (Mənfi)
- `DocType`: BUSINESS_PLAN (Biznes plan), SOIL_ANALYSIS (Torpaq analizi), IDENTITY (Şəxsiyyət sənədi), OTHER (Digər)
- `WaterNeed`: LOW (Az), MEDIUM (Orta), HIGH (Çox)
- `SuitabilitySource`: RULE (Qayda), AI (AI), EXPERT (Ekspert)
- `RiskLevel`: LOW (Aşağı), MEDIUM (Orta), HIGH (Yüksək)
- `WorkRole`: AGRONOMIST (Aqronom), VET (Baytar), MACHINE_OPERATOR (Texnika operatoru), WORKER (Fəhlə), ACCOUNTANT (Mühasib), OTHER (Digər)
- `CalendarStage`: SOIL_PREP (Torpaq hazırlığı), PLANTING (Əkin), IRRIGATION (Suvarma mövsümü), FERTILIZING (Gübrələmə), PRUNING (Budama / qulluq), HARVEST (Yığım)
- `TaskStatus`: PLANNED (Planlaşdırılıb), DONE (İcra olunub), DELAYED (Gecikir)
- `FarmStatus`: ACTIVE (Aktiv), AT_RISK (Risk var), COMPLETED (Tamamlanıb)
- `ReportStatus`: SUBMITTED (Göndərilib), ACCEPTED (Qəbul edilib), NEEDS_FIX (Düzəliş tələb olunur)
- `OfferingStatus`: OPEN (Açıq), FUNDED (Toplandı), CLOSED (Bağlandı)

## Entities (JPA, Long ids are fine; application uses public `number`)
- **User**: email, passwordHash, fullName, phone, role, investorType (INDIVIDUAL/LEGAL), fin, voen, companyName, experienceYears
- **Crop**: code, nameAz, activityType, isFruit, investmentPerHa, revenuePerHa, opexPerHa, firstHarvestYear, yieldTPerHa, frostToleranceC, maxElevationM, waterNeed
- **CropCalendarTemplate**: crop, stage, startMonth, endMonth, yearOffset (0 = planting year; HARVEST uses firstHarvestYear − 1)
- **Plot**: code, name, district (String), economicRegion, village, areaHa, purpose, demining, waterSource (String), irrigation, soilType, roadKm, electricity, leaseYears, feePerHa, recommendedCrops (comma-separated codes), description, geoJson (TEXT, Polygon), lat, lng
- **PlotClimate**: plot (1:1), elevationM, meanTempC, absMinTempC, frostDays, precipMm, fetchedAt
- **PlotCropSuitability**: plot, crop, score 0–100, source, reason
- **AiAnalysis**: plot, model, createdAt, resultJson (TEXT), status, error
- **Competition**: code, title, requirements, minInvestment, deadline (LocalDate), status, createdBy, approvedBy, comment, plots (ManyToMany)
- **Application**: number, investor, competition, plot, status, plannedCrop, investmentAzn, jobs, months, businessPlan (TEXT ≤ 3000), decision, decisionReason, infoRequest (TEXT), infoResponse (TEXT), ektisRef, createdAt, updatedAt
- **Document**: application (nullable), report (nullable), type, originalName, storedName, size, uploadedAt
- **StatusHistory**: application, fromStatus, toStatus, comment, actorName, actorRole, at
- **Notification**: user, message, link, read, at
- **AuditLog**: actorEmail, actorRole, action, entity, entityId, details, ip, at
- **IntegrationLog**: applicationNumber, operation, requestJson, responseJson, status (SUCCESS/FAILED), ektisRef, durationMs, at
- **FarmProject**: application (1:1), plot, investor, status, startDate, committedInvestment, committedJobs, committedMonths
- **CropPlanItem**: project, crop, areaHa, seasonYear
- **CalendarTask**: project, cropPlanItem, stage, plannedStart, plannedEnd, actualDate, status, note
- **WorkforceItem**: project, role, count, local (bool)
- **ProgressReport**: project, period ("2026-Q4"), investedToDate, note, status, reviewerComment, submittedAt (+ Document photos)
- **ShareOffering** (Step 8): project, leadInvestor, totalShares, pricePerShare, leadShares, deadline, status, createdAt
- **ShareHolding** (Step 8): offering, investor, shares, createdAt

## Application state machine
| From → To | Who | Rule |
|---|---|---|
| (new) → SUBMITTED | INVESTOR | competition PUBLISHED and deadline ≥ today; plot in competition; investment ≥ minInvestment; business plan file; FIN (individual) or VÖEN (legal). Number = `QAR-<year>-<6-digit counter>` |
| SUBMITTED → UNDER_REVIEW | OPERATOR | — |
| UNDER_REVIEW → INFO_REQUESTED | OPERATOR | message required |
| INFO_REQUESTED → UNDER_REVIEW | INVESTOR (owner) | response required, file optional |
| UNDER_REVIEW → DECIDED | COMMISSION | decision + reason (≥ 10 chars); call EKTİS; if APPROVED create FarmProject |
Invalid transition → error flash, nothing changes. Every change → StatusHistory + AuditLog + Notification.

## Competition approval (TZ "təsdiq mexanizmi")
Operator creates (title, requirements, min investment, deadline, plot checkboxes) → PENDING_APPROVAL → commission Approve (→ PUBLISHED) or Reject with comment (→ REJECTED). Only PUBLISHED visible to investors.

## Matching score (0–100)
`estInvestment = areaHa × investmentPerHa(first recommended crop)`
| Component | Max | Rule |
|---|---|---|
| Activity | 30 | plot.purpose == chosen → 30, else 0 |
| Budget | 25 | est ≤ budget → 25; ≤ 1.25×budget → 12; else 0 |
| Size | 20 | in range → 20; within 20% → 10; else 0 |
| Demining | 10 | CLEARED 10, PARTIALLY 3, IN_PROGRESS 0 |
| Infrastructure | 10 | irrigation 5 + electricity 3 + road ≤ 5 km 2 (≤ 15 km 1) |
| Region | 5 | no preference or district matches → 5 |
Reasons (✓/✗): "Büdcənizə sığır (təxmini 382 500 AZN)", "Büdcəni ~15% aşır", "Suvarma var" / "Suvarma yoxdur", "Yola 12 km məsafə", "Minalardan tam təmizlənib" / "Qismən təmizlənib — əlavə yoxlama lazımdır", "Təyinat uyğun deyil: Heyvandarlıq". Candidates: plots in PUBLISHED competitions.
Quiz: activity (4), budget (100k / 250k / 500k / 1M / 2M+), size (5–20 / 20–50 / 50–150 / 150+ ha), district ("Fərqi yoxdur" + list), experience (0 / 1–3 / 3–10 / 10+).

## Fruit / crop designation ("Torpaq təyinatı — meyvələr")
- Each plot has a suitability score per crop (`PlotCropSuitability`). Priority of sources: EXPERT > AI > RULE.
- **RULE score** (needs PlotClimate; if missing, crops in `recommendedCrops` get 70, others no score):
  start 100; if absMinTempC < frostToleranceC → −40; if elevationM > maxElevationM → −30; if waterNeed HIGH and no irrigation → −30 (MEDIUM → −15); if precipMm < 300 and no irrigation → −15; if crop NOT in recommendedCrops → −20 (a "+10 bonus" would always be clamped away); clamp 0–100. Reason text lists the penalties in Azerbaijani. Only crops whose activityType equals plot.purpose are scored.
- **AI score**: from AI analysis `suitableCrops[].code` → overrides RULE for that crop; `notRecommended[].name` matching a crop nameAz → AI score 20.
- UI: catalog/map filter "Hansı meyvə üçün?" (fruit crops) → plots with score ≥ 60, sorted by score; passport shows chips "Nar 85% · Fındıq 78% · Üzüm 64%" with source label.

## ROI + prediction
- investment = area × investmentPerHa; base annualNet = area × (revenuePerHa − opexPerHa − feePerHa); payback = (firstHarvestYear − 1) + investment / annualNet (1 decimal; "—" if ≤ 0).
- **Yield forecast**: expected tons/year = area × yieldTPerHa × (suitability/100, default 0.8).
- **Scenarios**: Pessimist = revenue × 0.7 and first harvest +1 year; Baza = as is; Optimist = revenue × 1.2. Show payback and 10-year cumulative net for each (small table + CSS bars).
- **Risk forecast** from PlotClimate + crop: Şaxta (HIGH if absMin < frostTolerance, MEDIUM if within 3 °C, else LOW); Quraqlıq (no irrigation and precip < 300 → HIGH, < 500 → MEDIUM); Su çatışmazlığı (waterNeed HIGH and no irrigation → HIGH).
- Always show: "Proqnoz sınaq parametrlərinə və açıq iqlim məlumatına əsaslanır — Nazirlik ekspertləri ilə dəqiqləşdiriləcək."

## Satellite & AI analysis
Inputs: (1) Esri World Imagery export JPEG 1024×1024 of polygon bbox + 15%; (2) plot facts; (3) PlotClimate (Open-Meteo); (4) allowed crop codes.
- Esri export: `https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export?bbox={minLng},{minLat},{maxLng},{maxLat}&bboxSR=4326&imageSR=3857&size=1024,1024&format=jpg&f=image`
- Open-Meteo elevation: `https://api.open-meteo.com/v1/elevation?latitude={lat}&longitude={lng}`; archive: `https://archive-api.open-meteo.com/v1/archive?latitude={lat}&longitude={lng}&start_date=2025-01-01&end_date=2025-12-31&daily=temperature_2m_mean,temperature_2m_min,precipitation_sum&timezone=Asia%2FBaku`
- Claude API: POST `https://api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`; model from `ai.model` (default `claude-haiku-5-5` for development, set `claude-sonnet-5-5` for the demo); `max_tokens` 4000 with `output_config.effort: "low"` (current models think by default and thinking counts toward `max_tokens`, so 1500 could truncate the JSON); no `temperature`; for `claude-sonnet-5-5` also `fallbacks: "default"` + header `anthropic-beta: server-side-fallback-2026-07-01` (refusal fallback; Haiku 5.5 has none); check `stop_reason` (`refusal` / `max_tokens` → error) before reading text blocks; request timeout 90 s (connect 10 s) because image analysis takes 10–30 s; image block first, then text.
- System prompt → `src/main/resources/prompts/land-analysis.txt`:
```
You are an agronomy assistant for the South Caucasus (Azerbaijan, Karabakh and East Zangezur).
You receive a satellite image of one agricultural plot and structured data about it.
Use the image only for what it can show: land cover, vegetation, bare soil, terrain/slope cues,
nearby water bodies, roads, settlements, forest edges. Do NOT claim soil chemistry, mines or
ownership from the image. Base crop suitability mainly on elevation, temperature, frost days,
precipitation, water/irrigation and soil type given in the data. Be conservative and practical.
Answer ONLY with valid JSON (no markdown) in Azerbaijani for all text fields, matching:
{"summary": string, "landCover": string, "terrain": string, "waterSigns": string,
 "risks": [string], "suitableCrops": [{"code": string|null, "name": string, "score": 0-100, "reason": string}],
 "notRecommended": [{"name": string, "reason": string}], "irrigationAdvice": string,
 "confidence": "LOW"|"MEDIUM"|"HIGH", "dataGaps": [string]}
```
- User text: plot code/name/district/region/area/purpose, lat/lng, demining, water, irrigation, soil, road, electricity, climate values, allowed crop codes with names.
- Disclaimer (always): "AI tövsiyəsidir: peyk görüntüsü və açıq iqlim məlumatlarına əsaslanır, torpaq analizi və aqronom rəyini əvəz etmir." Image credit: "Peyk görüntüsü: Esri World Imagery".
- PlotClimate.fetchedAt = null means seeded test value (UI: "Sınaq dəyəri"); set when Open-Meteo data is stored (admin "İqlim məlumatını yenilə" on `/idare`, or automatically before an AI analysis). `app.external.enabled=false` disables Esri/Open-Meteo calls (tests/offline).
- Cost control: one analysis per plot (cached); only logged-in users can trigger; max 5 per user per day; admin can re-run. Without `ANTHROPIC_API_KEY` the button is disabled, everything else works.

## Farm project (post-lease)
- Created on APPROVED. Commitments copied from the application.
- Progress: investment% = latest investedToDate / committed; jobs% = Σ workforce / committedJobs; time% = months elapsed / committedMonths; local% = local / total.
- Risk: AT_RISK if (time% ≥ 25 and (investment% < time% × 0.5 or jobs% < time% × 0.5)) or no report in 100 days or any calendar task DELAYED > 30 days; else ACTIVE (badge "Plan üzrə").

## Planting calendar ("Əkin tarixlərinin izlənməsi")
- Adding a CropPlanItem generates CalendarTasks from CropCalendarTemplate for `seasonYear` (+ yearOffset).
- Status computed on read: DONE if actualDate set; DELAYED if today > plannedEnd + 14 days and no actualDate; else PLANNED.
- Investor marks a task done (actual date + optional note). When a task becomes DELAYED → one notification to investor and it shows in operator Monitorinq.
- UI: per crop a horizontal 12-month timeline (CSS grid: planned bar, ✓ done marker, red delayed), plus a task list.
- Seed templates (test values): HAZELNUT — SOIL_PREP Sep–Oct (0), PLANTING Nov (0), IRRIGATION May–Sep (1), PRUNING Feb (1), HARVEST Aug–Sep (firstHarvestYear−1). POMEGRANATE — SOIL_PREP Oct (0), PLANTING Nov–Dec (0), IRRIGATION May–Sep (1), PRUNING Feb–Mar (1), HARVEST Oct (2). GRAPE — PLANTING Mar–Apr (0), IRRIGATION May–Aug (0), PRUNING Feb (1), HARVEST Sep (2). WHEAT — SOIL_PREP Sep (0), PLANTING Oct (0), FERTILIZING Mar (1), HARVEST Jun–Jul (1). APPLE, WALNUT — similar to POMEGRANATE with harvest Sep–Oct.

## Co-investment shares ("Şərik işləmək — pay kimi") — Step 8, simulation only
- Lead investor (owner of an ACTIVE FarmProject) creates an offering: totalShares, pricePerShare, leadShares (their own), deadline. Capital target = totalShares × price.
- Other investors "buy" shares (no payment) up to remaining shares; when sold = total → FUNDED.
- Holder view: shares, % of project, expected annual profit share = base annualNet × %.
- Every page shows a banner: "Simulyasiya — real ödəniş aparılmır. Hüquqi model pilot mərhələsində dəqiqləşdiriləcək."
- Audit every purchase; notify the lead investor.

## EKTİS mock
`interface EktisClient { EktisResult send(String payloadJson); }` · `MockEktisClient` (`ektis.mode=MOCK`, default): 300 ms delay, returns `{"status":"ACCEPTED","ektisReference":"EKTIS-INV-2026-xxxxxx"}`; `ektis.mock.fail=true` → FAILED (retry demo); admin can also flip this flag at runtime on `/idare/inteqrasiya` (a restart would reset the H2 demo data). A FAILED send does not undo the decision; admin "Yenidən göndər" re-sends while the application has no `ektisRef`. Payload schemaVersion 1.0: application number, decision, reason, investment, jobs, investor type + FIN/VÖEN, plot code/district/area/purpose, competition code. `HttpEktisClient` stub for `ektis.mode=HTTP`.

## Pages
| URL | Who | Content |
|---|---|---|
| `/` | all | hero + CTA "Sizə uyğun torpağı tapın" + KPIs |
| `/xerite` | all | Leaflet map (Xəritə/Peyk), polygons by activity, filters incl. "Hansı meyvə üçün?" |
| `/saheler` | all | catalog cards + filters + fruit filter + "N gün qalıb" + compare checkboxes |
| `/saheler/{id}` | all | passport, crop suitability chips, ROI + prediction, satellite + AI, competition card |
| `/muqayise?ids=` | all | 2–3 plots side by side |
| `/musabiqeler` | all | published competitions |
| `/uygunluq` | all | quiz → results with score, reasons, breakdown |
| `/giris`, `/qeydiyyat` | anon | login (demo hints), register |
| `/kabinet` | INVESTOR | profile + my applications + my farms (+ my shares) |
| `/kabinet/muraciet/yeni?plotId=` | INVESTOR | application form + upload |
| `/kabinet/muraciet/{id}` | owner | status steps, history, docs, info request reply, decision, EKTİS ref |
| `/kabinet/teserrufat/{id}` | owner | commitments, crop plan, planting calendar, workforce, reports (+ create share offering) |
| `/pay-layiheleri`, `/pay-layiheleri/{id}` | all / INVESTOR buys | offerings list, detail, buy form (Step 8) |
| `/bildirisler` | logged in | notifications |
| `/idare` | staff | dashboard |
| `/idare/muracietler` + `/{id}` | O, C, A | list + detail with actions |
| `/idare/musabiqeler` | O, C, A | create (O), approve/reject (C) |
| `/idare/monitorinq` + `/{id}` | O, C, A | farm projects, risk, calendar delays, report review |
| `/idare/inteqrasiya` | O, C, A | EKTİS log + retry (A) |
| `/idare/audit` | C, A | audit table |

## REST (Swagger at `/swagger-ui.html`)
`GET /api/v1/plots`, `GET /api/v1/plots/{code}` (incl. suitability), `GET /api/v1/competitions`, `GET /api/v1/applications/{number}/status` (auth), `GET /api/v1/external/decisions` (header `X-API-Key`).

## Seed (DataSeeder, fictional)
- Crops (placeholders — investment/revenue/opex per ha, first harvest year, yield t/ha, frost tolerance °C, max elevation m, water need):
  HAZELNUT Fındıq fruit 9000/7000/2500/4, 2.0, −20, 1500, MEDIUM · POMEGRANATE Nar fruit 10000/9000/3500/3, 15, −12, 1000, MEDIUM · GRAPE Üzüm fruit 12000/10000/4000/3, 10, −18, 1200, MEDIUM · APPLE Alma fruit 25000/18000/7000/3, 30, −30, 2000, HIGH · WALNUT Qoz fruit 8000/6000/2000/5, 2.5, −25, 1800, MEDIUM · WHEAT Buğda 1200/1400/700/1, 3.5, −20, 2000, LOW · BARLEY Arpa 1000/1100/600/1, 3.0, −22, 2400, LOW · BEEF_CATTLE Maldarlıq 3000/2200/1000/2, 0, −30, 2800, MEDIUM.
- Plots (≥ 15) incl.: `FZL-001` Füzuli HORTICULTURE 42.5 ha CLEARED irrigation yes electricity yes road 3.2 km crops HAZELNUT,POMEGRANATE polygon ~47.135–47.144 / 39.598–39.604 · `FZL-002` Füzuli HORTICULTURE 35 ha CLEARED irrigation NO road 12 km · `CBR-001` Cəbrayıl HORTICULTURE 60 ha PARTIALLY_CLEARED · `LCN-001` Laçın HORTICULTURE 30 ha CLEARED river, irrigation yes, road 6 km, ~46.53–46.54 / 39.62–39.627 · `KLB-001` Kəlbəcər LIVESTOCK 150 ha CLEARED · `ZNG-001` Zəngilan HORTICULTURE 45 ha PARTIALLY_CLEARED · plus Ağdam, Tərtər, Şuşa, Qubadlı, Xocavənd plots.
- PlotClimate for demo plots seeded with plausible values (so suitability works offline); real fetch replaces them.
- Competitions: `MUS-2026-03` PUBLISHED (FZL-001, FZL-002, CBR-001, LCN-001, ZNG-001; today+12), `MUS-2026-04` PUBLISHED (Ağdam/Tərtər; today+5), `MUS-2026-05` PENDING_APPROVAL.
- 8 applications from other investors in mixed statuses (none for Elvin, none on FZL-001); 2 FarmProjects for approved ones (one ACTIVE with 2 reports and calendar on time, one AT_RISK with a delayed task); 1 sample AiAnalysis for `FZL-001` (model "sample", label "Nümunə analiz"); 1 open ShareOffering by rashad@demo.az (40% sold).
