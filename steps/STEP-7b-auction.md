# Step 7b — Open ascending auctions for land lease or sale (≈ 60 min)
Do after Step 7. First **append the "Open auction" section below to `docs/SPEC.md`** (and the pages/enums/seed lines to their tables), then implement. Payments and deposits are **simulated** — no real money.

---

## Open auction (SPEC section to append)

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
Errors return Azerbaijani messages; amounts are whole AZN.

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
- `HRC-2026-02` SALE, plot `TRT-002` (Tərtər, 25 ha), SCHEDULED: starts tomorrow 11:00, startPrice 60 000, step 2 000.
- `HRC-2026-00` LEASE, CONFIRMED last week with a full protocol (start 2 000 → final 3 250, +62,5%).
- `demo` profile only, optional: `app.auction.demo-bot=true` → when Elvin bids on `HRC-2026-01`, participant #1 automatically outbids once after ~8 s (makes the live demo feel real). Mark bot bids in audit as `AUCTION_BID_DEMO_BOT`.

### Legal note (show on auction rules and say in the pitch)
"Hərrac simulyasiyadır: zaminat və ödəniş real aparılmır. Dövlət torpaqlarının satışı və icarəsi qanunvericiliyə və aidiyyəti qurumun qaydalarına uyğun aparılmalıdır; real tətbiqdə mövcud dövlət hərrac platforması ilə inteqrasiya nəzərdə tutulur."

---

## Implementation notes
- Package `auction/` (entities in `domain/` as before is fine). `AuctionService` owns all state changes; status recomputed by a `tick(now)` method used by the scheduler and on every read.
- Repository: `@Lock(LockModeType.PESSIMISTIC_WRITE)` finder for bidding.
- JS for the live panel in `static/js/auction-live.js` (vanilla, < 150 lines). Countdown red when < 60 s; flash the price when it changes; disable buttons while a request is in flight.
- EKTİS payload for results: `operation: "SEND_AUCTION_RESULT"`, include auction code, deal type, plot, start/final price, winner FİN/VÖEN, bids count.

## Done when
- Elvin opens `HRC-2026-01`, clicks "Zaminat ödənildi", bids "+1 addım" → he is highest, price and history update in both browsers within 2 s; Rəşad's window shows "Təklifiniz keçildi".
- A bid below the minimum or by the current highest bidder is rejected with a clear message.
- Bidding in the last 2 minutes extends the end time (visible in UI and protocol).
- When time runs out: status ENDED; commission confirms → protocol is public, winner notified, EKTİS log has `SEND_AUCTION_RESULT`, LEASE winner sees a new farm in "Təsərrüfatım".
- Auction with no bids becomes "Baş tutmadı" and the plot is available again.
- Unit tests: bid validation (min, self-outbid, ended, no deposit), anti-sniping extension, winner selection, FAILED with no bids.
