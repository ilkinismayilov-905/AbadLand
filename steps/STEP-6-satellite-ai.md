# Step 6 — Satellite imagery, real climate, AI land analysis (≈ 60 min)
Read `docs/SPEC.md` → "Satellite & AI analysis", "Fruit designation", "ROI + prediction". The API key is **server-side only**.

## A. Satellite view (10 min)
Layer switcher "Xəritə" (OSM) / "Peyk" (`https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`, attribution "Tiles © Esri — Esri, Maxar, Earthstar Geographics") on `/xerite` and passport; passport defaults to Peyk, fits polygon, outline only.

## B. Real data (15 min)
- `SatelliteImageService`: Esri export (SPEC URL) → `uploads/satellite/{plotCode}.jpg`, cached; served at `/saheler/{id}/peyk.jpg`.
- `ClimateService`: Open-Meteo elevation + archive (SPEC URLs) → update `PlotClimate` (elevation, mean temp, abs min, frost days, precip). Admin button "İqlim məlumatını yenilə" for all plots. After update → recompute RULE suitability. `java.net.http.HttpClient`, 10 s timeouts; failures keep seeded values.
- Passport: 4 climate cards (Hündürlük, Orta temperatur, Şaxtalı günlər, İllik yağıntı) with "Mənbə: Open-Meteo".

## C. AI (30 min)
- `AiClient` + `ClaudeAiClient` exactly as SPEC (image block first, then text; Jackson; strip ``` fences). Prompt file `prompts/land-analysis.txt`.
- `AiAnalysisService.analyze(plot)`: image + climate + call → save `AiAnalysis`; write AI rows into `PlotCropSuitability` (source AI) for codes that exist; audit `AI_ANALYSIS`. Limits: cached per plot, 5 per user per day, admin re-run.
- Passport section **"Peyk və AI analizi"**: image + credit, button "AI ilə analiz et" (logged in; text "Analiz edilir… ~15 san"), result: summary, land cover, terrain, water, risks, suitable crops bars, not recommended, irrigation advice, confidence, data gaps, disclaimer. No key → button disabled + "AI açarı konfiqurasiya edilməyib". ROI crop select pre-selects AI top crop.
- Sample analysis for FZL-001 shown as "Nümunə analiz" if no real one.

**Done when:** LCN-001 and FZL-001 show satellite image, real climate cards and an AI analysis; suitability chips show "AI" source afterwards; second click is instant; without the key the page still works.
