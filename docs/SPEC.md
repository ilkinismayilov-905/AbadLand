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
- `DealType`: LEASE (İcarə), SALE (Satış) · `AuctionStatus`: PENDING_APPROVAL, SCHEDULED, LIVE, ENDED, CONFIRMED, FAILED, REJECTED, CANCELLED · `PlotStatus`: AVAILABLE (Boşdur), IN_AUCTION (Hərracda), LEASED (İcarəyə verilib), SOLD (Satılıb)

## Entities (JPA, Long ids are fine; application uses public `number`)
- **User**: email, passwordHash, fullName, phone, role, investorType (INDIVIDUAL/LEGAL), fin, voen, companyName, experienceYears, active (default true; false → login refused with "Hesabınız deaktiv edilib", open sessions expired), lastLoginAt. `/qeydiyyat` always creates INVESTOR (role/active never bound from the request); staff accounts only via `/idare/istifadeciler`.
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
| Activity | 30 | plot.purpose == chosen → 30, else 0 ("Digər" + typed text = OTHER: only OTHER plots get 30) |
| Budget | 25 | est ≤ budget → 25; ≤ 1.25×budget → 12; else 0 (custom amount is used as the budget) |
| Size | 20 | in range → 20; within 20% → 10; else 0 (custom min–max range scored the same way) |
| Demining | 10 | CLEARED 10, PARTIALLY 3, IN_PROGRESS 0 |
| Infrastructure | 10 | irrigation 5 + electricity 3 + road ≤ 5 km 2 (≤ 15 km 1) |
| Region | 5 | no preference or district matches → 5 |
Reasons (✓/✗): "Büdcənizə sığır (təxmini 382 500 AZN)", "Büdcəni ~15% aşır", "Suvarma var" / "Suvarma yoxdur", "Yola 12 km məsafə", "Minalardan tam təmizlənib" / "Qismən təmizlənib — əlavə yoxlama lazımdır", "Təyinat uyğun deyil: Heyvandarlıq". Candidates: plots in PUBLISHED competitions.
Quiz: activity (4; "Digər" requires "Fəaliyyət növünü yazın", ≤ 100 chars), budget (100k / 250k / 500k / 1M / 2M+ / "Digər məbləğ" = typed amount ≥ 10 000 AZN, multiple of 1 000), size (1–5 / 5–20 / 20–50 / 50–150 / 150+ ha / "Özüm yazım" = min ≥ 0,5 and max ≥ min), district ("Fərqi yoxdur" + list), experience (0 / 1–3 / 3–10 / 10+). URL params: `activity, budget (0 = custom), budgetCustom, otherText, size (custom), minHa, maxHa, district, experience`. Results show "Seçiminiz: Digər — arıçılıq · …".

## Catalog / map filter
Params (all optional, GET, shareable): `q` (name / code / village / district, case-insensitive), `fruit`, `activity`, `region`, `district`, `demining`, `minHa`, `maxHa`, `open=true` (only plots in an open competition), `sort` = `deadline` | `area-asc` | `area-desc` | `score` (empty: by code, or by fruit score when `fruit` is set). Desktop auto-applies on change; mobile (< 768 px) uses a bottom sheet with "Tətbiq et" / "Sıfırla". Active filters are shown as removable chips.

## Fruit / crop designation ("Torpaq təyinatı — meyvələr")
- Each plot has a suitability score per crop (`PlotCropSuitability`). Priority of sources: EXPERT > AI > RULE.
- **RULE score** (needs PlotClimate; if missing, crops in `recommendedCrops` get 70, others no score):
  start 100; if absMinTempC < frostToleranceC → −40; if elevationM > maxElevationM → −30; if waterNeed HIGH and no irrigation → −30 (MEDIUM → −15); if precipMm < 300 and no irrigation → −15; if crop NOT in recommendedCrops → −20 (a "+10 bonus" would always be clamped away); clamp 0–100. Reason text lists the penalties in the UI language. Only crops whose activityType equals plot.purpose are scored.
- **AI score**: from AI analysis `suitableCrops[].code` → overrides RULE for that crop; `notRecommended[].name` matching a crop nameAz → AI score 20.
- UI: catalog/map filter "Hansı meyvə üçün?" (fruit crops) → plots with score ≥ 60, sorted by score; passport shows chips "Nar 85% · Fındıq 78% · Üzüm 64%" with source label.

## ROI + prediction
- investment = area × investmentPerHa; base annualNet = area × (revenuePerHa − opexPerHa − feePerHa); payback = (firstHarvestYear − 1) + investment / annualNet (1 decimal; "—" if ≤ 0).
- **Yield forecast**: expected tons/year = area × yieldTPerHa × (suitability/100, default 0.8).
- **Scenarios**: Pessimist = revenue × 0.7 and first harvest +1 year; Baza = as is; Optimist = revenue × 1.2. Show payback and 10-year cumulative net for each (small table + CSS bars).
- **Risk forecast** from PlotClimate + crop: Şaxta (HIGH if absMin < frostTolerance, MEDIUM if within 3 °C, else LOW); Quraqlıq (no irrigation and precip < 300 → HIGH, < 500 → MEDIUM); Su çatışmazlığı (waterNeed HIGH and no irrigation → HIGH).
- Always show: "Proqnoz sınaq parametrlərinə və açıq iqlim məlumatına əsaslanır — Nazirlik ekspertləri ilə dəqiqləşdiriləcək."

## Satellite & AI analysis
Inputs: (1) Esri World Imagery JPEG 1024×1024 of a **square** Web Mercator extent (polygon bbox, longer side + 15 % per side) with the plot boundary drawn as a **4 px yellow outline** (dark halo); (2) plot facts; (3) PlotClimate (Open-Meteo); (4) allowed crops with requirements (code | name | frost tolerance °C | max elevation m | water need).
- Esri export: `https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export?bbox={minX},{minY},{maxX},{maxY}&bboxSR=3857&imageSR=3857&size=1024,1024&format=jpg&f=image` (square extent → no re-fitting, so the outline lands on the right pixels). Cached as `uploads/satellite/{code}-v2.jpg`; the passport shows the same image.
- Open-Meteo elevation: `https://api.open-meteo.com/v1/elevation?latitude={lat}&longitude={lng}`; archive: `https://archive-api.open-meteo.com/v1/archive?latitude={lat}&longitude={lng}&start_date=2025-01-01&end_date=2025-12-31&daily=temperature_2m_mean,temperature_2m_min,precipitation_sum&timezone=Asia%2FBaku`
- Claude API: POST `https://api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`; model from `ai.model` (`AI_MODEL`, default `claude-haiku-5-5`; `claude-sonnet-5-5` for the demo); `max_tokens` 8000 with `output_config.effort: "low"` (thinking counts toward `max_tokens`; the richer JSON needs room); no `temperature`; for `claude-sonnet-5-5` also `fallbacks: "default"` + header `anthropic-beta: server-side-fallback-2026-07-01`; check `stop_reason` (`refusal` / `max_tokens` → error) before reading text blocks; request timeout 120 s (connect 10 s), a live analysis takes ~20–60 s; image block first, then text.
- System prompt → `src/main/resources/prompts/land-analysis.txt` (advisory only; hard rules: nothing about mines from the image — only the official demining status, no soil chemistry/ownership/prices as fact, no invented numbers). Output JSON: `summary, landCover, terrain, waterSigns, observations[], suitableCrops[{code|null,name,score,reason}], notRecommended[{name,reason}], risks[{title, level LOW|MEDIUM|HIGH, reason}], irrigationAdvice, recommendedChecks[], confidence LOW|MEDIUM|HIGH, dataGaps[]`. The parser (`AiView`) also accepts the older `risks: [string]` (seed sample, cached analyses) and ignores unknown fields.
- User text → `src/main/resources/prompts/land-analysis-user.txt` with `{placeholders}`, always in Azerbaijani (enum values as `NAME (AZ label)`); missing values → "məlum deyil"; seeded climate is marked "sınaq dəyərləri, real ölçü deyil"; without an image the outline sentence is replaced by "Peyk şəkli YOXDUR — …".
- Validation (`AiResultValidator`) before storing: all 12 keys present, `summary` not empty, scores integers 0–100, `confidence` and every risk `level` in LOW|MEDIUM|HIGH; a crop code outside the allowed list becomes `null` (kept by name, no catalog score). Invalid → exactly **one** retry with "Cavab etibarlı JSON deyildi. Yalnız sxemə uyğun JSON qaytar." appended to the user text; still invalid → FAILED + polite message. HTTP errors, refusals and timeouts are not retried (no extra billable call). Valid AI crops are still written to `PlotCropSuitability` (source AI).
- Deliberate deviations from the upgrade brief: **no `temperature`** (Sonnet 5.x / Haiku 5.5 return 400 for non-default sampling parameters) and **`max_tokens` 8000 instead of 1500** (thinking counts toward `max_tokens`; 1500 would truncate the JSON). Billing is for tokens actually used.
- Passport shows: summary, land cover, terrain, water, confidence, observations, suitable / not recommended crops, risks with level badges, irrigation advice, "Sahədə yoxlayın" checklist, data gaps, the disclaimer and the outlined image.
- Disclaimer (always): "AI tövsiyəsidir: peyk görüntüsü və açıq iqlim məlumatlarına əsaslanır, torpaq analizi və aqronom rəyini əvəz etmir." Image credit: "Peyk görüntüsü: Esri World Imagery".
- PlotClimate.fetchedAt = null means seeded test value (UI: "Sınaq dəyəri"); set when Open-Meteo data is stored (admin "İqlim məlumatını yenilə" on `/idare`, or automatically before an AI analysis). `app.external.enabled=false` disables Esri/Open-Meteo calls (tests/offline).
- Cost control: one analysis per plot (cached); only logged-in users can trigger; max 5 per user per day; admin can re-run. Without `ANTHROPIC_API_KEY` the button is disabled, everything else works.

## 3D view (Step 6b)
- Viewer `static/js/plot3d.js` (three.js r128 from cdnjs, loaded only on the passport and `/xerite`): Esri World Imagery tiles draped on AWS Terrain Tiles (Terrarium PNG), fetched by the browser directly — no backend calls, nothing stored.
- Opens from the passport layer toggle "Xəritə | Peyk | 3D" (toggle returns to the previous layer) and from the map popup link "3D görünüş".
- Controls: drag to orbit, wheel to zoom; "Sahənin içində gəz" = walk (W A S D / arrows / on-screen pad, Shift = faster); "Gün / Qürub"; Esc or "Bağla" closes and frees GPU memory. Shows plot outline + translucent walls in the activity colour, label, elevation box (centre, min–max m), credits.
- "Müraciət et" inside the viewer only when the plot is in an open PUBLISHED competition and the viewer is not staff; anonymous users go through login back to the form.
- Offline: "Peyk şəkli yüklənmədi. İnternet bağlantısını yoxlayın."; it still closes cleanly.

## Open auction (Step 7b)

Purpose: besides the investment competition (commission evaluates business plans), the state can lease or sell a plot through a transparent **open ascending auction** ("açıq artırma"): the highest valid bid at the end wins.

### Entities
- **Auction**: code (`HRC-2026-01`), plot, dealType (`LEASE` İcarə / `SALE` Satış), startPrice, step (min increment), deposit (zaminat, simulated), leaseYears (LEASE only), rules (TEXT), startsAt, endsAt, extendSeconds (default 120), status, currentPrice, winner (User, nullable), finalPrice, createdBy, approvedBy, confirmedBy, createdAt, version
- **AuctionParticipant**: auction, user, number (1..n, shown as "İştirakçı #3"), depositConfirmed (bool, simulated), registeredAt — unique (auction, user)
- **Bid**: auction, participant, amount, createdAt

### Enums
- `DealType`: LEASE (İcarə — illik icarə haqqı), SALE (Satış — ümumi qiymət)
- `AuctionStatus`: PENDING_APPROVAL (Təsdiq gözləyir), SCHEDULED (Planlaşdırılıb), LIVE (Gedir), ENDED (Başa çatıb), CONFIRMED (Nəticə təsdiqlənib), FAILED (Baş tutmadı), REJECTED (Rədd edilib), CANCELLED (Ləğv edilib)

### Price meaning
- LEASE: bid = **annual rent for the whole plot, AZN/year**; UI also shows AZN/ha/year.
- SALE: bid = **total price, AZN**. SALE auctions are created only when `app.auction.sale-enabled=true` (default true in demo; legal model to be confirmed — see note).

### Lifecycle
| From → To | Who / when | Rule |
|---|---|---|
| (new) → PENDING_APPROVAL | OPERATOR | plot not in a PUBLISHED competition or another open auction; startPrice > 0; step > 0; startsAt < endsAt |
| PENDING_APPROVAL → SCHEDULED / REJECTED | COMMISSION | reject needs comment |
| SCHEDULED → LIVE | system, now ≥ startsAt | computed on read + `@Scheduled` every 30 s |
| LIVE → ENDED | system, now ≥ endsAt and ≥ 1 bid | winner = highest bid |
| LIVE → FAILED | system, now ≥ endsAt and no bids | "Baş tutmadı" |
| ENDED → CONFIRMED | COMMISSION | signs the protocol; winner notified; EKTİS `SEND_AUCTION_RESULT`; LEASE → FarmProject for the winner (commitments: investment 0, jobs 0, months = leaseYears × 12 — investor edits later) |
| SCHEDULED → CANCELLED | OPERATOR | comment required |
Plot status: SCHEDULED/LIVE → `IN_AUCTION`, CONFIRMED → `LEASED` or `SOLD`, FAILED/CANCELLED → `AVAILABLE`.

### Participation
- INVESTOR with verified FİN/VÖEN registers before `endsAt`: accepts rules (checkbox) + "Zaminat ödənildi" (simulated button, sets depositConfirmed). Gets the next participant number.
- Staff users and the plot creator cannot bid.

### Bidding rules (`AuctionService.placeBid`, one transaction, pessimistic lock on the auction row)
1. Auction LIVE and now < endsAt.
2. Bidder is a participant with depositConfirmed.
3. amount ≥ (currentPrice == null ? startPrice : currentPrice + step).
4. Bidder is not already the highest bidder ("Ən yüksək təklif artıq sizindir").
5. **Anti-sniping:** if endsAt − now < extendSeconds → endsAt = now + extendSeconds (UI shows "Vaxt 2 dəqiqə uzadıldı").
6. Save Bid, update currentPrice, audit `AUCTION_BID`, notify the previously highest bidder "Təklifiniz keçildi".
Errors return messages in the request language (default Azerbaijani); amounts are whole AZN.

### Live page data (polling, no websockets)
`GET /api/v1/auctions/{id}/state` (public) → `{status, startPrice, currentPrice, step, minNextBid, endsAt, serverNow, participants, bids:[{participantNo, amount, at}] (last 20, newest first), me:{participantNo, depositConfirmed, isHighest} | null}`. Page polls every 2 s, countdown uses `serverNow` offset. Bids from the page: `POST /herraclar/{id}/teklif` via fetch with CSRF header (token from `<meta name="_csrf">`).

### Transparency
- Anyone can watch a LIVE auction read-only; bidders are shown only as "İştirakçı #n".
- **Protocol** `/herraclar/{id}/protokol` (public after CONFIRMED, staff always): plot, deal type, start price, final price, uplift %, participants count, bid count, full bid list (participant numbers + times), extensions, winner (participant number; full name visible to staff only), confirmed by/at. Printable (CSS `@media print`).

### Pages
| URL | Who | Content |
|---|---|---|
| `/herraclar` | all | cards: plot, deal type badge, start / current price, status, countdown or start time; filter by status and deal type |
| `/herraclar/{id}` | all (bid: participants) | plot summary + passport link + 3D button, rules, **live panel**: big current price, "Növbəti minimum təklif", countdown, quick buttons (+1, +2, +5 step), custom amount, bid history, participants count; register / deposit buttons |
| `/herraclar/{id}/protokol` | all after CONFIRMED | protocol |
| `/kabinet/herraclarim` | INVESTOR | my auctions: status, my last bid, highest? won? |
| `/idare/herraclar` | O, C, A | list; create form (O); approve / reject (C); confirm result (C); cancel (O) |
Nav: add "Hərraclar" to the public/investor menu; staff sidebar "Hərraclar". Passport: if the plot has an auction, show an auction card with status and link. Dashboard: KPIs "Aktiv hərrac", "Hərraclarda qiymət artımı (orta %)".

### Seed
- `HRC-2026-01` LEASE, plot `AGD-003` (add: Ağdam, CROP_FARMING, 70 ha, CLEARED), startPrice 3 500 AZN/year, step 250, deposit 1 000, leaseYears 15, LIVE: startsAt = now − 10 min, endsAt = now + 25 min; participants: rashad (#1), 2 other demo investors (#2, #3) with 4 bids up to 4 500; Elvin **registered but deposit not confirmed** (so the demo shows the deposit step).
- `HRC-2026-02` SALE, plot `TRT-003` (add: Tərtər, CROP_FARMING, 25 ha — our `TRT-002` is 55 ha and in the PUBLISHED `MUS-2026-04`, which the create rule forbids), SCHEDULED: starts tomorrow 11:00, startPrice 60 000, step 2 000.
- `HRC-2026-00` LEASE, plot `CBR-003` (add: Cəbrayıl, CROP_FARMING, 40 ha), CONFIRMED last week with a full protocol (start 2 000 → final 3 250, +62,5%), winner kamran@demo.az with a FarmProject.
- Demo bot (`app.auction.demo-bot=true`, set in `application-dev.properties` = `./mvnw spring-boot:run`; off in tests/prod) → when Elvin bids on `HRC-2026-01`, participant #1 automatically outbids once after ~8 s (makes the live demo feel real). Mark bot bids in audit as `AUCTION_BID_DEMO_BOT`.

### Legal note (show on auction rules and say in the pitch)
"Hərrac simulyasiyadır: zaminat və ödəniş real aparılmır. Dövlət torpaqlarının satışı və icarəsi qanunvericiliyə və aidiyyəti qurumun qaydalarına uyğun aparılmalıdır; real tətbiqdə mövcud dövlət hərrac platforması ilə inteqrasiya nəzərdə tutulur."
- Implementation notes: "verified FİN/VÖEN" = the investor profile has a FİN (individual) or VÖEN (legal) — no real verification in the MVP. `Plot.status` (`PlotStatus`: AVAILABLE, IN_AUCTION, LEASED, SOLD) added. `FarmProject.application` is optional; an auction-won farm has `auction` set instead.


## Plot listings (staff; all land is state land)
- OPERATOR and ADMIN create and edit drafts; only ADMIN publishes / unpublishes. Every action is audited (`LISTING_*`). Status only changes in `PlotListingService`.
- `Plot` fields: `category` (`PlotCategory`), `listingStatus` (`ListingStatus` DRAFT / PUBLISHED / UNPUBLISHED; seed plots PUBLISHED), `createdBy`, `createdAt`, `publishedAt`, `conceptLinkToAgri`, `estimatedInvestment`, `expectedJobs`; the title is `Plot.name`. Photos / certificate / PDFs are `Document`s with `plot` + `sortOrder` (first photo = cover). `DocType` + PHOTO, DEMINING_CERT.
- `PlotCategory` → `ActivityType` (for matching): Əkinçilik CROP_FARMING → CROP_FARMING; Bağçılıq → HORTICULTURE; Heyvandarlıq, Arıçılıq → LIVESTOCK; İstixana, Emal və saxlama, Restoran, Qonaq evi, Otel / istirahət kompleksi → OTHER. Aqroturizm = RESTAURANT, GUEST_HOUSE, HOTEL. Recommended crops are required for Əkinçilik, Bağçılıq, İstixana (the crop list has no bees / processing items — deviation from "all agricultural"); agrotourism requires "Kənd təsərrüfatı ilə əlaqə".
- Wizard: 1 Torpaq (Leaflet Xəritə/Peyk, click points, "Sıfırla", paste GeoJSON, area auto-calculated and editable; region → district → village) · 2 Təyinat (title, category, description ≤ 3000, crops, concept) · 3 Sahə məlumatları və şərtlər (demining + ANAMA certificate when not CLEARED, water, irrigation, soil, road, electricity, lease years, fee AZN/ha, optional investment and jobs) · 4 Sənədlər və fotolar (≥ 3 JPG/PNG ≤ 5 MB, ≤ 10, reorder, optional PDFs) + summary, "Qaralama kimi saxla", "Dərc et" (A) with "Dərcdən sonra": Yalnız kataloqda / Müsabiqəyə əlavə et (a PENDING_APPROVAL competition; the commission then approves it as usual) / Hərrac qaralaması yarat (opens the auction form with the plot preselected).
- Rules (rejected on save, all errors at once): area 0.5–5 000 ha; polygon parsed as GeoJSON Polygon inside Azerbaijan (44.5–51 E, 38.2–42 N), 0.1–5 000 ha; road 0–200 km; lease 1–49 years; fee ≥ 0; title 10–120 chars and not generic ("Elan", "Test", digits…). Warnings (never block): entered vs drawn area differ > 20 %; polygon centre outside the district box (`listing/Districts`). Publish also needs category, description, crops / concept as above, demining not IN_PROGRESS, certificate when not CLEARED, ≥ 3 photos.
- Codes: district prefix + next number (`FZL-013`); prefixes in `listing/Districts`.
- Publishing: listingStatus PUBLISHED, plot AVAILABLE, `publishedAt`; Open-Meteo climate fetched (offline → RULE suitability without climate). Only PUBLISHED plots appear in catalog, map, API, competition / auction plot pickers; drafts' passports are 404 for non-staff. "Yeni" badge for 7 days; cover photo on catalog cards; photo strip on the passport.
- Unpublish is refused when the plot is in a PUBLISHED competition, has an auction in PENDING_APPROVAL / SCHEDULED / LIVE / ENDED, or an APPROVED application. Published listings must be unpublished before editing.
- "Aqrar payı" = published non-agrotourism plots / published plots; warning below 85 %.

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
`interface EktisClient { EktisResult send(String payloadJson); }` · `MockEktisClient` (`ektis.mode=MOCK`, default): 300 ms delay, returns `{"status":"ACCEPTED","ektisReference":"EKTIS-INV-2026-xxxxxx"}`; `ektis.mock.fail=true` → FAILED (retry demo); admin can also flip this flag at runtime on `/idare/inteqrasiya` (a restart would reset the H2 demo data). A FAILED send does not undo the decision; admin "Yenidən göndər" re-sends while the application has no `ektisRef`. Payload schemaVersion 1.0: `source` = "ABADLAND_QARABAG", application number, decision, reason, investment, jobs, investor type + FIN/VÖEN, plot code/district/area/purpose, competition code. `HttpEktisClient` stub for `ektis.mode=HTTP`.

## Pages
| URL | Who | Content |
|---|---|---|
| `/` | all | hero + CTA "Sizə uyğun torpağı tapın" + KPIs |
| `/xerite` | all | Leaflet map (Xəritə/Peyk), polygons by activity, filters incl. "Hansı meyvə üçün?" |
| `/saheler` | all | catalog cards + filters + fruit filter + "N gün qalıb" + compare checkboxes |
| `/saheler/{id}` | all | passport, crop suitability chips, ROI + prediction, satellite + AI, competition card, **3D görünüş** (layer toggle "Xəritə \| Peyk \| 3D") |
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
| `/idare/elanlar` | O, A | staff plot listings: table + filters, "Aqrar payı" KPI, Bax / Redaktə / Dərc et (A) / Dərcdən çıxar (A) — see "Plot listings" |
| `/idare/elan/yeni`, `/idare/elan/{id}`, `/idare/elan/{id}/addim/{1-4}` | O, A | 4-step listing wizard (draft autosave) |
| `/saheler/{id}/foto/{docId}` | all (published plot) / staff (draft) | listing photo |
| `/idare/monitorinq` + `/{id}` | O, C, A | farm projects, risk, calendar delays, report review |
| `/idare/inteqrasiya` | O, C, A | EKTİS log + retry (A) |
| `/idare/audit` | C, A | audit table |
| `/herraclar`, `/herraclar/{id}`, `/herraclar/{id}/protokol` | all (bid: participants) | open auctions — see "Open auction" |
| `/kabinet/herraclarim` | INVESTOR | my auctions |
| `/idare/herraclar` | O, C, A | create (O), approve / reject / confirm (C), cancel (O) |
| `/idare/istifadeciler` | A | users table (name, email, role, active, last login); "Yeni işçi" (OPERATOR / COMMISSION / ADMIN only, temporary password generated and shown once); activate / deactivate (not self). Audit `USER_CREATED`, `USER_DISABLED`, `USER_ENABLED`. |

### Installed app (PWA) — bottom tab bar
- Manifest `site.webmanifest` (`display: standalone`, `start_url: /`); no service worker.
- Only when installed on an Android phone held upright (CSS `display-mode: standalone`, ≤ 767 px, portrait, coarse pointer): fixed bottom tabs **Ana səhifə `/` · Xəritə `/xerite` · Sahələr `/saheler` (+ `/muqayise`) · Müsabiqələr `/musabiqeler` · account tab**: "Daxil ol" → `/giris` (anon), "Kabinet" → `/kabinet` (INVESTOR) or `/idare` (staff); also active on `/bildirisler`. Quiz and auctions stay in the ☰ menu.
- Browser tabs, desktop (also desktop-installed windows), landscape, print and iOS home-screen apps: unchanged. Header, ☰ menu, language switch and 🔔 stay as they are.

## REST (Swagger at `/swagger-ui.html`)
`GET /api/v1/plots`, `GET /api/v1/plots/{code}` (incl. suitability), `GET /api/v1/competitions`, `GET /api/v1/applications/{number}/status` (auth), `GET /api/v1/external/decisions` (header `X-API-Key`).

## Seed (DataSeeder, fictional)
- Crops (placeholders — investment/revenue/opex per ha, first harvest year, yield t/ha, frost tolerance °C, max elevation m, water need):
  HAZELNUT Fındıq fruit 9000/7000/2500/4, 2.0, −20, 1500, MEDIUM · POMEGRANATE Nar fruit 10000/9000/3500/3, 15, −12, 1000, MEDIUM · GRAPE Üzüm fruit 12000/10000/4000/3, 10, −18, 1200, MEDIUM · APPLE Alma fruit 25000/18000/7000/3, 30, −30, 2000, HIGH · WALNUT Qoz fruit 8000/6000/2000/5, 2.5, −25, 1800, MEDIUM · WHEAT Buğda 1200/1400/700/1, 3.5, −20, 2000, LOW · BARLEY Arpa 1000/1100/600/1, 3.0, −22, 2400, LOW · BEEF_CATTLE Maldarlıq 3000/2200/1000/2, 0, −30, 2800, MEDIUM.
- Plots (≥ 15) incl.: `FZL-001` Füzuli HORTICULTURE 42.5 ha CLEARED irrigation yes electricity yes road 3.2 km crops HAZELNUT,POMEGRANATE polygon ~47.135–47.144 / 39.598–39.604 · `FZL-002` Füzuli HORTICULTURE 35 ha CLEARED irrigation NO road 12 km · `CBR-001` Cəbrayıl HORTICULTURE 60 ha PARTIALLY_CLEARED · `LCN-001` Laçın HORTICULTURE 30 ha CLEARED river, irrigation yes, road 6 km, ~46.53–46.54 / 39.62–39.627 · `KLB-001` Kəlbəcər LIVESTOCK 150 ha CLEARED · `ZNG-001` Zəngilan HORTICULTURE 45 ha PARTIALLY_CLEARED · plus Ağdam, Tərtər, Şuşa, Qubadlı, Xocavənd plots.
- PlotClimate for demo plots seeded with plausible values (so suitability works offline); real fetch replaces them.
- Competitions: `MUS-2026-03` PUBLISHED (FZL-001, FZL-002, CBR-001, LCN-001, ZNG-001; today+12), `MUS-2026-04` PUBLISHED (Ağdam/Tərtər; today+5), `MUS-2026-05` PENDING_APPROVAL.
- 8 applications from other investors in mixed statuses (none for Elvin, none on FZL-001); 2 FarmProjects for approved ones (one ACTIVE with 2 reports and calendar on time, one AT_RISK with a delayed task); 1 sample AiAnalysis for `FZL-001` (model "sample", label "Nümunə analiz"); 1 open ShareOffering by rashad@demo.az (40% sold).

## Languages (i18n)
- UI languages: **az** (default), **en**, **ru**. Header switcher AZ | EN | RU → `?lang=xx` on the current URL; stored in the `lang` cookie (1 year); unsupported values are ignored.
- Bundles: `messages*.properties` (binding errors) + `i18n/{enums,common,plots,apply,staff,auction}*.properties`; enum labels `enum.<Type>.<NAME>`, crop names `crop.<CODE>`. Missing key → AZ text (base bundle), never the server locale. az / en / ru files of every bundle have the same keys (`I18nTest`).
- Notifications are stored as a key + args and rendered in the reader's language. Seed data (plot names, descriptions, competition titles), user-entered text, audit details, EKTİS payloads and AI analysis text stay Azerbaijani.
