# Step 5 — Competition approval, EKTİS mock, dashboard, audit, API (≈ 50 min)
Read `docs/SPEC.md` (Competition approval, EKTİS mock, REST, Pages).

- **Competitions** `/idare/musabiqeler`: table by status. Operator: create form (title, requirements, min investment, deadline, plot checkboxes from plots not in a PUBLISHED competition) → PENDING_APPROVAL. Commission: "Təsdiqlə" / "Rədd et" (comment required). Audit + notification to creator.
- **EKTİS**: `EktisClient`, `MockEktisClient`, `HttpEktisClient` (stub with TODO, active when `ektis.mode=HTTP`), `EktisService` implementing SPEC. `/idare/inteqrasiya`: header badge "Rejim: MOCK", table (time, application number, status, EKTİS ref, ms), `<details>` with pretty request/response JSON (FİN masked in UI), "Yenidən göndər" for FAILED (admin). Investor detail page shows "EKTİS ref: …".
- **Dashboard** `/idare`: KPI cards (Ümumi müraciət, Ümumi investisiya, Gözlənilən iş yerləri, Təsdiqlənmiş, Dərc edilmiş müsabiqə), status counts, table by district (applications, investment). Simple CSS bars are enough — no chart library.
- **Audit** `/idare/audit`: last 200 entries, newest first (no filters — keep it 5 minutes).
- **REST** under `/api/v1` (DTO records, no entities) + springdoc; `X-API-Key` filter for `/api/v1/external/**` (key from `app.external-api-key`).

**Done when:** approving Elvin's application creates a SUCCESS log with EKTIS ref in < 1 s; with `ektis.mock.fail=true` it is FAILED and retry works after switching back; dashboard numbers change; operator-created competition appears publicly only after commission approval; Swagger shows the API.
