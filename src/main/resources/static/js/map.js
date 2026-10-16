// Leaflet helpers: OSM + Esri satellite layers, plot polygons coloured by activity.
const ACTIVITY_COLORS = { HORTICULTURE: '#3d7326', CROP_FARMING: '#ca8a04', LIVESTOCK: '#a0703c', OTHER: '#6b7280' };
const ACTIVITY_LABELS = { HORTICULTURE: window.t('js.plots.horticulture', 'Bağçılıq'), CROP_FARMING: window.t('js.plots.cropFarming', 'Əkinçilik'),
    LIVESTOCK: window.t('js.plots.livestock', 'Heyvandarlıq'), OTHER: window.t('js.plots.other', 'Digər') };

// p: {name, subtitle, purpose, geoJson (string), applyUrl} from the server
function open3d(p) {
    if (typeof Plot3D === 'undefined') { alert(window.t('js.plots.no3d', '3D görünüş bu səhifədə yüklənməyib.')); return; }
    Plot3D.open({ name: p.name, subtitle: p.subtitle, color: ACTIVITY_COLORS[p.purpose] || '#6b7280',
        geometry: JSON.parse(p.geoJson), applyUrl: p.applyUrl || undefined });
}

// satDefault: start on the satellite layer (passport); plot3d: adds a "3D" entry to the layer toggle (passport)
function createMap(elId, satDefault, plot3d) {
    const map = L.map(elId);
    const osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        { maxZoom: 19, attribution: '&copy; OpenStreetMap' });
    const sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        { maxZoom: 19, attribution: 'Tiles &copy; Esri &mdash; Esri, Maxar, Earthstar Geographics' });
    let current = satDefault ? sat : osm;
    current.addTo(map);
    const bases = { [window.t('js.plots.layerMap', 'Xəritə')]: osm, [window.t('js.plots.layerSat', 'Peyk')]: sat };
    if (plot3d) {
        // "3D" is a pseudo layer: open the viewer, then switch the toggle back to the previous layer
        const three = L.layerGroup();
        bases['3D'] = three;
        map.on('baselayerchange', e => {
            if (e.layer !== three) { current = e.layer; return; }
            // next tick: Leaflet ignores layer changes while it handles its own click, so the radio would stay on "3D"
            setTimeout(() => { map.removeLayer(three); current.addTo(map); }, 0);
            open3d(plot3d);
        });
    }
    L.control.layers(bases, null, { collapsed: !plot3d }).addTo(map);
    return map;
}

// plots: [{id, code, name, district, areaHa, purpose, geoJson, score}]
// outlineOnly: no fill (passport over satellite imagery)
function drawPlots(map, plots, withPopup, outlineOnly) {
    const group = L.featureGroup();
    plots.forEach(p => {
        const color = ACTIVITY_COLORS[p.purpose] || '#6b7280';
        const layer = L.geoJSON(JSON.parse(p.geoJson), { style: outlineOnly ? { color: '#facc15', weight: 3, fillOpacity: 0 } : { color, weight: 2, fillOpacity: 0.35 } });
        if (withPopup) {
            const div = document.createElement('div');
            const b = document.createElement('b'); b.textContent = p.code + ' — ' + p.name; div.appendChild(b);
            div.appendChild(document.createElement('br'));
            div.appendChild(document.createTextNode(p.district + ', ' + p.areaHa + ' ' + window.t('js.plots.ha', 'ha') + ' · ' + (ACTIVITY_LABELS[p.purpose] || '')
                + (p.score ? ' · ' + window.t('js.plots.match', 'uyğunluq') + ' ' + p.score : '')));
            div.appendChild(document.createElement('br'));
            const a = document.createElement('a'); a.href = '/saheler/' + p.id; a.textContent = window.t('js.plots.openPassport', 'Pasporta bax →'); div.appendChild(a);
            if (p.code) {
                const ai = document.createElement('a'); ai.href = '/ai?sahe=' + encodeURIComponent(p.code) + '#sec';
                ai.textContent = window.t('js.plots.ai', 'AI analizi →'); ai.style.marginLeft = '12px'; div.appendChild(ai);
            }
            if (p.subtitle) {
                const b3 = document.createElement('a'); b3.href = '#'; b3.textContent = window.t('js.plots.view3d', '3D görünüş'); b3.style.marginLeft = '12px';
                b3.onclick = e => { e.preventDefault(); map.closePopup(); open3d(p); };
                div.appendChild(b3);
            }
            layer.bindPopup(div);
        }
        layer.addTo(group);
    });
    group.addTo(map);
    if (plots.length) map.fitBounds(group.getBounds(), { padding: [30, 30], maxZoom: 15 });
    else map.setView([39.75, 46.8], 9);
    return group;
}

function addLegend(map) {
    const legend = L.control({ position: 'bottomright' });
    legend.onAdd = () => {
        const div = L.DomUtil.create('div', 'bg-white rounded shadow px-3 py-2 text-xs');
        div.innerHTML = Object.keys(ACTIVITY_COLORS).map(k =>
            `<div><span style="display:inline-block;width:12px;height:12px;background:${ACTIVITY_COLORS[k]};opacity:.7;margin-right:6px"></span>${ACTIVITY_LABELS[k]}</div>`).join('');
        return div;
    };
    legend.addTo(map);
}
