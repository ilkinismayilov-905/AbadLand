/*
 * AgroInvest Qarabağ — 3D plot viewer
 * Satellite imagery (Esri World Imagery) draped on real terrain (AWS Terrain Tiles, Terrarium encoding),
 * rendered with three.js r128. Modes: bird's-eye orbit, walk inside the plot, day / sunset.
 *
 * Requires (before this file):
 *   <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
 *
 * Usage:
 *   Plot3D.open({
 *     name: "Füzuli — Qacar bağ sahəsi",
 *     subtitle: "Füzuli · 42,5 ha · Bağçılıq",
 *     color: "#3F8A4E",                         // activity color
 *     geometry: {type:"Polygon", coordinates:[[[lng,lat],...]]},   // GeoJSON polygon of the plot
 *     applyUrl: "/kabinet/muraciet/yeni?plotId=1"  // optional, shows "Müraciət et"
 *   });
 */
(function () {
  "use strict";

  var CSS = [
    ".p3d{position:fixed;inset:0;z-index:5000;background:#0b120e;font-family:Inter,system-ui,sans-serif}",
    ".p3d canvas{width:100%;height:100%;display:block;touch-action:none;cursor:grab}",
    ".p3d canvas:active{cursor:grabbing}",
    ".p3d .hud{position:absolute;left:0;right:0;display:flex;gap:8px;flex-wrap:wrap;padding:14px;color:#fff;pointer-events:none}",
    ".p3d .hud>*{pointer-events:auto}",
    ".p3d .top{top:env(safe-area-inset-top,0px);justify-content:space-between;align-items:flex-start;text-shadow:0 1px 6px #000}",
    ".p3d .top b{font-size:19px;display:block}",
    ".p3d .bot{bottom:env(safe-area-inset-bottom,0px);justify-content:center}",
    ".p3d .g{border:1px solid rgba(255,255,255,.4);background:rgba(16,36,24,.7);color:#fff;padding:9px 16px;border-radius:999px;cursor:pointer;font:600 14px Inter,system-ui,sans-serif}",
    ".p3d .g.on{background:#fff;color:#1E4A28}",
    ".p3d .g.pri{background:#2E6B3A;border-color:#2E6B3A}",
    ".p3d .g:focus-visible{outline:2px solid #fff;outline-offset:2px}",
    ".p3d .hint{position:absolute;left:50%;transform:translateX(-50%);bottom:calc(76px + env(safe-area-inset-bottom,0px));color:#fff;background:rgba(0,0,0,.55);padding:6px 14px;border-radius:999px;font-size:13.5px;text-align:center;pointer-events:none;max-width:92vw}",
    ".p3d .attr{position:absolute;right:12px;top:calc(72px + env(safe-area-inset-top,0px));color:rgba(255,255,255,.8);font-size:11.5px;text-shadow:0 1px 3px #000}",
    ".p3d .elev{position:absolute;left:14px;top:calc(72px + env(safe-area-inset-top,0px));color:#fff;font-size:12.5px;background:rgba(16,36,24,.7);padding:6px 11px;border-radius:9px;line-height:1.5}",
    ".p3d .pad{position:absolute;right:16px;bottom:calc(110px + env(safe-area-inset-bottom,0px));display:grid;grid-template-columns:repeat(3,48px);gap:6px}",
    ".p3d .pad button{height:48px;border-radius:50%;border:1px solid rgba(255,255,255,.4);background:rgba(16,36,24,.7);color:#fff;font-size:18px;touch-action:none;cursor:pointer}"
  ].join("\n");

  function injectCss() {
    if (document.getElementById("p3d-css")) return;
    var s = document.createElement("style");
    s.id = "p3d-css";
    s.textContent = CSS;
    document.head.appendChild(s);
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  var tileXY = function (la, lo, z) {
    var n = Math.pow(2, z), r = la * Math.PI / 180;
    return [(lo + 180) / 360 * n, (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * n];
  };
  var loadImg = function (u) {
    return new Promise(function (res) {
      var i = new Image();
      i.crossOrigin = "anonymous";
      i.onload = function () { res(i); };
      i.onerror = function () { res(null); };
      i.src = u;
    });
  };

  /* Convert GeoJSON polygon to local metres around its centroid. x = east, z = south. */
  function localPolygon(geometry) {
    var ring = geometry.coordinates[0].slice();
    var f = ring[0], l = ring[ring.length - 1];
    if (f[0] === l[0] && f[1] === l[1]) ring.pop();
    var lat0 = 0, lng0 = 0;
    ring.forEach(function (p) { lng0 += p[0]; lat0 += p[1]; });
    lat0 /= ring.length; lng0 /= ring.length;
    var cl = Math.cos(lat0 * Math.PI / 180);
    var pts = ring.map(function (p) { return [(p[0] - lng0) * 111320 * cl, -(p[1] - lat0) * 111320]; });
    var xs = pts.map(function (p) { return p[0]; }), zs = pts.map(function (p) { return p[1]; });
    var extent = Math.max(Math.max.apply(null, xs) - Math.min.apply(null, xs), Math.max.apply(null, zs) - Math.min.apply(null, zs), 150);
    var area = 0;
    for (var i = 0; i < pts.length; i++) { var a = pts[i], b = pts[(i + 1) % pts.length]; area += a[0] * b[1] - b[0] * a[1]; }
    return { lat: lat0, lng: lng0, pts: pts, extent: extent, areaHa: Math.abs(area) / 2 / 1e4 };
  }

  async function buildTerrain(T, scene, poly, opts, onHint) {
    var lat = poly.lat, lng = poly.lng, a = poly.extent;
    var cl = Math.cos(lat * Math.PI / 180);
    var z = Math.min(17, Math.max(11, Math.floor(Math.log2(156543.03 * cl * 512 / (0.6 * a)))));
    var fxy = tileXY(lat, lng, z), fx = fxy[0], fy = fxy[1], tx = Math.floor(fx), ty = Math.floor(fy);
    var mpp = 156543.03392 * cl / Math.pow(2, z), size = 1280 * mpp;
    var ox = (tx + 0.5 - fx) * 256 * mpp, oz = (ty + 0.5 - fy) * 256 * mpp;

    var cv = document.createElement("canvas"); cv.width = cv.height = 1280;
    var cx = cv.getContext("2d"); cx.fillStyle = "#5d7442"; cx.fillRect(0, 0, 1280, 1280);
    var ok = 0, jobs = [];
    for (var dy = -2; dy <= 2; dy++) for (var dx = -2; dx <= 2; dx++) (function (dx, dy) {
      jobs.push(loadImg("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" + z + "/" + (ty + dy) + "/" + (tx + dx))
        .then(function (i) { if (i) { cx.drawImage(i, (dx + 2) * 256, (dy + 2) * 256); ok++; } }));
    })(dx, dy);

    var E = {}, ez = 13, half = size / 2;
    var c1 = tileXY(lat + (half - oz) / 111320, lng + (-half - ox) / (111320 * cl), ez);
    var c2 = tileXY(lat - (half + oz) / 111320, lng + (half - ox) / (111320 * cl), ez);
    for (var ax = Math.floor(c1[0]); ax <= Math.floor(c2[0]); ax++) for (var by = Math.floor(c1[1]); by <= Math.floor(c2[1]); by++) (function (ax, by) {
      jobs.push(loadImg("https://s3.amazonaws.com/elevation-tiles-prod/terrarium/" + ez + "/" + ax + "/" + by + ".png").then(function (i) {
        if (!i) return;
        var c = document.createElement("canvas"); c.width = c.height = 256;
        var x = c.getContext("2d"); x.drawImage(i, 0, 0);
        try { E[ax + "_" + by] = x.getImageData(0, 0, 256, 256).data; } catch (e) { /* CORS */ }
      }));
    })(ax, by);
    await Promise.all(jobs);

    var elev = function (la, lo) {
      var t = tileXY(la, lo, ez), d = E[Math.floor(t[0]) + "_" + Math.floor(t[1])];
      if (!d) return null;
      var u = Math.min(254, (t[0] % 1) * 256), v = Math.min(254, (t[1] % 1) * 256), i0 = Math.floor(u), j0 = Math.floor(v), fu = u - i0, fv = v - j0;
      var g = function (i, j) { var k = (j * 256 + i) * 4; return d[k] * 256 + d[k + 1] + d[k + 2] / 256 - 32768; };
      return (g(i0, j0) * (1 - fu) + g(i0 + 1, j0) * fu) * (1 - fv) + (g(i0, j0 + 1) * (1 - fu) + g(i0 + 1, j0 + 1) * fu) * fv;
    };
    var base = elev(lat, lng);
    var hasElev = base !== null; if (!hasElev) base = 0;

    var N = 128, geo = new THREE.PlaneGeometry(size, size, N, N); geo.rotateX(-Math.PI / 2);
    var ps = geo.attributes.position, H = new Float32Array(ps.count), minE = Infinity, maxE = -Infinity;
    for (var i = 0; i < ps.count; i++) {
      var w = ps.getX(i) + ox, zz = ps.getZ(i) + oz;
      var e = elev(lat - zz / 111320, lng + w / (111320 * cl));
      if (e === null) e = base;
      if (e < minE) minE = e; if (e > maxE) maxE = e;
      var h = (e - base) * 1.4; H[i] = h; ps.setY(i, h);
    }
    geo.computeVertexNormals();
    var tex = new THREE.CanvasTexture(cv); tex.anisotropy = 8;
    var mesh = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: tex }));
    mesh.position.set(ox, 0, oz); scene.add(mesh); T.objects.push(mesh);

    var hAt = function (x, zq) {
      var u = Math.min(N - 0.001, Math.max(0, ((x - ox) / size + 0.5) * N)), v = Math.min(N - 0.001, Math.max(0, ((zq - oz) / size + 0.5) * N));
      var i = Math.floor(u), j = Math.floor(v), fu = u - i, fv = v - j, g = function (a2, b2) { return H[b2 * (N + 1) + a2]; };
      return (g(i, j) * (1 - fu) + g(i + 1, j) * fu) * (1 - fv) + (g(i, j + 1) * (1 - fu) + g(i + 1, j + 1) * fu) * fv;
    };

    // plot outline + translucent walls
    var col = new THREE.Color(opts.color || "#3F8A4E");
    var pts = poly.pts.map(function (p) { return new THREE.Vector3(p[0], hAt(p[0], p[1]) + 1.5, p[1]); });
    var WH = Math.max(25, a * 0.03), wv = [];
    for (var k = 0; k < pts.length; k++) {
      var p1 = pts[k], p2 = pts[(k + 1) % pts.length];
      wv.push(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p2.x, p2.y + WH, p2.z, p1.x, p1.y, p1.z, p2.x, p2.y + WH, p2.z, p1.x, p1.y + WH, p1.z);
    }
    var wg = new THREE.BufferGeometry(); wg.setAttribute("position", new THREE.Float32BufferAttribute(wv, 3));
    var walls = new THREE.Mesh(wg, new THREE.MeshBasicMaterial({ color: col, transparent: true, opacity: 0.32, side: THREE.DoubleSide, depthWrite: false }));
    var line = new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0xffffff }));
    scene.add(walls, line); T.objects.push(walls, line);

    // floating label
    var lc = document.createElement("canvas"); lc.width = 640; lc.height = 110;
    var lx = lc.getContext("2d"); lx.fillStyle = "rgba(16,36,24,.85)"; lx.fillRect(0, 0, 640, 110);
    lx.fillStyle = "#fff"; lx.textAlign = "center";
    lx.font = "bold 36px Inter, Arial, sans-serif"; lx.fillText(opts.name || "", 320, 50, 610);
    lx.font = "26px Inter, Arial, sans-serif"; lx.fillText(opts.subtitle || "", 320, 90, 610);
    var sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(lc), depthTest: false }));
    var ss = Math.max(90, a / 6); sp.scale.set(ss, ss * 0.17, 1); sp.position.set(0, hAt(0, 0) + ss * 0.7, 0);
    scene.add(sp); T.objects.push(sp);

    T.hAt = hAt; T.ox = ox; T.oz = oz; T.size = size; T.tex = tex;
    if (!ok) onHint(window.t('js.plots.3d.noImage', 'Peyk şəkli yüklənmədi. İnternet bağlantısını yoxlayın.'));
    else if (!hasElev) onHint(window.t('js.plots.3d.noElevation', 'Relyef məlumatı yüklənmədi, sahə düz göstərilir.'));
    else onHint(null);
    return { minE: hasElev ? minE : null, maxE: hasElev ? maxE : null, centerE: hasElev ? base : null };
  }

  function open(opts) {
    if (typeof THREE === "undefined") { alert(window.t('js.plots.3d.noLib', '3D kitabxanası (three.js) yüklənmədi.')); return; }
    if (!opts || !opts.geometry || !opts.geometry.coordinates) { alert(window.t('js.plots.3d.noGeometry', 'Sahənin sərhədləri tapılmadı.')); return; }
    injectCss();
    var poly = localPolygon(opts.geometry);
    var m = document.createElement("div"); m.className = "p3d"; m.setAttribute("role", "dialog"); m.setAttribute("aria-label", window.t('js.plots.3d.dialog', '3D görünüş:') + " " + (opts.name || ""));
    m.innerHTML =
      '<canvas></canvas>' +
      '<div class="hud top"><div><b>' + esc(opts.name) + '</b>' + esc(opts.subtitle) + '</div><button class="g" data-a="close">✕ ' + esc(window.t('js.plots.3d.close', 'Bağla')) + '</button></div>' +
      '<div class="elev" hidden></div>' +
      '<div class="attr">' + esc(window.t('js.plots.3d.attribution', 'Peyk: Esri World Imagery · Relyef: AWS Terrain Tiles')) + '</div>' +
      '<div class="pad" hidden><span></span><button data-k="f" aria-label="' + esc(window.t('js.plots.3d.forward', 'İrəli')) + '">▲</button><span></span><button data-k="l" aria-label="' + esc(window.t('js.plots.3d.left', 'Sola')) + '">◀</button><button data-k="b" aria-label="' + esc(window.t('js.plots.3d.back', 'Geri')) + '">▼</button><button data-k="r" aria-label="' + esc(window.t('js.plots.3d.right', 'Sağa')) + '">▶</button></div>' +
      '<div class="hint">' + esc(window.t('js.plots.3d.loading', 'Peyk görüntüsü və relyef yüklənir…')) + '</div>' +
      '<div class="hud bot"><button class="g on" data-m="orbit">' + esc(window.t('js.plots.3d.orbit', 'Quşbaxışı')) + '</button><button class="g" data-m="walk">' + esc(window.t('js.plots.3d.walk', 'Sahənin içində gəz')) + '</button><button class="g" data-a="tod">' + esc(window.t('js.plots.3d.dayDusk', 'Gün / Qürub')) + '</button>' +
      (opts.applyUrl ? '<a class="g pri" href="' + esc(opts.applyUrl) + '" style="text-decoration:none">' + esc(window.t('js.plots.3d.apply', 'Müraciət et')) + '</a>' : '') + '</div>';
    document.body.appendChild(m);
    var prevOverflow = document.body.style.overflow; document.body.style.overflow = "hidden";
    var hint = m.querySelector(".hint"), canvas = m.querySelector("canvas");

    var R;
    try { R = new THREE.WebGLRenderer({ canvas: canvas, antialias: true }); }
    catch (e) {
      hint.textContent = window.t('js.plots.3d.unsupported', 'Bu cihazda 3D dəstəklənmir.');
      m.querySelector('[data-a="close"]').onclick = function () { m.remove(); document.body.style.overflow = prevOverflow; };
      return;
    }

    var scene = new THREE.Scene(), cam = new THREE.PerspectiveCamera(60, 1, 0.5, 20000);
    var hemi = new THREE.HemisphereLight(0xffffff, 0x8a8a7a, 1), sun = new THREE.DirectionalLight(0xffffff, 0.45);
    scene.add(hemi, sun);
    var night = 0;
    var T = { hAt: function () { return 0; }, ox: 0, oz: 0, size: 1400, objects: [] };
    var tod = function () {
      var c = night ? 0xf0a070 : 0x9cc9ee;
      scene.background = new THREE.Color(c); scene.fog = new THREE.Fog(c, 3000, 11000);
      hemi.intensity = night ? 0.7 : 1; hemi.color.setHex(night ? 0xffd2b0 : 0xffffff);
      sun.color.setHex(night ? 0xff9a5a : 0xffffff); sun.position.set(night ? -300 : 200, night ? 60 : 300, 150);
    };
    tod();

    var st = { mode: "orbit", th: 0.8, ph: 1.0, rad: Math.max(poly.extent, 300) * 1.15, yaw: 0, pit: -0.05, pos: new THREE.Vector3(0, 0, poly.extent / 3.2 + 60), k: {}, drag: false, sh: false };
    var modeHint = function (md) { return md === "walk" ? window.t('js.plots.3d.hintWalk', 'Sürüşdürün: ətrafa baxın · W A S D / oxlar: hərəkət · Shift: sürətli') : window.t('js.plots.3d.hintOrbit', 'Sürüşdürün: fırladın · Təkər: yaxınlaşdırın'); };

    buildTerrain(T, scene, poly, opts, function (msg) { hint.textContent = msg || modeHint(st.mode); })
      .then(function (r) {
        if (r && r.centerE !== null) {
          var box = m.querySelector(".elev"); box.hidden = false;
          box.innerHTML = esc(window.t('js.plots.3d.center', 'Mərkəz:')) + " <b>" + Math.round(r.centerE) + " m</b><br>" + esc(window.t('js.plots.3d.minMax', 'Ən aşağı / ən yuxarı:')) + " " + Math.round(r.minE) + " – " + Math.round(r.maxE) + " m";
        }
      })
      .catch(function (e) { hint.textContent = window.t('js.plots.3d.failed', '3D görünüş yüklənmədi:') + " " + (e && e.message ? e.message : e); });

    cam.rotation.order = "YXZ";
    var setMode = function (md) {
      st.mode = md;
      m.querySelectorAll("[data-m]").forEach(function (b) { b.classList.toggle("on", b.dataset.m === md); });
      m.querySelector(".pad").hidden = md !== "walk";
      if (md === "walk") { st.pos.set(0, 0, poly.extent / 3.2 + 60); st.yaw = 0; st.pit = -0.05; }
      hint.textContent = modeHint(md);
    };
    m.querySelectorAll("[data-m]").forEach(function (b) { b.onclick = function () { setMode(b.dataset.m); }; });
    m.querySelector('[data-a="tod"]').onclick = function () { night ^= 1; tod(); };

    var lx = 0, ly = 0;
    canvas.onpointerdown = function (e) { st.drag = true; lx = e.clientX; ly = e.clientY; canvas.setPointerCapture(e.pointerId); };
    canvas.onpointerup = canvas.onpointercancel = function () { st.drag = false; };
    canvas.onpointermove = function (e) {
      if (!st.drag) return;
      var dx = e.clientX - lx, dy = e.clientY - ly; lx = e.clientX; ly = e.clientY;
      if (st.mode === "orbit") { st.th -= dx * 0.006; st.ph = Math.min(1.5, Math.max(0.2, st.ph - dy * 0.005)); }
      else { st.yaw -= dx * 0.005; st.pit = Math.min(1.2, Math.max(-1.2, st.pit - dy * 0.004)); }
    };
    canvas.onwheel = function (e) { e.preventDefault(); st.rad = Math.min(7000, Math.max(60, st.rad * (1 + e.deltaY * 0.001))); };

    var km = { w: "f", arrowup: "f", s: "b", arrowdown: "b", a: "l", arrowleft: "l", d: "r", arrowright: "r" };
    var kd = function (e) { if (e.key === "Escape") { close(); return; } var k = km[e.key.toLowerCase()]; if (k) { st.k[k] = 1; e.preventDefault(); } st.sh = e.shiftKey; };
    var ku = function (e) { var k = km[e.key.toLowerCase()]; if (k) st.k[k] = 0; st.sh = e.shiftKey; };
    addEventListener("keydown", kd); addEventListener("keyup", ku);
    m.querySelectorAll(".pad button").forEach(function (b) {
      b.onpointerdown = function () { st.k[b.dataset.k] = 1; };
      b.onpointerup = b.onpointerleave = function () { st.k[b.dataset.k] = 0; };
    });

    var rs = function () { R.setPixelRatio(Math.min(2, devicePixelRatio)); R.setSize(innerWidth, innerHeight, false); cam.aspect = innerWidth / innerHeight; cam.updateProjectionMatrix(); };
    rs(); addEventListener("resize", rs);

    var reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    var raf, t = 0;
    var loop = function () {
      raf = requestAnimationFrame(loop); t++;
      if (st.mode === "orbit") {
        if (!st.drag && !reduce) st.th += 0.002;
        var y0 = T.hAt(0, 0);
        cam.position.set(st.rad * Math.sin(st.ph) * Math.sin(st.th), y0 + st.rad * Math.cos(st.ph) + 10, st.rad * Math.sin(st.ph) * Math.cos(st.th));
        cam.lookAt(0, y0, 0);
      } else {
        var f = (st.k.f || 0) - (st.k.b || 0), r = (st.k.r || 0) - (st.k.l || 0), spd = (st.sh ? 3 : 1) * Math.max(0.55, poly.extent / 1800);
        st.pos.x += (-Math.sin(st.yaw) * f + Math.cos(st.yaw) * r) * spd;
        st.pos.z += (-Math.cos(st.yaw) * f - Math.sin(st.yaw) * r) * spd;
        var lim = T.size / 2 - 40;
        st.pos.x = Math.min(T.ox + lim, Math.max(T.ox - lim, st.pos.x));
        st.pos.z = Math.min(T.oz + lim, Math.max(T.oz - lim, st.pos.z));
        cam.position.set(st.pos.x, T.hAt(st.pos.x, st.pos.z) + 1.8 + ((f || r) && !reduce ? Math.sin(t * 0.2) * 0.05 : 0), st.pos.z);
        cam.rotation.set(st.pit, st.yaw, 0);
      }
      R.render(scene, cam);
    };
    loop();

    function close() {
      cancelAnimationFrame(raf);
      removeEventListener("keydown", kd); removeEventListener("keyup", ku); removeEventListener("resize", rs);
      scene.traverse(function (o) {
        if (o.geometry) o.geometry.dispose();
        if (o.material) { if (o.material.map) o.material.map.dispose(); o.material.dispose(); }
      });
      R.dispose(); m.remove(); document.body.style.overflow = prevOverflow;
    }
    m.querySelector('[data-a="close"]').onclick = close;
    m.querySelector('[data-a="close"]').focus();
  }

  window.Plot3D = { open: open };
})();
