# Step 8 — OPTIONAL: Co-investment shares (simulation) (≈ 35 min)
Do this only if Steps 1–7 work and there are ≥ 60 minutes left. Otherwise skip to Step 9 and show this idea on a slide.
Read `docs/SPEC.md` → "Co-investment shares".

- `ShareService`: create offering (owner of ACTIVE FarmProject, one open offering per project), buy shares (INVESTOR, not the lead, ≤ remaining, before deadline), status FUNDED when sold out. Audit + notification to lead.
- `/pay-layiheleri`: cards (plot, district, crop, target capital, % sold progress bar, price per share, deadline).
- `/pay-layiheleri/{id}`: project summary (plot passport link, crop plan, ROI base annualNet), share table (lead + holders, names shown as initials for privacy), buy form (shares → total AZN, % of project, expected annual profit share).
- "Pay təklifi yarat" button on `/kabinet/teserrufat/{id}`; "Paylarım" table on `/kabinet`.
- Banner on every share page: "Simulyasiya — real ödəniş aparılmır. Hüquqi model pilot mərhələsində dəqiqləşdiriləcək."

**Done when:** Elvin creates an offering (1 000 × 500 AZN, keeps 400); Rəşad buys 100 → 50% sold, both see correct % and expected profit share.
