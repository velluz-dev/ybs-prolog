/* YBS demo frontend.
   Layout is two-zone: the rider's decision surface on the left, the evidence of
   what the AI actually did on the right. Both must be visible at once during the
   viva, which is why the A* trace is not tucked away below the fold. */

const YANGON_CENTRE = [16.83, 96.15];

const map = L.map('map', { scrollWheelZoom: true }).setView(YANGON_CENTRE, 12);

// OpenStreetMap tiles need the internet. If they cannot load nothing breaks -
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

let networkStops = [];
let routeColours = {};
let selectedJourney = null;

/* ------------------------------------------------------------------ network */

async function loadNetwork() {
    const net = await (await fetch('/api/network')).json();
    const legend = document.getElementById('legend');
    const allPoints = [];

    for (const route of net.routes) {
        routeColours[route.code] = route.colour;
        const latlngs = route.stops.map(s => [s.lat, s.lon]);
        allPoints.push(...latlngs);

        L.polyline(latlngs, { color: route.colour, weight: 5, opacity: 0.85 })
            .addTo(map)
            .bindPopup(`<b>${route.code}</b><br>${route.nameMy}<br>${route.baseFareKyats} ကျပ်`);

        const item = document.createElement('div');
        item.className = 'item';
        item.innerHTML = `<span class="swatch" style="background:${route.colour}"></span>` +
                         `<span><b>${route.code}</b> — ${route.nameMy}</span>`;
        legend.appendChild(item);
    }

    // Clicking a stop opens its arrivals board — "which bus is next, and how long".
    for (const stop of net.stops) {
        const marker = L.circleMarker([stop.lat, stop.lon], {
            radius: 6, color: '#0B1220', weight: 2, fillColor: '#ffffff', fillOpacity: 1
        }).addTo(map);
        marker.bindPopup(`<b>${stop.nameMy}</b><br>${stop.nameEn}`, { minWidth: 240 });
        marker.on('popupopen', () => loadStopBoard(stop, marker));
    }

    if (allPoints.length) {
        map.fitBounds(L.latLngBounds(allPoints).pad(0.08));
    }
    return net;
}

/**
 * The "when's my bus" board for one stop. Each row is a predicted arrival with its
 * confidence band, so the board never claims more precision than the model has.
 */
async function loadStopBoard(stop, marker) {
    const head = `<div class="board-head"><b>${stop.nameMy}</b><br>
                  <span class="en">${stop.nameEn} · next buses</span></div>`;
    try {
        const data = await (await fetch(`/api/stops/${stop.id}/arrivals`)).json();
        const rows = data.arrivals.length
            ? data.arrivals.map(a => `
                <div class="board-row">
                    <span class="route-badge" data-route="${a.routeCode}">${a.routeCode}</span>
                    <span class="board-eta">${a.etaMinutes < 1 ? 'ရောက်နေပြီ' : Math.round(a.etaMinutes) + ' မိနစ်'}</span>
                    <span class="en">${a.etaLowerBound.toFixed(0)}–${a.etaUpperBound.toFixed(0)} min</span>
                </div>`).join('')
            : `<div class="board-row muted">ယခု ဘတ်စ်ကား မရှိပါ <span class="en">no bus inbound</span></div>`;
        marker.setPopupContent(head + `<div class="board">${rows}</div>`);
    } catch (err) {
        marker.setPopupContent(head + `<div class="board muted">arrivals unavailable</div>`);
    }
}

/* -------------------------------------------------------------- simulation */

const busMarkers = new Map();

async function pollBuses() {
    try {
        const buses = await (await fetch('/api/buses/live')).json();
        for (const bus of buses) {
            const pos = [bus.lat, bus.lon];
            const dwelling = bus.state === 'DWELLING';
            // A waiting bus gets a visibly different marker, so a bus sitting at Hledan
            // reads as "loading passengers" rather than "the simulation has frozen".
            const html = `<div class="bus-pill ${dwelling ? 'waiting' : ''}">${bus.routeCode}</div>`;

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

            const crowd = bus.crowdingLevel >= 2 ? '🔴 လူပြည့်' : bus.crowdingLevel === 1 ? '🟠 မတ်တပ်' : '🟢 ထိုင်ခုံရ';
            const state = dwelling
                ? `⏸ ရပ်နားနေသည် ${bus.dwellSecondsRemaining}s <span class="en">waiting</span>`
                : `🚌 ပြေးနေသည် <span class="en">driving</span>`;
            const next = bus.nextStopNameMy
                ? `<br>→ ${bus.nextStopNameMy} · ${bus.minutesToNextStop} မိနစ်`
                : '';
            marker.bindTooltip(
                `ဘတ်စ် #${bus.id} — လမ်း ${bus.routeCode}<br>${state} ${crowd}${next}`,
                { direction: 'top' });
        }
    } catch (err) {
        console.warn('bus poll failed', err);
    }
}

function initSimControls() {
    const el = document.getElementById('sim-controls');
    el.className = 'speed-row';
    el.innerHTML = `<span>⏩ အရှိန်</span>` +
        [1, 10, 60].map(x => `<button type="button" class="speed-btn" data-x="${x}">${x}×</button>`).join('');
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
    { key: 'pregnant',         my: 'ကိုယ်ဝန်ဆောင်', en: 'pregnant', icon: '🤰' },
    { key: 'needs_seat',       my: 'ထိုင်ခုံလို',    en: 'needs seat', icon: '💺' },
    { key: 'mobility_limited', my: 'သွားလာရခက်',   en: 'mobility',  icon: '♿' },
    { key: 'student',          my: 'ကျောင်းသား',    en: 'student',   icon: '🎓' },
    { key: 'verified_card',    my: 'ကတ်အတည်ပြု',  en: 'verified',  icon: '🪪' },
    { key: 'monk',             my: 'သံဃာ',        en: 'monk',      icon: '🧘' }
];

const REASON_TEXT = {
    crowded_no_seat:           'ဘတ်စ်ကား လူပြည့်နေသည် — ထိုင်ခုံမရနိုင်ပါ · no seat available',
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
        chip.addEventListener('click', () => {
            chip.classList.toggle('on');
            runSearch();
        });
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

function initTripSelectors(stops) {
    networkStops = stops;
    const opts = stops.map(s => `<option value="${s.id}">${s.nameMy} — ${s.nameEn}</option>`).join('');
    const from = document.getElementById('from-stop');
    const to = document.getElementById('to-stop');
    from.innerHTML = opts;
    to.innerHTML = opts;
    from.value = 1;    // Insein Market
    to.value = 21;     // Botataung Pagoda
    document.getElementById('search-btn').addEventListener('click', runSearch);
}

async function runSearch() {
    const from = document.getElementById('from-stop').value;
    const to = document.getElementById('to-stop').value;
    const flags = activeFlags().map(f => `&profile=${f}`).join('');

    document.getElementById('results').textContent = 'ရှာနေသည်… / searching…';

    const data = await (await fetch(`/api/journeys/search?from=${from}&to=${to}${flags}`)).json();
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
    el.classList.remove('muted');
    document.getElementById('pareto-badge').textContent =
        data.options.length ? `Pareto front · ${data.options.length}` : '';

    if (!data.options.length) {
        el.textContent = 'ခရီးစဉ် မတွေ့ပါ / no journey found';
        return;
    }

    el.innerHTML = data.options.map(o => {
        const j = o.journey;

        // One badge per leg, so the transfer pattern reads at a glance.
        const badges = j.legs.map(l => l.kind === 'walk'
            ? `<span class="route-badge walk-badge">🚶</span>`
            : `<span class="route-badge" data-route="${l.routeCode}">${l.routeCode}</span>`
        ).join('<span class="route-sep">›</span>');

        const verdict = o.suitable
            ? `<div class="verdict ok"><span class="ic">✔</span>
                 <span>အဆင်ပြေပါသည် <span class="en">suitable for this rider</span></span></div>`
            : `<div class="verdict bad"><span class="ic">⚠</span><span>` +
              o.violationReasons.map(r => REASON_TEXT[r] || r).join('<br>') +
              `</span></div>`;

        return `<div class="journey ${o.suitable ? '' : 'unsuitable'}">
            <div class="journey-top">
                <div>
                    <div class="fare">${o.riderFareKyats.toLocaleString()}</div>
                    <div class="fare-unit">ကျပ် · kyats</div>
                </div>
                <div class="journey-meta">
                    <div class="meta-row">
                        <span class="ic">⏱</span>
                        <span class="mins">${j.totalMinutes.toFixed(0)}</span>
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
        });
    });
}

let journeyLayer = null;
function drawJourney(journey) {
    if (journeyLayer) {
        map.removeLayer(journeyLayer);
    }
    const pts = journey.stopIds
        .map(id => networkStops.find(s => s.id === id))
        .filter(Boolean)
        .map(s => [s.lat, s.lon]);
    journeyLayer = L.polyline(pts, { color: '#0B1220', weight: 9, opacity: 0.5 }).addTo(map);
    map.fitBounds(journeyLayer.getBounds().pad(0.15));
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

        // Show the parse, so the NLP step is visible rather than taken on trust.
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

/* --------------------------------------------------------- arrival alert */

let watchedBusId = null;

async function pollAlert() {
    try {
        const s = await (await fetch('/api/alert/state')).json();
        if (s.triggered) {
            showArrival(s);
        }
        // While a trip is running, show how long until the alight stop rather than only
        // firing at 400 m. The countdown comes from the same predictor as the stop boards.
        if (s.watching && watchedBusId && s.stopId) {
            const data = await (await fetch(`/api/buses/${watchedBusId}/arrivals`)).json();
            const due = data.arrivals.find(a => a.stopId === s.stopId);
            const el = document.getElementById('trip-countdown');
            if (el) {
                el.innerHTML = due
                    ? `🚌 ${due.stopNameMy} အထိ <b>${Math.round(due.etaMinutes)}</b> မိနစ်
                       <span class="en">(${due.etaLowerBound.toFixed(0)}–${due.etaUpperBound.toFixed(0)} min · ${Math.round(s.distanceMetres)} m away)</span>`
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
    });
}

/** Watches whichever bus on the journey's first route is currently running. */
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
        initTripSelectors(net.stops);
        initVoice();
        initArrival();
        pollBuses();
        setInterval(pollBuses, 1000);
        setInterval(pollAlert, 1000);
    })
    .catch(err => {
        console.error('Failed to load network', err);
        document.getElementById('map-note').classList.remove('hidden');
    });
