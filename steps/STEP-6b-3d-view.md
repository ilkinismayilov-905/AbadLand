# Step 6b — 3D satellite view of a plot (≈ 25 min)
The 3D viewer is **already written**: `src/main/resources/static/js/plot3d.js` (copied from the kit). Do not rewrite it; only integrate it. Reference for look & behaviour: `docs/design/qarabag-agro-portal-v3.html` (only its 3D view — keep our own colors and layout everywhere else).

## What the viewer does
Esri World Imagery draped over real terrain (AWS Terrain Tiles, Terrarium PNG) with three.js r128. Full-screen overlay with: bird's-eye orbit (drag to rotate, wheel to zoom), "Sahənin içində gəz" walk mode (W A S D / arrows / on-screen pad, Shift = faster), "Gün / Qürub" lighting, plot outline + translucent walls in the activity color, floating label, elevation box (centre, min–max m), "Müraciət et" button, Esc / "Bağla" closes and frees GPU memory.

API: `Plot3D.open({ name, subtitle, color, geometry, applyUrl })` — `geometry` is the plot's GeoJSON Polygon object (lng, lat order).

## Tasks
1. **Scripts.** In `layout.html` add an optional fragment/block for page scripts. On the passport and map pages only, load in this order:
   `<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>` then `<script th:src="@{/js/plot3d.js}"></script>`.
2. **Data to the page.** Passport controller adds a small DTO for the viewer: name, subtitle (`"{district} · {area} ha · {activity label}"`, area with comma decimal), color (same activity color map as the Leaflet polygons), geometry (the stored GeoJSON string), applyUrl (only if the plot is in a PUBLISHED competition with deadline ≥ today; anonymous users get the login URL with redirect). In the template use Thymeleaf JS inlining and `JSON.parse` for the geometry string.
3. **Passport.** Extend the existing layer toggle to **"Xəritə | Peyk | 3D"**. "3D" calls `Plot3D.open(...)`; the toggle stays on the previous layer after closing.
4. **Map page.** In each polygon popup add a second link **"3D görünüş"** next to "Pasporta bax". Pass the same fields via the plots JSON already sent to the map.
5. **Catalog cards (optional, 5 min).** Small secondary button "3D" on each card.
6. **Security headers.** Spring Security adds no CSP by default — leave it. If a CSP exists, allow `script-src https://cdnjs.cloudflare.com`, `img-src https://server.arcgisonline.com https://s3.amazonaws.com data: blob:`.
7. **SPEC.** Add a short "3D view" section to `docs/SPEC.md` (sources, controls, where the button appears) and add "3D görünüş" to the passport row of the Pages table.
8. Audit nothing (read-only feature). No backend calls from the viewer — tiles come straight from Esri and AWS.

## Done when
- FZL-001 passport → "3D" → satellite terrain loads within a few seconds, white outline + green walls around the plot, label visible, orbit rotates slowly.
- LCN-001 (Laçın) shows clearly visible mountains; the elevation box shows ~1 400 m centre.
- Walk mode moves with W A S D and the on-screen pad on mobile width; Shift is faster.
- "Gün / Qürub" changes the sky; Esc and "Bağla" close it and the page scrolls normally again.
- "Müraciət et" inside 3D opens the application form (or login for anonymous users).
- Map popup "3D görünüş" works for any plot.
- Without internet the overlay shows "Peyk şəkli yüklənmədi. İnternet bağlantısını yoxlayın." and still closes cleanly.
