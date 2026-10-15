## Private owners (Sahibkar) — added module

Everything that exists stays unchanged (state competitions, commission, auctions, EKTİS, investor flow). This module ADDS a second land source: plots owned or used by private entrepreneurs (sahibkar), who publish them as investment listings ("elan").

### New role
`OWNER` (label "Sahibkar" — use only this word in the UI; never "fermer"). Self-registration is allowed for `INVESTOR` and `OWNER` only — the backend ignores any other role sent by the form.

### Owner registration fields (`/qeydiyyat`, choice screen first: "İnvestoram" | "Sahibkaram (torpağım və layihəm var)")
- Ad, soyad · e-poçt · telefon · şifrə
- Şəxs növü: Fiziki şəxs | Fərdi sahibkar | Hüquqi şəxs
- FİN (fiziki / fərdi sahibkar) or VÖEN + təşkilat adı (hüquqi şəxs)
- Fəaliyyət rayonu (district select) · kənd təsərrüfatı təcrübəsi (il)
- EKTİS qeydiyyat nömrəsi (optional)
- Torpaq üzərində hüquq: Mülkiyyət | İcarə | İstifadə hüququ
- Consent checkbox (personal data + "məlumatların doğruluğuna görə məsuliyyət daşıyıram")
After registration: `ownerStatus = PENDING_VERIFICATION`; mock FİN/VÖEN check as for investors; an OPERATOR verifies the owner (→ VERIFIED / REJECTED with comment). Owners may create drafts before verification but can submit listings only when VERIFIED.

### Entities (new)
- **OwnerProfile**: user (1:1), personType, fin, voen, orgName, districtId, experienceYears, ektisRegNo, landRight (OWNERSHIP / LEASE / USE_RIGHT), status (PENDING_VERIFICATION / VERIFIED / REJECTED), verifiedBy, verifiedAt, comment
- **Plot** gets: `ownership` (STATE default / PRIVATE), `owner` (User, nullable), `landRightDoc` (Document) — private plots reuse ALL existing plot features: map, catalog, passport, suitability, ROI + prediction, satellite + AI, 3D.
- **InvestmentListing**: plot (1:1 for MVP), owner, code (`ELN-2026-0001`), title, category, description (≤ 3000), conceptLinkToAgri (TEXT, required for agrotourism), totalBudget, ownContribution, requestedAmount (= total − own, computed), minTicket, model (EQUITY "Pay iştirakı" / REVENUE_SHARE "Gəlir payı"), offeredEquityPct (EQUITY) or revenueSharePct + revenueShareYears (REVENUE_SHARE), plannedJobs, implementationMonths, plannedCrop (agri), capex/annualRevenue/annualOpex (agrotourism, manual), fundingDeadline, status, viewCount, createdAt, publishedAt, reviewComment, version
- **ListingView**: listing, viewerUser (nullable), day (LocalDate) — one row per viewer per day for analytics
- **InvestmentOffer**: listing, investor, amount, message, status (SENT / ACCEPTED / REJECTED / COUNTERED / WITHDRAWN), counterAmount, ownerComment, createdAt, decidedAt
- **MonthlyReport**: farmProject, month ("2026-11"), workDone (TEXT), tasksCompleted (CalendarTask ids), spentThisMonth, spentBreakdown (JSON: Toxum/ting, Texnika, Əmək haqqı, Suvarma, Tikinti, Gübrə/dərman, Digər), cumulativeSpent, workersCount, photos (≥ 2 Document), receipts (optional Documents), status (SUBMITTED / ACCEPTED / NEEDS_FIX), reviewerComment, submittedAt
- **FarmProject** gets: `source` (STATE_COMPETITION / PRIVATE_LISTING), `listing` (nullable).

### Categories (85–90% agricultural focus)
Agricultural (shown first, default): CROP_FARMING Əkinçilik · HORTICULTURE Bağçılıq · LIVESTOCK Heyvandarlıq · GREENHOUSE İstixana · BEEKEEPING Arıçılıq · AGRO_PROCESSING Emal və saxlama.
Agrotourism (separate group "Aqroturizm"): AGRO_RESTAURANT Restoran (təsərrüfat məhsulları ilə) · GUEST_HOUSE Qonaq evi · AGRO_HOTEL Otel / istirahət kompleksi.
Rules: agrotourism listings must fill `conceptLinkToAgri`. Admin dashboard shows "Aqrar payı" (% of published listings that are agricultural); if a new agrotourism submission would push it below 85%, the moderator sees a warning (not a hard block). Seed keeps ~88% agricultural.

### Listing lifecycle
DRAFT → PENDING_REVIEW (owner submits; owner VERIFIED; required: polygon, area, district, landRightDoc, ≥ 3 photos, budget fields, demining certificate if status ≠ CLEARED is "Təmizlənir" → cannot submit) → PUBLISHED or CHANGES_REQUESTED / REJECTED (OPERATOR, comment required) → FUNDED (accepted offers ≥ requestedAmount) → IN_PROGRESS (FarmProject created, source PRIVATE_LISTING) → CLOSED. Owner can withdraw while PUBLISHED with no accepted offers. Every change: audit + notification.

### Investment offers (simulation — no real money)
- INVESTOR (verified) sends an offer on a PUBLISHED listing: amount ≥ minTicket and ≤ remaining, message.
- OWNER: Qəbul et / Rədd et / Əks-təklif (counterAmount); investor accepts or withdraws a counter.
- Funding progress = Σ accepted / requestedAmount. FUNDED → FarmProject; investors with accepted offers get read access to the project (monthly reports, calendar, workforce).
- Investor sees their share: EQUITY → offeredEquityPct × amount / requestedAmount; REVENUE_SHARE → revenueSharePct × amount / requestedAmount for revenueShareYears.
- Banner on offer screens: "Simulyasiya — real ödəniş aparılmır. Real tətbiqdə lisenziyalı bank vasitəsilə escrow nəzərdə tutulur."

### Monthly reports (owner obligation)
- For FarmProjects with source PRIVATE_LISTING (and optionally state ones), the owner submits one report per month, **due by the 5th of the next month**.
- Form: month, işlər (text), this month's calendar tasks as checkboxes (checked → marked DONE with date), spending this month by category, workers count, ≥ 2 photos, receipts optional.
- OPERATOR reviews: Qəbul et / Düzəliş istə (comment). Investors of the project see reports read-only.
- Overdue: not submitted by the 5th → status shown "Gecikir"; notifications to the owner on the 1st (reminder) and the 6th (overdue); shown to staff.
- Monitorinq (staff) adds columns/filters: "Mənbə" (Dövlət / Özəl), "Gecikmiş aylıq hesabat", "Xərclənən / investisiya %". Existing delayed planting tasks stay; for private projects they are updated through the monthly report.
- Risk rule extension: AT_RISK also if a monthly report is > 35 days late, or cumulativeSpent < 30% of time-proportional plan after 6 months, or 2 consecutive NEEDS_FIX.

### Owner panel `/sahibkar`
- **Ümumi baxış**: verification status, listings by status, total requested vs funded, pending offers (action needed), next monthly report due date (red if overdue), delayed tasks.
- **Elan analitikası** (per listing): views last 30 days (CSS bar chart from ListingView), unique viewers, offers count, view → offer conversion %, funding progress, average requested AZN/ha of PUBLISHED listings in the same district & category (benchmark), completeness checklist (photos, docs, AI analysis run, business plan) with "Tamamla" links.
- **Torpaq analizi**: the plot's suitability chips, ROI + prediction, satellite + AI analysis (owner can run it for own plot), 3D.
- **Layihə monitorinqi** (after FUNDED): commitments, spending vs funding, calendar, workforce, monthly reports history.

### Pages (new)
| URL | Who | Content |
|---|---|---|
| `/qeydiyyat` | anon | choice screen → investor form (existing) or owner form |
| `/sahibkar` | OWNER | dashboard (above) |
| `/sahibkar/elanlar`, `/sahibkar/elan/yeni`, `/sahibkar/elan/{id}` | OWNER | listings list, 4-step create/edit wizard (Torpaq → Layihə → Maliyyə → Sənədlər və fotolar), detail with analytics and offers |
| `/sahibkar/teklifler` | OWNER | all offers with actions |
| `/sahibkar/layihe/{id}/hesabat/yeni` | OWNER | monthly report form |
| `/elanlar` | all | public listings catalog (same filter component; extra filters: kateqoriya, model, min investisiya) — also shown in `/saheler` and `/xerite` with "Mülkiyyət: Dövlət | Özəl" filter and a distinct "Özəl" badge |
| `/elanlar/{code}` | all | plot passport + owner card (verified badge, experience, district) + financials + funding bar + "İnvestisiya təklifi göndər" |
| `/kabinet/investisiyalarim` | INVESTOR | offers and funded projects with monthly reports |
| `/idare/sahibkarlar`, `/idare/elanlar` | OPERATOR, ADMIN | owner verification queue, listing moderation queue |
| `/idare/monitorinq` | staff | existing page + new columns/filters |
Nav: public "Sahibkar elanları"; owner header menu: Panel, Elanlarım, Təkliflər, Layihələrim.

### Seed (fictional)
- Owners: `sahibkar@demo.az` (Vüqar Əliyev, Hüquqi şəxs "Qacar Bağları MMC", Füzuli, VERIFIED), `sahibkar2@demo.az` (Leyla Quliyeva, Fərdi sahibkar, Laçın, VERIFIED), `yeni@demo.az` (PENDING_VERIFICATION — for the moderation demo). Password `Demo1234`.
- 8 private plots `PRV-001…008` with listings: 7 agricultural (nar bağı Füzuli, fındıq Zəngilan, istixana Ağdam, arıçılıq Kəlbəcər, maldarlıq Laçın, taxıl Tərtər, meyvə emalı Ağdam) + 1 agrotourism (qonaq evi + bağ, Laçın).
- Statuses: 4 PUBLISHED (one 70% funded), 1 PENDING_REVIEW, 1 DRAFT, 1 FUNDED→IN_PROGRESS (FarmProject with 3 monthly reports: 2 ACCEPTED, last month missing → overdue), 1 CHANGES_REQUESTED.
- ListingView rows for the last 30 days so the analytics chart is not empty; 6 offers in mixed statuses (Elvin has one SENT).
