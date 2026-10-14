# Step 2 — Map, catalog, passport, ROI + prediction, compare (≈ 85 min)
Read `docs/SPEC.md` (Pages, Fruit designation, ROI + prediction).

- `SuitabilityService` (RULE part only for now; uses seeded PlotClimate): compute and store `PlotCropSuitability` for all plots at startup and after plot/climate changes. EXPERT > AI > RULE when reading.
- `/saheler`: filters (region, district, activity, minHa, maxHa, demining, **"Hansı meyvə üçün?"** = fruit crop → score ≥ 60, sorted by score). Cards: code, name, district, area, activity + demining badges, top 3 suitability chips, competition + "N gün qalıb" (red ≤ 7), compare checkbox → `/muqayise?ids=`.
- `/xerite`: Leaflet (CDN), OSM tiles, polygons by activity, legend, popup → passport. Same filter fragment.
- `/saheler/{id}` passport: header; usage terms; "İnvestor sualları" grid (demining, water/irrigation, soil, road, electricity) with green/amber/red; **"Uyğun meyvə və bitkilər"** chips with % and source; mini map; competition card + "Müraciət et"; `?score=` badge.
- **ROI + Proqnoz** box on passport (crop select via GET `?crop=`): investment, annual net, payback; yield forecast (t/year); scenario table Pessimist / Baza / Optimist (payback + 10-year cumulative, CSS bars); risk cards Şaxta / Quraqlıq / Su çatışmazlığı with LOW/MEDIUM/HIGH; disclaimer.
- `/muqayise`: table of passport fields + top suitability; highlight best values.
- `/musabiqeler`: published competitions.
- Unit tests: RoiService (payback, scenarios), SuitabilityService (frost and elevation penalties).

**Done when:** fruit filter "Nar" returns FZL-001 but not KLB-001; FZL-001 passport shows chips, ROI, 3 scenarios and risks; map and compare work.
