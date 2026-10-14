// Leaflet helpers: OSM + Esri satellite layers, plot polygons coloured by activity.
const ACTIVITY_COLORS = { HORTICULTURE: '#3d7326', CROP_FARMING: '#ca8a04', LIVESTOCK: '#a0703c', OTHER: '#6b7280' };
const ACTIVITY_LABELS = { HORTICULTURE: 'Bağçılıq', CROP_FARMING: 'Əkinçilik', LIVESTOCK: 'Heyvandarlıq', OTHER: 'Digər' };

// satDefault: start on the satellite layer (passport)
function createMap(elId, satDefault) {
    const map = L.map(elId);
    const osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        { maxZoom: 19, attribution: '&copy; OpenStreetMap' });
    const sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        { maxZoom: 19, attribution: 'Tiles &copy; Esri &mdash; Esri, Maxar, Earthstar Geographics' });
    (satDefault ? sat : osm).addTo(map);
    L.control.layers({ 'Xəritə': osm, 'Peyk': sat }).addTo(map);
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
            div.appendChild(document.createTextNode(p.district + ', ' + p.areaHa + ' ha · ' + (ACTIVITY_LABELS[p.purpose] || '')
                + (p.score ? ' · uyğunluq ' + p.score : '')));
            div.appendChild(document.createElement('br'));
            const a = document.createElement('a'); a.href = '/saheler/' + p.id; a.textContent = 'Pasporta bax →'; div.appendChild(a);
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
