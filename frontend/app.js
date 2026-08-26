/* YBS demo frontend.

   One feature per view, switched from the sidebar. The earlier build stacked
   everything on a single page, which meant the examiner scrolled past four
   sections to reach the one being discussed. Routing is the URL hash, so any
   view can be linked to directly and the back button behaves. */

const YANGON_CENTRE = [16.83, 96.15];

let networkStops = [];
let selectedJourney = null;
let watchedBusId = null;
let lastSearch = null;
let busesOnlySelected = false;

/* ------------------------------------------------------------------- router */

const VIEWS = ['plan', 'map', 'arrivals', 'trace', 'prolog', 'fleet', 'about'];

function showView(name) {
    const view = VIEWS.includes(name) ? name : 'plan';
    document.querySelectorAll('.view').forEach(v => {
        v.classList.toggle('active', v.dataset.view === view);
    });
    document.querySelectorAll('.nav-item[data-view]').forEach(a => {
        a.classList.toggle('on', a.dataset.view === view);
    });

    // Leaflet measures the container when it is created. If the map was hidden
    // at that moment it computes a zero size and renders as a grey box, so it
    // has to be told to re-measure every time this view becomes visible.
    if (view === 'map' && window._map) {
        setTimeout(() => window._map.invalidateSize(), 30);
    }
    if (view === 'fleet') renderFleet();
    if (view === 'arrivals') loadArrivalsBoard();
}

window.addEventListener('hashchange', () => showView(location.hash.slice(1)));

/* --------------------------------------------------------------------- map */

const map = L.map('map', { scrollWheelZoom: true }).setView(YANGON_CENTRE, 12);
window._map = map;

// OpenStreetMap tiles need the internet. If they cannot load nothing breaks —
// the polylines and markers are the demo-critical layer and they render fine
// over the plain background. A note appears so the failure is honest.
const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
});
let tileErrorShown = false;
tiles.on('tileerror', () => {
    if (!tileErrorShown) {
        tileErrorShown = true;
        document.getElementById('map-note').classList.remove('hidden');
    }
});
tiles.addTo(map);

let allRoutes = [];
const selected = new Set();
const routeLayers = new Map();   // code -> { line, stops: [] }


/* ------------------------------------------------------- route colouring ---
   One colour per route, taken from the API, used everywhere that route is named:
   its line on the map, its bus markers, and every badge. Before this, buses were
   all the same dark pill, so two buses sitting at the same junction gave no clue
   which line they belonged to. */

function routeColour(code) {
    const r = allRoutes.find(x => x.code === code);
    return r ? r.colour : '#64748B';
}

/** Straight-line RGB distance. Crude, but enough to tell blue from navy. */
function colourDistance(a, b) {
    const rgb = h => [0, 2, 4].map(i => parseInt(h.replace('#', '').slice(i, i + 2), 16));
    const [x, y] = [rgb(a), rgb(b)];
    return Math.hypot(x[0] - y[0], x[1] - y[1], x[2] - y[2]);
}

/** Black or white label, whichever actually reads on the given background. */
function inkOn(hex) {
    const c = hex.replace('#', '');
    const [r, g, b] = [0, 2, 4].map(i => parseInt(c.slice(i, i + 2), 16) / 255);
    const lin = v => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
    const L = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    return L > 0.45 ? '#0B1220' : '#FFFFFF';
}

/** A route badge in the route's own colour. */
function badge(code) {
    const bg = routeColour(code);
    return `<span class="route-badge" style="background:${bg};color:${inkOn(bg)}">${code}</span>`;
}

async function loadNetwork() {
    const net = await (await fetch('/api/network')).json();
    networkStops = net.stops;
    allRoutes = net.routes;

    // The map opens with NO route lines drawn. Each drawn route brings its polyline
    // and all of its stop circles with it, and on the real network that buried the
    // buses under clutter. Routes are switched on from the filter when wanted; the
    // 236 coloured buses are always visible, and search always runs over all 118
    // routes regardless of what is drawn.

    renderRouteFilter();
    fitToCity(net.stops);
    return net;
}

function renderRouteFilter() {
    const box = document.getElementById('route-filter');
    if (!box) return;
    const term = (document.getElementById('route-search')?.value || '').toLowerCase();
    const list = allRoutes.filter(r =>
        !term || r.code.toLowerCase().includes(term) || (r.nameMy || '').includes(term));

    document.getElementById('route-count').textContent =
        `${selected.size} / ${allRoutes.length} ပြထား`;

    box.innerHTML = list.slice(0, 400).map(r => `
        <label class="route-toggle ${selected.has(r.code) ? 'on' : ''}">
            <input type="checkbox" data-code="${r.code}" ${selected.has(r.code) ? 'checked' : ''}>
            ${badge(r.code)}
            <span class="route-name">${r.nameMy}<em>${r.stopCount} stops</em></span>
        </label>`).join('');

    box.querySelectorAll('input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', async () => {
            const code = cb.dataset.code;
            if (cb.checked) { selected.add(code); await showRoute(code); }
            else { selected.delete(code); hideRoute(code); }
            renderRouteFilter();
        });
    });
}

/** Draws one route: its road-following shape, plus its own stop markers. */
async function showRoute(code) {
    if (routeLayers.has(code)) return;
    const r = await (await fetch(`/api/routes/${encodeURIComponent(code)}`)).json();

    // shape follows the road; if geometry was unavailable, join the stops instead
    const pts = (r.shape && r.shape.length)
        ? r.shape
        : r.stops.map(s => [s.lat, s.lon]);

    const line = L.polyline(pts, { color: r.colour, weight: 4, opacity: 0.85 })
        .addTo(map)
        .bindPopup(`<b>${r.code}</b><br>${r.nameMy}<br>${r.baseFareKyats} ကျပ် · ${r.stops.length} stops`);

    const markers = r.stops.map(s => {
        const m = L.circleMarker([s.lat, s.lon], {
            radius: 4, color: '#0B1220', weight: 1.5, fillColor: '#fff', fillOpacity: 1
        }).addTo(map);
        m.bindPopup(`<b>${s.nameMy}</b><br>${s.nameEn}`, { minWidth: 240 });
        m.on('popupopen', () => loadStopPopup({ id: s.stopId, nameMy: s.nameMy, nameEn: s.nameEn }, m));
        return m;
    });

    routeLayers.set(code, { line, stops: markers });
    updateLegend();
    fitToSelection();
}

function hideRoute(code) {
    const layer = routeLayers.get(code);
    if (!layer) return;
    map.removeLayer(layer.line);
    layer.stops.forEach(m => map.removeLayer(m));
    routeLayers.delete(code);
    updateLegend();
}

function updateLegend() {
    const legend = document.getElementById('legend');
    legend.innerHTML = [...selected].map(code => {
        const r = allRoutes.find(x => x.code === code);
        return r ? `<div class="item"><span class="swatch" style="background:${r.colour}"></span>
                    <span><b>${r.code}</b></span></div>` : '';
    }).join('');
}

/** Frames the whole network, used when no individual route is drawn. */
function fitToCity(stops) {
    if (!stops.length) return;
    const bounds = L.latLngBounds(stops.map(s => [s.lat, s.lon]));
    setTimeout(() => { map.invalidateSize(); map.fitBounds(bounds.pad(0.02)); }, 60);
}

function fitToSelection() {
    const lines = [...routeLayers.values()].map(l => l.line);
    if (!lines.length) return;
    const bounds = lines.reduce((b, l) => b ? b.extend(l.getBounds()) : l.getBounds(), null);
    setTimeout(() => { map.invalidateSize(); map.fitBounds(bounds.pad(0.05)); }, 60);
}

/* ---------------------------------------------------- arrivals (shared) --- */

function arrivalRows(arrivals) {
    if (!arrivals.length) {
        return `<div class="board-row muted">ယခု ဘတ်စ်ကား မရှိပါ <span class="en">no bus inbound</span></div>`;
    }
    return arrivals.map(a => `
        <div class="board-row">
            ${badge(a.routeCode)}
            <span class="board-eta">${a.etaMinutes < 1 ? 'ရောက်နေပြီ' : Math.round(a.etaMinutes) + ' မိနစ်'}</span>
            <span class="en">${a.etaLowerBound.toFixed(0)}–${a.etaUpperBound.toFixed(0)} min</span>
            <span class="board-bus en">bus #${a.busId}</span>
        </div>`).join('');
}

async function loadStopPopup(stop, marker) {
    const head = `<div class="board-head"><b>${stop.nameMy}</b><br>
                  <span class="en">${stop.nameEn} · next buses</span></div>`;
    try {
        const data = await (await fetch(`/api/stops/${stop.id}/arrivals`)).json();
        marker.setPopupContent(head + `<div class="board">${arrivalRows(data.arrivals)}</div>`);
    } catch (err) {
        marker.setPopupContent(head + `<div class="board muted">arrivals unavailable</div>`);
    }
}

/** The arrivals board view — the same prediction, given a page of its own. */
async function loadArrivalsBoard() {
    const select = document.getElementById('arrivals-stop');
    if (!select.value) return;
    const el = document.getElementById('arrivals-board');
    try {
        const data = await (await fetch(`/api/stops/${select.value}/arrivals`)).json();
        el.classList.remove('muted', 'empty-state');
        el.innerHTML = `<div class="board-title">${data.stopNameMy}
                        <span class="en">${data.stopNameEn}</span></div>
                        <div class="board big">${arrivalRows(data.arrivals)}</div>`;
    } catch (err) {
        el.textContent = 'arrivals unavailable';
    }
}

/* -------------------------------------------------------------- simulation */

const busMarkers = new Map();
let latestBuses = [];

async function pollBuses() {
    try {
        const all = await (await fetch('/api/buses/live')).json();
        latestBuses = all;
        // Every bus on the network is shown by default, each in its own route's colour -
        // that colouring is what makes 236 markers readable rather than a swarm of
        // identical pills. Restricting them to the drawn routes is opt-in, because
        // hiding buses whose line happens not to be drawn surprises people.
        const buses = busesOnlySelected ? all.filter(b => selected.has(b.routeCode)) : all;
        for (const [id, m] of busMarkers) {
            if (!buses.some(b => b.id === id)) { map.removeLayer(m); busMarkers.delete(id); }
        }
        for (const bus of buses) {
            const pos = [bus.lat, bus.lon];
            const dwelling = bus.state === 'DWELLING';
            // A waiting bus gets a visibly different marker, so a stationary
            // marker reads as "loading passengers", not "the sim has frozen".
            const bg = routeColour(bus.routeCode);
            const html = `<div class="bus-pill ${dwelling ? 'waiting' : ''}"
                 style="background:${bg};color:${inkOn(bg)}">${bus.routeCode}</div>`;

            let marker = busMarkers.get(bus.id);
            if (!marker) {
                marker = L.marker(pos, {
                    icon: L.divIcon({ className: '', html, iconSize: [34, 22], iconAnchor: [17, 11] })
                }).addTo(map);
                busMarkers.set(bus.id, marker);
            } else {
                marker.setLatLng(pos);
                if (marker._dwelling !== dwelling) {
                    marker.setIcon(L.divIcon({ className: '', html, iconSize: [34, 22], iconAnchor: [17, 11] }));
                }
            }
            marker._dwelling = dwelling;

            const state = dwelling
                ? `⏸ ရပ်နားနေသည် ${bus.dwellSecondsRemaining}s`
                : `🚌 ပြေးနေသည်`;
            const next = bus.nextStopNameMy ? `<br>→ ${bus.nextStopNameMy} · ${bus.minutesToNextStop} မိနစ်` : '';
            marker.bindTooltip(`ဘတ်စ် #${bus.id} — လမ်း ${bus.routeCode}<br>${state}${next}`, { direction: 'top' });
        }
        if (document.querySelector('.view[data-view="fleet"]').classList.contains('active')) {
            renderFleet();
        }
    } catch (err) {
        console.warn('bus poll failed', err);
    }
}

const CROWD = { 0: ['🟢', 'ထိုင်ခုံရ', 'seats free'], 1: ['🟠', 'မတ်တပ်', 'standing'], 2: ['🔴', 'လူပြည့်', 'full'] };

function renderFleet() {
    const el = document.getElementById('fleet-table');
    const shown = busesOnlySelected ? latestBuses.filter(b => selected.has(b.routeCode)) : latestBuses;
    if (!shown.length) {
        el.innerHTML = '<div class="muted">No buses to show.</div>';
        return;
    }
    el.innerHTML = `
      <table class="grid">
        <tr><th>Bus</th><th>Route</th><th>State</th><th>Next stop</th><th>ETA</th><th>Crowding</th></tr>
        ${shown.map(b => {
            const [icon, my, en] = CROWD[b.crowdingLevel] || CROWD[2];
            const dwelling = b.state === 'DWELLING';
            return `<tr>
                <td><b>#${b.id}</b></td>
                <td>${badge(b.routeCode)}</td>
                <td><span class="state-pill ${dwelling ? 'waiting' : 'driving'}">
                    ${dwelling ? `⏸ waiting ${b.dwellSecondsRemaining}s` : '🚌 driving'}</span></td>
                <td>${b.nextStopNameMy || '—'}<br><span class="en">${b.nextStopNameEn || ''}</span></td>
                <td>${b.minutesToNextStop != null ? b.minutesToNextStop + ' min' : '—'}</td>
                <td>${icon} ${my} <span class="en">${en}</span></td>
            </tr>`;
        }).join('')}
      </table>`;
}

function initSimControls() {
    const el = document.getElementById('sim-controls');
    el.className = 'speed-row';
    el.innerHTML = [1, 10, 60]
        .map(x => `<button type="button" class="speed-btn" data-x="${x}">${x}×</button>`).join('');
    el.querySelectorAll('.speed-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const res = await fetch(`/api/sim/speed?multiplier=${btn.dataset.x}`, { method: 'POST' });
            showSpeed((await res.json()).multiplier);
        });
    });
    fetch('/api/sim/speed').then(r => r.json()).then(d => showSpeed(d.multiplier));
}

function showSpeed(multiplier) {
    document.querySelectorAll('.speed-btn').forEach(b => {
        b.classList.toggle('on', Number(b.dataset.x) === multiplier);
    });
}

/* ------------------------------------------------------- rider constraints */

const PROFILE_FLAGS = [
    { key: 'pregnant',         my: 'ကိုယ်ဝန်ဆောင်', en: 'pregnant',   icon: '🤰' },
    { key: 'needs_seat',       my: 'ထိုင်ခုံလို',    en: 'needs seat', icon: '💺' },
    { key: 'mobility_limited', my: 'သွားလာရခက်',   en: 'mobility',   icon: '♿' },
    { key: 'student',          my: 'ကျောင်းသား',    en: 'student',    icon: '🎓' },
    { key: 'verified_card',    my: 'ကတ်အတည်ပြု',  en: 'verified',   icon: '🪪' },
    { key: 'monk',             my: 'သံဃာ',        en: 'monk',       icon: '🧘' }
];

const REASON_TEXT = {
    crowded_no_seat:           'ဘတ်စ်ကား လူပြည့်နေသည် · no seat available',
    too_many_transfers:        'ကားပြောင်းရမှု များလွန်းသည် · too many transfers',
    overbridge_not_accessible: 'ခုံးကျော်တံတား ဖြတ်ရမည် · overbridge not accessible',
    flooded_segment:           'ရေကြီးနေသော လမ်းပိုင်း · route segment is flooded'
};

function activeFlags() {
    return [...document.querySelectorAll('#profile .chip.on')].map(c => c.dataset.key);
}

function initProfile() {
    const el = document.getElementById('profile');
    el.innerHTML = PROFILE_FLAGS.map(f =>
        `<button type="button" class="chip" data-key="${f.key}">
            <span class="ic">${f.icon}</span>
            <span><span class="my">${f.my}</span><span class="en">${f.en}</span></span>
         </button>`).join('');
    el.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => { chip.classList.toggle('on'); runSearch(); });
    });

    document.getElementById('flood-btn').addEventListener('click', async () => {
        await fetch('/api/disruptions/flood?stopId=15&active=true', { method: 'POST' });
        runSearch();
    });
    document.getElementById('clear-btn').addEventListener('click', async () => {
        await fetch('/api/disruptions/clear', { method: 'POST' });
        runSearch();
    });
}

/* ------------------------------------------------------------------ search */

function initSelectors(stops) {
    networkStops = stops;
    const opts = stops.map(s => `<option value="${s.id}">${s.nameMy} — ${s.nameEn}</option>`).join('');
    const from = document.getElementById('from-stop');
    const to = document.getElementById('to-stop');
    const arr = document.getElementById('arrivals-stop');
    from.innerHTML = opts; to.innerHTML = opts; arr.innerHTML = opts;
    from.value = 1;   // Insein Market
    to.value = 21;    // Botataung Pagoda
    arr.value = 6;    // Hledan Junction — an interchange, so the board is never empty
    document.getElementById('search-btn').addEventListener('click', runSearch);
    arr.addEventListener('change', loadArrivalsBoard);
}

async function runSearch() {
    const from = document.getElementById('from-stop').value;
    const to = document.getElementById('to-stop').value;
    const flags = activeFlags().map(f => `&profile=${f}`).join('');

    document.getElementById('results').textContent = 'ရှာနေသည်… / searching…';
    const data = await (await fetch(`/api/journeys/search?from=${from}&to=${to}${flags}`)).json();
    lastSearch = data;
    renderTrace(data);
    renderResults(data);
}

function renderTrace(data) {
    const saved = data.dijkstraExpansions - data.expansions;
    document.getElementById('search-stats').innerHTML =
        `<div class="stat hero"><b>${data.expansions}</b>A* nodes expanded</div>` +
        `<div class="stat"><b>${data.dijkstraExpansions}</b>Dijkstra, h = 0</div>` +
        `<div class="stat"><b>${saved}</b>saved by the heuristic</div>` +
        `<div class="stat"><b>${data.bucket.replace('_', ' ')}</b>traffic bucket</div>`;

    const panel = document.getElementById('trace-panel');
    panel.classList.remove('muted');
    panel.innerHTML = data.trace.map(t =>
        `<div><span class="ord">${String(t.order).padStart(3, ' ')}</span> ` +
        `${t.stopName}<span class="rc"> [${t.routeCode || 'on foot'}]</span> ` +
        `g=${t.g.toFixed(1)} h=${t.h.toFixed(1)} <b>f=${t.f.toFixed(1)}</b></div>`).join('');
    panel.scrollTop = 0;
}

function renderResults(data) {
    const el = document.getElementById('results');
    el.classList.remove('muted', 'empty-state');
    document.getElementById('pareto-badge').textContent =
        data.options.length ? `Pareto front · ${data.options.length}` : '';

    if (!data.options.length) { el.textContent = 'ခရီးစဉ် မတွေ့ပါ / no journey found'; return; }

    el.innerHTML = data.options.map(o => {
        const j = o.journey;
        const badges = j.legs.map(l => l.kind === 'walk'
            ? `<span class="route-badge walk-badge">🚶</span>`
            : badge(l.routeCode)
        ).join('<span class="route-sep">›</span>');

        const verdict = o.suitable
            ? `<div class="verdict ok"><span class="ic">✔</span>
                 <span>အဆင်ပြေပါသည် <span class="en">suitable for this rider</span></span></div>`
            : `<div class="verdict bad"><span class="ic">⚠</span><span>` +
              o.violationReasons.map(r => REASON_TEXT[r] || r).join('<br>') + `</span></div>`;

        return `<div class="journey ${o.suitable ? '' : 'unsuitable'}">
            <div class="journey-top">
                <div>
                    <div class="fare">${o.riderFareKyats.toLocaleString()}</div>
                    <div class="fare-unit">ကျပ် · kyats</div>
                </div>
                <div class="journey-meta">
                    <div class="meta-row">
                        <span class="ic">⏱</span><span class="mins">${j.totalMinutes.toFixed(0)}</span>
                        <span>မိနစ်</span>
                        <span class="band">${j.etaLowerBound.toFixed(0)}–${j.etaUpperBound.toFixed(0)}</span>
                    </div>
                    <div class="meta-row">
                        <span class="ic">🔁</span>
                        <span>${j.transferCount} ကြိမ် ပြောင်း
                          <span class="en">${j.transferCount} transfer${j.transferCount === 1 ? '' : 's'}</span></span>
                    </div>
                </div>
            </div>
            <div class="route-badges">${badges}</div>
            ${verdict}
            <button type="button" class="pick" data-id="${j.id}" ${o.suitable ? '' : 'disabled'}>
                ${o.suitable ? '🧭 ဤခရီးစဉ် ရွေးမည်' : '🚫 မရနိုင်ပါ · unavailable'}
            </button>
        </div>`;
    }).join('');

    el.querySelectorAll('.pick:not([disabled])').forEach(btn => {
        btn.addEventListener('click', () => {
            selectedJourney = data.options.find(o => o.journey.id === btn.dataset.id).journey;
            drawJourney(selectedJourney);
            startTrip(selectedJourney);
            location.hash = '#map';   // the trip is a map story from here on
        });
    });
}

let journeyLayer = null;
function drawJourney(journey) {
    if (journeyLayer) map.removeLayer(journeyLayer);
    const pts = journey.stopIds
        .map(id => networkStops.find(s => s.id === id))
        .filter(Boolean).map(s => [s.lat, s.lon]);
    journeyLayer = L.polyline(pts, { color: '#0B1220', weight: 9, opacity: 0.5 }).addTo(map);
    setTimeout(() => { map.invalidateSize(); map.fitBounds(journeyLayer.getBounds().pad(0.15)); }, 60);
}

/* ------------------------------------------------------------ Prolog console */

const GOAL_PRESETS = [
    ["fare(m1,'43',F)", 'unknown rider → full fare'],
    ["assert(monk(m1)),fare(m1,'43',F)", 'assert a fact, then two solutions'],
    ["bonus_eligible(d1,'2026-08')", 'driver bonus rule'],
    ["bonus_eligible(d4,'2026-08')", 'one complaint disqualifies'],
    ["suitable(j1,rider1)", 'needs a search first'],
    ["violation_reason(j1,rider1,Why)", 'why an option was withdrawn']
];

function initProlog() {
    const input = document.getElementById('goal-input');
    input.value = GOAL_PRESETS[0][0];

    document.getElementById('goal-presets').innerHTML = GOAL_PRESETS
        .map(([g, label], i) => `<button type="button" class="preset" data-i="${i}">
            <code>${g.replace(/</g, '&lt;')}</code><em>${label}</em></button>`).join('');

    document.querySelectorAll('#goal-presets .preset').forEach(b => {
        b.addEventListener('click', () => {
            input.value = GOAL_PRESETS[b.dataset.i][0];
            runGoal();
        });
    });

    document.getElementById('goal-run').addEventListener('click', runGoal);
    input.addEventListener('keydown', e => { if (e.key === 'Enter') runGoal(); });
}

async function runGoal() {
    const goal = document.getElementById('goal-input').value.trim();
    const out = document.getElementById('goal-output');
    if (!goal) return;
    out.classList.remove('muted');
    out.innerHTML = `<div class="echo">?- ${goal}</div><div class="muted">solving…</div>`;

    try {
        const d = await (await fetch('/api/prolog/query?goal=' + encodeURIComponent(goal))).json();
        let body;
        if (d.error) {
            body = `<div class="sol err">${d.error}</div>`;
        } else if (!d.proved) {
            body = `<div class="sol no">false — no solution</div>`;
        } else if (!d.solutions.length || d.solutions.every(s => !Object.keys(s).length)) {
            // A ground goal has nothing to bind; proving it IS the answer.
            body = `<div class="sol yes">true</div>`;
        } else {
            body = d.solutions.map((s, i) =>
                `<div class="sol yes"><span class="n">${i + 1}.</span> ` +
                Object.entries(s).map(([k, v]) => `<code>${k} = ${v}</code>`).join(', ') +
                `</div>`).join('');
        }
        const count = d.proved ? `<div class="sol-count en">${d.solutions.length} solution(s)</div>` : '';
        out.innerHTML = `<div class="echo">?- ${goal}</div>${body}${count}`;
    } catch (err) {
        out.innerHTML = `<div class="echo">?- ${goal}</div><div class="sol err">request failed</div>`;
    }
}

/* ------------------------------------------------------------------- about */

const TOPICS = [
    ['🔍', 'Heuristic search', 'planning/', 'A* over (stop, route) nodes. Returns a Pareto set over time, fare and transfers rather than one weighted answer, and reports how many nodes it expanded against plain Dijkstra.', '#trace'],
    ['🧠', 'Knowledge representation', 'reasoning/', 'A tuProlog engine loaded from ybs.pl. Java only asserts ground facts and asks goals — every decision is made by the rule base.', '#prolog'],
    ['🚦', 'Constraint satisfaction', 'reasoning/', 'suitable(J,R) is negation-as-failure over violates/2. A parallel violation_reason/3 carries the cause, so a withdrawn option can say why.', '#plan'],
    ['🎲', 'Reasoning under uncertainty', 'eta/', 'A hand-collected traffic prior, stated as an assumption rather than dressed up as a learned model. Every estimate is an interval, never a point.', '#arrivals'],
    ['🗣️', 'NLP / speech', 'voice/', 'Burmese audio to transcript to entities, keyed off the postpositions မှ (from) and ကို (to). String matching, not a language model — and the UI shows the parse so nobody takes it on trust.', '#plan']
];

function initAbout() {
    document.getElementById('about-grid').innerHTML = TOPICS.map(([ic, title, pkg, body, href]) => `
        <a class="topic" href="${href}">
            <div class="topic-ic">${ic}</div>
            <div class="topic-title">${title}</div>
            <code class="topic-pkg">${pkg}</code>
            <p>${body}</p>
        </a>`).join('') + `
        <div class="topic flat">
            <div class="topic-ic">📐</div>
            <div class="topic-title">One process, on purpose</div>
            <p>No Docker, no microservices, no build step, no database server. The whole demo is one
               Spring Boot process with H2 in memory, so it starts with a single command and survives
               a venue with no wifi. Leaflet is vendored; only map tiles need the internet.</p>
        </div>`;
}

/* ------------------------------------------------------------------- voice */

async function initVoice() {
    const data = await (await fetch('/api/voice/samples')).json();
    const select = document.getElementById('voice-sample');
    select.innerHTML = Object.entries(data.samples)
        .map(([k, text]) => `<option value="${k}">${text}</option>`).join('');

    document.getElementById('voice-btn').addEventListener('click', async () => {
        const status = document.getElementById('voice-status');
        const chips = document.getElementById('voice-entities');
        chips.innerHTML = '';
        status.textContent = '🎧 နားထောင်နေသည်… / listening…';

        const res = await (await fetch(`/api/voice/query?sample=${select.value}`, { method: 'POST' })).json();
        status.innerHTML = `<b>“${res.transcript}”</b> <span class="en">${res.mode}</span>`;
        chips.innerHTML =
            `<span class="entity"><span class="mark">မှ</span><b>${res.originName || '?'}</b></span>` +
            `<span class="entity"><span class="mark">ကို</span><b>${res.destName || '?'}</b></span>`;

        if (res.understood) {
            document.getElementById('from-stop').value = res.originStopId;
            document.getElementById('to-stop').value = res.destStopId;
            runSearch();
        } else {
            status.innerHTML += ' — <span class="en">not understood</span>';
        }
    });
}

/* ------------------------------------------------------------ arrival alert */

async function pollAlert() {
    try {
        const s = await (await fetch('/api/alert/state')).json();
        if (s.triggered) showArrival(s);

        if (s.watching && watchedBusId && s.stopId) {
            const data = await (await fetch(`/api/buses/${watchedBusId}/arrivals`)).json();
            const due = data.arrivals.find(a => a.stopId === s.stopId);
            const el = document.getElementById('trip-countdown');
            if (el) {
                el.classList.remove('muted');
                el.innerHTML = due
                    ? `🚌 ${due.stopNameMy} အထိ <b>${Math.round(due.etaMinutes)}</b> မိနစ်
                       <span class="en">(${due.etaLowerBound.toFixed(0)}–${due.etaUpperBound.toFixed(0)} min ·
                       ${Math.round(s.distanceMetres)} m away)</span>`
                    : `🚌 ${Math.round(s.distanceMetres)} m away`;
            }
        }
    } catch (err) {
        console.warn('alert poll failed', err);
    }
}

function showArrival(state) {
    document.getElementById('arrival-stop').textContent = `${state.stopNameMy} — ${state.stopNameEn}`;
    document.getElementById('arrival-banner').classList.remove('hidden');
    playClips(state.clips || []);
}

/* Plays the pre-recorded clips end to end. Tries .mp3 first so real recordings
   drop in without touching this code; falls back to the .wav placeholders. */
function playClips(names) {
    let i = 0;
    const next = () => {
        if (i >= names.length) return;
        const name = names[i++];
        const audio = new Audio(`/audio/${name}.mp3`);
        audio.addEventListener('ended', next);
        audio.addEventListener('error', () => {
            const wav = new Audio(`/audio/${name}.wav`);
            wav.addEventListener('ended', next);
            wav.addEventListener('error', next);
            wav.play().catch(next);
        });
        audio.play().catch(() => { /* the error handler above takes over */ });
    };
    next();
}

function initArrival() {
    document.getElementById('arrival-close').addEventListener('click', async () => {
        document.getElementById('arrival-banner').classList.add('hidden');
        await fetch('/api/alert/cancel', { method: 'POST' });
        watchedBusId = null;
    });
}

async function startTrip(journey) {
    const routeCode = journey.routeCodes[0];
    const buses = await (await fetch('/api/buses/live')).json();
    const bus = buses.find(b => b.routeCode === routeCode);
    if (!bus) return;
    const alightStopId = journey.stopIds[journey.stopIds.length - 1];
    await fetch(`/api/alert/start?busId=${bus.id}&stopId=${alightStopId}`, { method: 'POST' });
    watchedBusId = bus.id;
    document.getElementById('trip-countdown').innerHTML =
        `🚌 ဘတ်စ် #${bus.id} ကို စောင့်ကြည့်နေသည် — ${journey.legs.at(-1).toNameMy} တွင် ဆင်းရန်`;
}

/* -------------------------------------------------------------------- boot */

loadNetwork()
    .then(net => {
        initSimControls();
        initProfile();
        initSelectors(net.stops);
        initVoice();
        initArrival();
        initProlog();
        initAbout();
        document.getElementById('route-search').addEventListener('input', renderRouteFilter);
        document.getElementById('buses-selected-only').addEventListener('change', e => {
            busesOnlySelected = e.target.checked;
            for (const [id, m] of busMarkers) { map.removeLayer(m); }
            busMarkers.clear();
            pollBuses();
        });
        document.getElementById('route-clear').addEventListener('click', () => {
            [...selected].forEach(hideRoute);
            selected.clear();
            renderRouteFilter();
        });
        showView(location.hash.slice(1) || 'plan');
        pollBuses();
        setInterval(pollBuses, 1000);
        setInterval(pollAlert, 1000);
    })
    .catch(err => {
        console.error('Failed to load network', err);
        document.getElementById('map-note').classList.remove('hidden');
    });
