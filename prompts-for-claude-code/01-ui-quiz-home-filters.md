UI improvements — 4 parts. Read CLAUDE.md, docs/SPEC.md (Matching score) and use docs/design/agroinvest-prototip.html as the visual reference. All UI text in Azerbaijani. Do not break the demo path. Update docs/SPEC.md where matching rules change. Short plan first, then implement.

## 1. Matching quiz (/uygunluq)
a) BUG: budget option buttons render empty. Fix the cause. Options: "100 000 AZN", "250 000 AZN", "500 000 AZN", "1 000 000 AZN", "2 000 000+ AZN".
b) 6th budget option "Digər məbləğ" → number input "Məbləği yazın (AZN)" (min 10 000, step 1 000, formatted).
c) Activity "Digər" → required text input "Fəaliyyət növünü yazın" (≤ 100 chars). Scoring as ActivityType OTHER; show typed text on results.
d) Area options: "1–5 ha", "5–20 ha", "20–50 ha", "50–150 ha", "150+ ha", "Özüm yazım" (Min ha / Maks ha inputs, max ≥ min, ±20% tolerance like presets).
e) Conditional inputs expand smoothly, keep values, included in shareable URL params.
f) Responsive grid: 2 cols < 640px, 3 cols 640–1024px, 4–6 cols ≥ 1024px; buttons ≥ 56px; sticky "Nəticələri göstər" on mobile; "3 / 5 cavablandırılıb".
g) Selected = green border + light green bg + check icon; radio inputs styled as cards, visible focus ring.
h) Unit tests: OTHER activity, custom budget, custom area.

## 2. Home page — sections: hero, stats strip (real counts), "Necə işləyir", feature showcase (Uyğunluq balı, Peyk + AI, 3D, Əkin təqvimi), "Məlumat mənbələri və texnologiyalar" (text badges, no logos), "Potensial tərəfdaşlar" (text cards, note "Planlaşdırılan əməkdaşlıq istiqamətləri — rəsmi tərəfdaşlıq deyil."), FAQ (5), final CTA + richer footer. (Sections "Müraciəti tezliklə bitən sahələr", "Regionlar üzrə", "Aktiv hərraclar" were removed later — keep them removed.)

## 3. Plots catalog filters (/saheler)
Top bar: search `q`, prominent "Hansı meyvə üçün?", sort, view toggle. Activity chips. Collapsible "Ətraflı filtrlər" (Region, Rayon, Mina vəziyyəti, Sahə min–max, Yalnız müsabiqədə, Mülkiyyət Dövlət/Özəl) with active-count badge. All controls 44px, labels on one baseline (CSS grid). Active filter chips + "Hamısını təmizlə". Result count + empty state. Mobile: search + "Filtrlər (3)" → bottom sheet with "Tətbiq et" / "Sıfırla". Auto-apply, URL params. Same component on /xerite.

## 4. Global: test 375 / 768 / 1280 px, hamburger nav < 900px, consistent spacing, radius 12–14px, buttons ≥ 44px.
Done when: all of the above work, FZL-001 still first for the demo answers, ./mvnw test green, PROGRESS.md updated.
