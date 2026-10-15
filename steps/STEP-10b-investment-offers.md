# Step 10b — Public listings and investment offers (≈ 35 min)
Read the "Private owners" section in `docs/SPEC.md`.
- `/elanlar` catalog (reuse the plot filter component; extra filters kateqoriya, model, min investisiya; agricultural first) and `/elanlar/{code}` page: plot passport blocks + owner card + financials + funding progress bar + "İnvestisiya təklifi göndər" (modal: amount with min/remaining hints, computed share %, message, simulation banner).
- Record ListingView (one per viewer per day; anonymous by session id hash).
- InvestmentOffer flow: owner `/sahibkar/teklifler` (Qəbul et / Rədd et / Əks-təklif), investor `/kabinet/investisiyalarim` (accept counter / withdraw). Notifications both ways; audit.
- FUNDED when accepted ≥ requested → create FarmProject (source PRIVATE_LISTING, owner as manager, accepted investors get read access) → listing IN_PROGRESS.
- Home stats strip and admin dashboard: add "Özəl elanlar", "Toplanmış investisiya (özəl)", "Aqrar payı %". Do not change other sections.
- Tests: offer bounds, counter flow, funding threshold creates FarmProject once.
**Done when:** Elvin sends an offer → owner (sahibkar@demo.az) accepts → progress bar updates; filling the remaining amount moves the listing to FUNDED and a project appears for both owner and investors.
