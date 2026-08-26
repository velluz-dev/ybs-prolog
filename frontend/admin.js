/* Admin screens.

   Every save posts to /api/admin/*, which writes to H2 and then rebuilds the
   in-memory caches - so a stop added here shows up on the map and in the planner
   immediately, without a restart. It is still lost on restart unless exported,
   which is what the banner at the top of the page is about. */

let network = { stops: [], routes: [] };
let itinerary = [];      // the itinerary being edited, before it is saved

const $ = id => document.getElementById(id);
const val = id => $(id).value;

function toast(message, ok = true) {
    const t = $('toast');
    t.textContent = message;
    t.className = 'toast ' + (ok ? 'ok' : 'bad');
    setTimeout(() => t.classList.add('hidden'), 3200);
    t.classList.remove('hidden');
}

async function api(path, options) {
    const res = await fetch('/api/admin' + path, options);
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.error || `${res.status} ${res.statusText}`);
    return body;
}

const post = (path, payload) => api(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
});

/* ------------------------------------------------------------------- tabs */

$('tabs').addEventListener('click', e => {
    const tab = e.target.closest('.tab');
    if (!tab) return;
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('on', t === tab));
    document.querySelectorAll('.tab-panel').forEach(p =>
        p.classList.toggle('active', p.dataset.tab === tab.dataset.tab));
});

/* ------------------------------------------------------------- reference data */

async function loadNetwork() {
    network = await (await fetch('/api/network')).json();

    const routeOpts = network.routes
        .map(r => `<option value="${r.id}">${r.code} — ${r.nameMy}</option>`).join('');
    ['i-route', 'd-route', 'b-route', 'n-route'].forEach(id => { $(id).innerHTML = routeOpts; });

    // 2,080 stops in a plain select is slow to scroll but searchable by typing,
    // which beats a custom widget nobody asked for.
    const stopOpts = network.stops
        .map(s => `<option value="${s.id}">${s.nameMy} — ${s.nameEn}</option>`).join('');
    ['i-stop', 'n-stop'].forEach(id => { $(id).innerHTML = stopOpts; });

    renderLists();
    await refreshSummary();
}

async function refreshSummary() {
    const s = await api('/summary');
    $('summary').textContent =
        `${s.stops} stops · ${s.routes} routes · ${s.routeStops} route-stops · ` +
        `${s.buses} buses · ${s.drivers} drivers · ${s.trafficFactors} traffic rows · ` +
        `${s.incidents} incidents`;
}

function renderLists() {
    $('stop-list').innerHTML = rows(network.stops.slice(0, 60),
        s => [`#${s.id}`, s.nameMy, s.nameEn, `${s.lat.toFixed(4)}, ${s.lon.toFixed(4)}`],
        s => `/stops/${s.id}`, network.stops.length);

    $('route-list').innerHTML = rows(network.routes,
        r => [`#${r.id}`, r.code, r.nameMy, `${r.baseFareKyats} K`, `${r.stopCount} stops`],
        r => `/routes/${r.id}`);
}

/** A compact table with a delete button per row. */
function rows(items, cells, deletePath, total) {
    if (!items.length) return '<p class="muted">none yet</p>';
    const more = total && total > items.length
        ? `<p class="hint">showing ${items.length} of ${total}</p>` : '';
    return `<table class="grid">${items.map(i => `
        <tr>${cells(i).map(c => `<td>${c ?? ''}</td>`).join('')}
        <td><button type="button" class="alt-btn tiny" data-del="${deletePath(i)}">✖</button></td></tr>`
    ).join('')}</table>${more}`;
}

document.addEventListener('click', async e => {
    const btn = e.target.closest('[data-del]');
    if (!btn) return;
    if (!confirm('Delete this permanently from the running database?')) return;
    try {
        await api(btn.dataset.del, { method: 'DELETE' });
        toast('Deleted');
        await loadNetwork();
    } catch (err) {
        toast(err.message, false);
    }
});

/* ------------------------------------------------------------------ saving */

const SAVERS = {
    stop: () => post('/stops', {
        id: val('s-id'), nameMy: val('s-my'), nameEn: val('s-en'),
        lat: val('s-lat'), lon: val('s-lon'), landmarkPhotoUrl: val('s-photo')
    }),
    route: () => post('/routes', {
        id: val('r-id'), code: val('r-code'), nameMy: val('r-my'), baseFareKyats: val('r-fare')
    }),
    traffic: () => post('/traffic', {
        segmentId: val('t-seg'), bucket: val('t-bucket'), factor: val('t-factor')
    }),
    driver: () => post('/drivers', {
        id: val('d-id'), name: val('d-name'), routeId: val('d-route')
    }),
    score: () => post('/scores', {
        driverId: val('sc-driver'), monthKey: val('sc-month'),
        headwayScore: val('sc-headway'), harshEvents: val('sc-harsh'),
        upheldComplaints: val('sc-complaints')
    }),
    bus: () => post('/buses', {
        id: val('b-id'), routeId: val('b-route'),
        currentSegmentIndex: val('b-index'), crowdingLevel: val('b-crowd')
    }),
    incident: () => post('/incidents', {
        type: val('n-type'), routeId: val('n-route'), stopId: val('n-stop')
    })
};

document.addEventListener('click', async e => {
    const btn = e.target.closest('[data-save]');
    if (!btn) return;
    try {
        const saved = await SAVERS[btn.dataset.save]();
        toast('Saved — live now, but export data.sql to keep it');
        await loadNetwork();
        await loadDrivers();
        console.log('saved', saved);
    } catch (err) {
        toast(err.message, false);
    }
});

/* --------------------------------------------------------------- itinerary */

$('i-add').addEventListener('click', () => {
    const stop = network.stops.find(s => String(s.id) === val('i-stop'));
    if (!stop) return;
    // 3 minutes per hop is only a starting point; real running times come from the
    // seeded data, and this is here so a hand-built route is not left at zero.
    itinerary.push({ stopId: stop.id, nameMy: stop.nameMy, nameEn: stop.nameEn,
                     cumulativeMinutes: itinerary.length * 3 });
    renderItinerary();
});

$('i-load').addEventListener('click', async () => {
    const route = network.routes.find(r => String(r.id) === val('i-route'));
    if (!route) return;
    const full = await (await fetch(`/api/routes/${encodeURIComponent(route.code)}`)).json();
    itinerary = full.stops.map(s => ({
        stopId: s.stopId, nameMy: s.nameMy, nameEn: s.nameEn, cumulativeMinutes: s.cumulativeMinutes
    }));
    renderItinerary();
});

$('i-save').addEventListener('click', async () => {
    try {
        const r = await post(`/routes/${val('i-route')}/stops`, itinerary);
        toast(`Saved itinerary: ${r.stops} stops`);
        await loadNetwork();
    } catch (err) {
        toast(err.message, false);
    }
});

function renderItinerary() {
    $('i-list').innerHTML = itinerary.length
        ? `<table class="grid">
             <tr><th>#</th><th>Stop</th><th>Cumulative min</th><th></th></tr>
             ${itinerary.map((s, i) => `<tr>
                <td>${i}</td>
                <td>${s.nameMy}<br><span class="en">${s.nameEn}</span></td>
                <td><input type="number" class="cum" data-i="${i}" value="${s.cumulativeMinutes}"></td>
                <td><button type="button" class="alt-btn tiny" data-rm="${i}">✖</button></td>
             </tr>`).join('')}
           </table>`
        : '<p class="muted">Append stops, or load the route\'s current itinerary.</p>';

    $('i-list').querySelectorAll('.cum').forEach(inp => {
        inp.addEventListener('change', () => {
            itinerary[inp.dataset.i].cumulativeMinutes = Number(inp.value);
        });
    });
    $('i-list').querySelectorAll('[data-rm]').forEach(b => {
        b.addEventListener('click', () => {
            itinerary.splice(Number(b.dataset.rm), 1);
            renderItinerary();
        });
    });
}

/* ----------------------------------------------------------------- drivers */

async function loadDrivers() {
    const d = await (await fetch('/api/ops/dashboard')).json();
    $('sc-driver').innerHTML = d.drivers
        .map(r => `<option value="${r.driverId}">${r.driverName}</option>`).join('');
    $('driver-list').innerHTML = `<table class="grid">
        <tr><th>Driver</th><th>Month</th><th>Headway</th><th>Harsh</th><th>Complaints</th>
            <th>Prolog says</th><th></th></tr>
        ${d.drivers.map(r => `<tr>
            <td>${r.driverName}</td><td>${r.month}</td><td>${r.headwayScore}</td>
            <td>${r.harshEvents}</td><td>${r.upheldComplaints}</td>
            <td>${r.bonusEligible ? '<span class="badge yes">eligible</span>'
                                  : '<span class="badge no">not eligible</span>'}</td>
            <td><button type="button" class="alt-btn tiny" data-del="/drivers/${r.driverId}">✖</button></td>
        </tr>`).join('')}</table>`;
}

/* -------------------------------------------------------------------- boot */

loadNetwork().then(loadDrivers).catch(err => toast(err.message, false));
