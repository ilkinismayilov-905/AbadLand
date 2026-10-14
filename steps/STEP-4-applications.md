# Step 4 — Application, review cycle, notifications (≈ 90 min)
Read `docs/SPEC.md` (Application state machine, Pages). This is the heart of the demo — be careful.

- `ApplicationService`: `submit`, `startReview`, `requestInfo`, `respond`, `decide`. Each validates the transition + role + ownership, writes StatusHistory, AuditLog, Notification (investor). Number generator `QAR-2026-000xxx` (counter = max existing + 1, synchronized).
- File upload: PDF/JPG/PNG only (check extension + first bytes), ≤ 5 MB, stored in `uploads/` with UUID name; download endpoint checks owner or staff role.
- **Investor**
  - `/kabinet`: profile card (edit FİN/VÖEN, experience) + table "Müraciətlərim" (number, plot, status badge, updated).
  - `/kabinet/muraciet/yeni?plotId=`: plot + competition summary, fields: planned crop (select, pre-filled from recommended), investment (show minimum), jobs, months, business plan text, business plan file (required), other file (optional), consent checkbox. Show all validation errors at once. On success → detail page with big green box "Müraciətiniz qeydə alındı: QAR-2026-000xxx".
  - `/kabinet/muraciet/{id}`: horizontal status steps (Qəbul edilib → Baxılır → Qərar verilib; amber side state for info request), history timeline (time, status, comment, role), documents, decision box. If INFO_REQUESTED: amber card with operator's message + reply textarea + file input + "Cavabı göndər".
- **Staff**
  - `/idare/muracietler`: table with status filter; FİN/VÖEN masked.
  - `/idare/muraciet/{id}`: all data, documents, history; buttons by role & status: Operator — "Baxışa başla", "Əlavə məlumat istə" (textarea); Commission — "Müsbət qərar" / "Mənfi qərar" with reason textarea (required). Use POST forms + redirect + flash.
- **Notifications**: `/bildirisler` list (mark all read on open); header badge = unread count (via `@ControllerAdvice` model attribute).
- In `decide`, leave a call `ektisService.sendDecision(app)` — create a no-op `EktisService` stub now; Step 5 implements it.

**Done when:** full loop works in 3 browsers (normal + 2 incognito): Elvin submits → operator starts review + requests info → Elvin sees badge, replies with PDF → commission approves → Elvin sees "Qərar verilib: Müsbət" and reason.
