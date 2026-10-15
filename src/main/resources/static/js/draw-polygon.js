// Staff listing wizard: click on the map to add polygon points, or paste GeoJSON. Writes the hidden geoJson input and the area (ha).
(function () {
  "use strict";
  const el = document.getElementById("draw-map");
  if (!el) return;
  const geoInput = document.getElementById("geoJson");
  const areaInput = document.getElementById("areaHa");
  const paste = document.getElementById("geoPaste");
  const info = document.getElementById("draw-info");
  const map = createMap("draw-map", true);
  map.setView([39.75, 46.75], 9);
  let pts = [], shape = null, markers = [];
  let areaTouched = !!areaInput.value && !geoInput.value;
  areaInput.addEventListener("input", () => (areaTouched = true));

  // Equirectangular shoelace — same formula as the server (Geo.areaHa)
  function areaHa(latlngs) {
    if (latlngs.length < 3) return 0;
    const lat0 = latlngs.reduce((s, p) => s + p[0], 0) / latlngs.length;
    const kx = 111320 * Math.cos(lat0 * Math.PI / 180), ky = 110540;
    let sum = 0;
    for (let i = 0; i < latlngs.length; i++) {
      const a = latlngs[i], b = latlngs[(i + 1) % latlngs.length];
      sum += a[1] * kx * b[0] * ky - b[1] * kx * a[0] * ky;
    }
    return Math.abs(sum) / 2 / 10000;
  }

  function render(fit) {
    if (shape) map.removeLayer(shape);
    markers.forEach((m) => map.removeLayer(m));
    markers = pts.map((p) => L.circleMarker(p, { radius: 4, color: "#facc15", weight: 2, fillOpacity: 1 }).addTo(map));
    shape = pts.length >= 3 ? L.polygon(pts, { color: "#facc15", weight: 3, fillOpacity: 0.15 }).addTo(map)
      : pts.length === 2 ? L.polyline(pts, { color: "#facc15", weight: 3 }).addTo(map) : null;
    if (pts.length >= 3) {
      const ring = pts.map((p) => [Number(p[1].toFixed(6)), Number(p[0].toFixed(6))]);
      ring.push(ring[0]);
      geoInput.value = JSON.stringify({ type: "Polygon", coordinates: [ring] });
      const ha = areaHa(pts);
      if (!areaTouched) areaInput.value = ha.toFixed(1);
      info.textContent = window.t("js.listing.points", "Nöqtə") + ": " + pts.length + " · ≈ " + ha.toFixed(1) + " ha";
      if (fit) map.fitBounds(shape.getBounds(), { padding: [30, 30], maxZoom: 17 }); // Esri imagery has no tiles beyond ~17 in the region
    } else {
      geoInput.value = "";
      info.textContent = window.t("js.listing.drawHint", "Xəritəyə klikləyib ən azı 3 nöqtə qoyun.");
    }
  }

  function load(geo) {
    try {
      let g = typeof geo === "string" ? JSON.parse(geo) : geo;
      if (g.type === "Feature") g = g.geometry;
      if (g.type !== "Polygon") throw new Error();
      const ring = g.coordinates[0].map((c) => [c[1], c[0]]);
      if (ring.length > 1 && ring[0][0] === ring[ring.length - 1][0] && ring[0][1] === ring[ring.length - 1][1]) ring.pop();
      pts = ring;
      render(true);
      return true;
    } catch (e) {
      return false;
    }
  }

  map.on("click", (e) => { pts.push([e.latlng.lat, e.latlng.lng]); render(false); });
  document.getElementById("draw-undo").addEventListener("click", () => { pts.pop(); render(false); });
  document.getElementById("draw-reset").addEventListener("click", () => { pts = []; areaTouched = false; render(false); });
  document.getElementById("draw-paste").addEventListener("click", () => {
    if (!load(paste.value)) alert(window.t("js.listing.badGeo", "GeoJSON düzgün deyil (Polygon gözlənilir)."));
  });
  // Jump to the chosen district (rough box) while nothing is drawn yet
  const district = document.querySelector("select[name=district]");
  function toDistrict() {
    const b = (window.DISTRICT_BOXES || {})[district && district.value];
    if (b && pts.length === 0) map.fitBounds([[b[1], b[0]], [b[3], b[2]]]);
  }
  district && district.addEventListener("change", toDistrict);

  if (geoInput.value) load(geoInput.value); else { render(false); toDistrict(); }
})();
