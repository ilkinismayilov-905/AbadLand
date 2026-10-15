# Step 10a — Owner role, registration, listings, moderation (≈ 45 min)
**Do not remove or change existing features.** This adds a module.
1. If `docs/SPEC.md` has no "Private owners (Sahibkar)" section yet, append `docs/SPEC-owner-section.md` to it as a new section, and add the new role/enums/pages to the existing tables.
2. Implement from that section:
   - Role OWNER, OwnerProfile, Plot.ownership/owner/landRightDoc, InvestmentListing, ListingView (entity only for now), FarmProject.source/listing.
   - `/qeydiyyat` choice screen (two big cards with icons and one-line explanations) → existing investor form or new owner form with the fields in SPEC. Backend accepts only INVESTOR or OWNER.
   - Login redirect: OWNER → `/sahibkar`.
   - Owner listing wizard `/sahibkar/elan/yeni` (4 steps, draft autosave): Torpaq (draw polygon on Leaflet with a simple click-to-add-points tool + "Sıfırla"; or paste GeoJSON; area auto-calculated and editable), Layihə (category grouped Aqrar / Aqroturizm, description, plannedCrop or agrotourism figures, conceptLinkToAgri for agrotourism), Maliyyə (budget, own contribution, computed requested, min ticket, model + %/years, deadline, jobs, months), Sənədlər və fotolar (landRightDoc, demining certificate, ≥ 3 photos). Submit → PENDING_REVIEW.
   - Staff: `/idare/sahibkarlar` (verify / reject owners), `/idare/elanlar` (review listing: all data + map + docs + photos; Dərc et / Düzəliş istə / Rədd et with comment; agri-share warning).
   - Published private plots appear in existing `/saheler`, `/xerite` (filter "Mülkiyyət", badge "Özəl") and the passport works for them (suitability, ROI, AI, 3D).
   - Seed from SPEC (owners, plots, listings in all statuses).
3. Tests: registration cannot create staff roles; submit validation; moderation transitions.
**Done when:** register a new owner → verify as operator → create a listing with a drawn polygon and photos → operator publishes → it shows on the map and catalog with the "Özəl" badge and a working passport (AI + 3D).
