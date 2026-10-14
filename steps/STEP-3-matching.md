# Step 3 — Matching quiz (≈ 40 min)
Read `docs/SPEC.md` (Matching score).

- `MatchingService.match(answers)` → list of `{plot, score, components[], reasons[]}` sorted desc; implement exactly the table in SPEC. Unit tests: perfect plot = 100, budget boundary, FZL-002 has reason "Yola 12 km məsafə" and "Suvarma yoxdur".
- `/uygunluq`: one page, 5 questions as large radio cards (fəaliyyət, büdcə, ölçü, rayon, təcrübə), "Nəticələri göstər". POST (or GET with params so results are shareable) → results page.
- Results: card per plot with a big score circle ("94% uyğun", green ≥ 80, amber 60–79, grey < 60), up to 4 reasons with ✓/✗, deadline, buttons "Pasport" (`/saheler/{id}?score=94`) and compare checkbox. `<details>` "Bal necə hesablanır?" showing each component points / max.
- Home hero CTA links to `/uygunluq`.

**Done when:** answers Bağçılıq / 500k / 20–50 ha / Füzuli / 3–10 → FZL-001 first with ≥ 90 and correct reasons.
