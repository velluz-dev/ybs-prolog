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

/* --------------------------------------------------------------------- auth ---
   Two things live here. postJson() is the only way this file makes a write: CSRF is
   on for every POST, including the public ones like /api/sim/speed, so each needs
   the token Spring put in a readable cookie. Routing every write through one helper
   is what stops that being a thing to remember.

   session is who the server says we are. It is asked for, never assumed - a page
   that guessed would show a driver nav to someone whose session had expired. Signed
   out is not an error state: it means rider, which is a real role here. */

function csrfToken() {
    const hit = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
    return hit ? decodeURIComponent(hit.slice('XSRF-TOKEN='.length)) : '';
}

async function post(url, options) {
    const opts = options || {};
    return fetch(url, {
        ...opts,
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': csrfToken(), ...(opts.headers || {}) }
    });
}

let session = { authenticated: false, role: 'RIDER' };

async function loadSession() {
    try {
        session = await (await fetch('/api/auth/me')).json();
    } catch (err) {
        session = { authenticated: false, role: 'RIDER' };
    }
    return session;
}

/** Which roles this visitor may switch to. Rider is always one of them. */
function allowedRoles() {
    const roles = ['rider'];
    if (session.role === 'DRIVER') roles.push('driver');
    if (session.role === 'ADMIN') roles.push('admin');
    return roles;
}

/* ------------------------------------------------------------------- router */

const VIEWS = ['plan', 'map', 'arrivals', 'trace', 'prolog', 'fleet', 'shift', 'about'];

/* ------------------------------------------------------------ decoration ---
   Illustrations as inline SVG rather than image files. Three reasons, in order:
   the demo has to run with the network unplugged, the whole frontend is meant to
   be readable in one sitting without a build step, and drawings that inherit
   currentColor follow the dark-mode switch for free.

   They are deliberately simple. This is a bus app, not a picture book, and every
   one of them is decoration - each is aria-hidden and the interface still reads
   correctly with all of them removed. */

const ROLE_ART = {
    rider: `<svg class="role-art" viewBox="0 0 56 56" fill="none" aria-hidden="true">
        <ellipse cx="28" cy="50" rx="17" ry="4" fill="#000" opacity=".07"/>
        <path d="M12 50c0-8.8 7.2-16 16-16s16 7.2 16 16H12Z" fill="#3B82F6"/>
        <path d="M22 36h12v6a6 6 0 0 1-12 0v-6Z" fill="#F3C9A2"/>
        <circle cx="28" cy="22" r="12" fill="#F8D5AF"/>
        <path d="M16 21a12 12 0 0 1 24 0c0 2.2-2.2 1-4-.6-2-1.8-4.6-2.6-8-2.6s-6 .8-8 2.6c-1.8 1.6-4 2.8-4-.6Z" fill="#2B3A55"/>
        <ellipse cx="23" cy="23" rx="1.7" ry="2" fill="#2B3A55"/>
        <ellipse cx="33" cy="23" rx="1.7" ry="2" fill="#2B3A55"/>
        <circle cx="19.5" cy="27" r="2.2" fill="#F4A9A0" opacity=".65"/>
        <circle cx="36.5" cy="27" r="2.2" fill="#F4A9A0" opacity=".65"/>
        <path d="M25 28.5c1.8 1.6 4.2 1.6 6 0" stroke="#B9764C" stroke-width="1.6" stroke-linecap="round"/>
        <path d="M43 33c3 1.4 4.4 4.4 3.6 7.4" stroke="#F8D5AF" stroke-width="4" stroke-linecap="round"/>
        <circle cx="47" cy="42" r="2.6" fill="#F8D5AF"/>
    </svg>`,
    driver: `<svg class="role-art" viewBox="0 0 56 56" fill="none" aria-hidden="true">
        <ellipse cx="28" cy="50" rx="17" ry="4" fill="#000" opacity=".07"/>
        <path d="M12 50c0-8.8 7.2-16 16-16s16 7.2 16 16H12Z" fill="#16A34A"/>
        <path d="M22 36h12v6a6 6 0 0 1-12 0v-6Z" fill="#F3C9A2"/>
        <path d="M20 44h16v6H20z" fill="#0F7A38"/>
        <circle cx="28" cy="23" r="11.5" fill="#F8D5AF"/>
        <ellipse cx="23.5" cy="24" rx="1.7" ry="2" fill="#2B3A55"/>
        <ellipse cx="32.5" cy="24" rx="1.7" ry="2" fill="#2B3A55"/>
        <path d="M25 29c1.8 1.5 4.2 1.5 6 0" stroke="#B9764C" stroke-width="1.6" stroke-linecap="round"/>
        <path d="M14 18h28l-2-4.6A5 5 0 0 0 35.4 10H20.6A5 5 0 0 0 16 13.4L14 18Z" fill="#1E3A8A"/>
        <rect x="12.5" y="17" width="31" height="4.4" rx="2.2" fill="#152C6B"/>
        <circle cx="28" cy="13.6" r="2.3" fill="#FCD34D"/>
        <path d="M28 12.2 28.7 13.6 30.2 13.8 29.1 14.9 29.4 16.4 28 15.7 26.6 16.4 26.9 14.9 25.8 13.8 27.3 13.6Z" fill="#B45309"/>
    </svg>`,
    admin: `<svg class="role-art" viewBox="0 0 56 56" fill="none" aria-hidden="true">
        <ellipse cx="28" cy="50" rx="17" ry="4" fill="#000" opacity=".07"/>
        <path d="M12 50c0-8.8 7.2-16 16-16s16 7.2 16 16H12Z" fill="#7C3AED"/>
        <path d="M22 36h12v6a6 6 0 0 1-12 0v-6Z" fill="#F3C9A2"/>
        <circle cx="28" cy="23" r="11.5" fill="#F8D5AF"/>
        <path d="M16 22a12 12 0 0 1 24 0c0 2.6-3 1.2-5.6.4a24 24 0 0 0-12.8 0C19 23.2 16 24.6 16 22Z" fill="#D5DCE6"/>
        <path d="M17 20c2-6 8-9 11-9s9 3 11 9c-3-3.6-7-5.2-11-5.2S20 16.4 17 20Z" fill="#EDF1F6"/>
        <circle cx="23.4" cy="24" r="3.9" stroke="#42506B" stroke-width="1.5" fill="#fff" fill-opacity=".55"/>
        <circle cx="32.6" cy="24" r="3.9" stroke="#42506B" stroke-width="1.5" fill="#fff" fill-opacity=".55"/>
        <path d="M27.3 24h1.4" stroke="#42506B" stroke-width="1.5"/>
        <path d="M25 30c1.8 1.4 4.2 1.4 6 0" stroke="#B9764C" stroke-width="1.6" stroke-linecap="round"/>
    </svg>`
};

/**
 * The painted header scene. Drop the artwork at this path and it is used; leave it
 * out and HEAD_ART below is drawn instead, so a checkout without the asset still
 * renders a header rather than a broken image.
 */
const HEAD_IMAGE = '/img/hero-bus.png';

/**
 * The tips card's painted background. Same arrangement as the header: present it if
 * the file is there, keep the drawn family if it is not.
 */
const TIPS_IMAGE = '/img/tips-family.png';

/** The scene beside the planner heading: a YBS bus on the road, pagoda behind. */
const HEAD_ART = `<svg viewBox="0 0 1024 300" fill="none" aria-hidden="true"
     preserveAspectRatio="xMidYMax meet">
  <defs>
    <linearGradient id="hillA" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#CFE9B0"/><stop offset="1" stop-color="#BFE0A0"/>
    </linearGradient>
    <linearGradient id="pagodaG" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#F7D46B"/><stop offset="1" stop-color="#E8B33F"/>
    </linearGradient>
  </defs>

  <g fill="#DCEBFA">
    <ellipse cx="140" cy="66" rx="52" ry="20"/><ellipse cx="188" cy="58" rx="34" ry="15"/>
    <ellipse cx="404" cy="52" rx="46" ry="18"/><ellipse cx="448" cy="46" rx="30" ry="13"/>
    <ellipse cx="700" cy="46" rx="40" ry="16"/><ellipse cx="736" cy="40" rx="26" ry="12"/>
    <ellipse cx="866" cy="88" rx="44" ry="17"/><ellipse cx="906" cy="82" rx="28" ry="12"/>
  </g>

  <path d="M0 232c150-26 300-30 470-14s360 8 554-16v98H0Z" fill="url(#hillA)"/>

  <g>
    <rect x="60" y="196" width="10" height="42" rx="5" fill="#A9754A"/>
    <path d="M65 200c-6-8-10-2-14-10" stroke="#A9754A" stroke-width="5" stroke-linecap="round"/>
    <circle cx="65" cy="172" r="42" fill="#9FD08A"/><circle cx="34" cy="192" r="26" fill="#8CC377"/>
    <circle cx="96" cy="192" r="24" fill="#B4DC9F"/>
    <rect x="160" y="212" width="8" height="30" rx="4" fill="#A9754A"/>
    <circle cx="164" cy="196" r="28" fill="#A8D694"/><circle cx="142" cy="208" r="18" fill="#93C87E"/>
  </g>
  <g fill="#93C87E">
    <ellipse cx="628" cy="238" rx="46" ry="26"/><ellipse cx="676" cy="244" rx="34" ry="20"/>
    <ellipse cx="944" cy="240" rx="42" ry="24"/>
  </g>

  <g transform="translate(742 84)">
    <path d="M46 0 33 40h26L46 0Z" fill="#F9E08E"/>
    <circle cx="46" cy="6" r="6" fill="#FBEBAE"/>
    <path d="M28 40h36l10 32H18l10-32Z" fill="url(#pagodaG)"/>
    <path d="M16 72h60l12 34H4l12-34Z" fill="url(#pagodaG)"/>
    <path d="M2 106h88l14 40H-12l14-40Z" fill="url(#pagodaG)"/>
    <rect x="-16" y="146" width="120" height="26" rx="5" fill="#EFC454"/>
    <rect x="30" y="150" width="28" height="22" rx="4" fill="#D9A62F"/>
    <g fill="url(#pagodaG)">
      <path d="M-40 118 -30 96l10 22h-20Z"/><path d="M-44 118h28l6 28h-40l6-28Z"/>
      <path d="M116 122 126 100l10 22h-20Z"/><path d="M112 122h28l6 26h-40l6-26Z"/>
    </g>
    <rect x="-52" y="148" width="24" height="24" rx="4" fill="#EFC454"/>
    <rect x="104" y="150" width="24" height="22" rx="4" fill="#EFC454"/>
  </g>

  <path d="M92 300c58-46 190-64 420-64s330 16 420 64H92Z" fill="#DFE4EC"/>
  <g stroke="#FFF" stroke-width="6" stroke-linecap="round" opacity=".9">
    <path d="M300 284h44"/><path d="M400 282h44"/><path d="M500 281h44"/><path d="M600 282h44"/>
  </g>

  <g transform="translate(236 96)">
    <rect x="6" y="10" width="330" height="150" rx="30" fill="#3B62C4"/>
    <rect x="6" y="10" width="330" height="150" rx="30" fill="#2F52B0" opacity=".35"/>
    <path d="M6 40c0-17 13-30 30-30h60v150H36c-17 0-30-13-30-30V40Z" fill="#4A72D6"/>
    <path d="M14 46c0-13 10-24 24-24h52v58H14V46Z" fill="#BFD8F5"/>
    <path d="M20 30c14-6 40-6 54 2-18-2-38-1-54-2Z" fill="#E8F2FF" opacity=".8"/>
    <g fill="#BFD8F5">
      <rect x="112" y="30" width="46" height="44" rx="9"/><rect x="166" y="30" width="46" height="44" rx="9"/>
      <rect x="220" y="30" width="46" height="44" rx="9"/><rect x="274" y="30" width="46" height="44" rx="9"/>
    </g>
    <rect x="112" y="86" width="208" height="8" rx="4" fill="#5B80DC" opacity=".7"/>
    <rect x="130" y="-2" width="76" height="20" rx="9" fill="#25409A"/>
    <text x="168" y="13" font-family="system-ui,sans-serif" font-size="13" font-weight="800"
          fill="#fff" text-anchor="middle">YBS</text>
    <rect x="10" y="120" width="18" height="14" rx="5" fill="#FBD24E"/>
    <rect x="316" y="120" width="16" height="12" rx="5" fill="#F2A24B"/>
    <circle cx="92" cy="164" r="24" fill="#2A3550"/><circle cx="92" cy="164" r="11" fill="#C2CCDC"/>
    <circle cx="266" cy="164" r="24" fill="#2A3550"/><circle cx="266" cy="164" r="11" fill="#C2CCDC"/>
  </g>
</svg>`;

/**
 * The tips scene: a family waiting at a stop. It fills the card behind the copy, which
 * is why the left half is deliberately empty - the sentence sits there.
 */
const FAMILY_ART = `<svg viewBox="0 0 1024 300" fill="none" aria-hidden="true"
     preserveAspectRatio="xMaxYMax slice">
  <defs>
    <linearGradient id="tipSky" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#FDF6E3"/><stop offset="1" stop-color="#FBF3DC"/>
    </linearGradient>
  </defs>
  <rect width="1024" height="300" fill="url(#tipSky)"/>
  <g fill="#FFFDF6" opacity=".9">
    <ellipse cx="470" cy="86" rx="90" ry="34"/><ellipse cx="556" cy="76" rx="60" ry="26"/>
    <ellipse cx="760" cy="60" rx="70" ry="28"/>
  </g>
  <path d="M0 214c170-30 340-34 520-16s330 10 504-14v116H0Z" fill="#CFE6AE"/>
  <path d="M0 246c190-24 360-24 520-8s330 6 504-14v76H0Z" fill="#BFDD9B"/>

  <g opacity=".85">
    <rect x="72" y="150" width="9" height="46" rx="4" fill="#B08258"/>
    <circle cx="76" cy="126" r="34" fill="#B7D9A2"/><circle cx="50" cy="146" r="21" fill="#A6CE90"/>
    <rect x="150" y="168" width="8" height="34" rx="4" fill="#B08258"/>
    <circle cx="154" cy="150" r="26" fill="#AFD59A"/>
  </g>
  <g fill="#A8D08E">
    <ellipse cx="600" cy="216" rx="54" ry="30"/><ellipse cx="880" cy="212" rx="48" ry="28"/>
  </g>

  <g transform="translate(900 96)">
    <rect x="26" y="34" width="9" height="112" fill="#9AA6B4"/>
    <rect x="0" y="0" width="62" height="52" rx="10" fill="#3C6FD0"/>
    <rect x="6" y="6" width="50" height="40" rx="7" fill="#EAF2FF"/>
    <rect x="15" y="17" width="32" height="18" rx="5" fill="#3C6FD0"/>
    <circle cx="21" cy="37" r="3" fill="#3C6FD0"/><circle cx="41" cy="37" r="3" fill="#3C6FD0"/>
  </g>
  <g transform="translate(778 190)">
    <rect x="0" y="0" width="128" height="12" rx="5" fill="#E0A552"/>
    <rect x="0" y="18" width="128" height="10" rx="4" fill="#D9973F"/>
    <rect x="6" y="28" width="9" height="30" fill="#B8802F"/>
    <rect x="113" y="28" width="9" height="30" fill="#B8802F"/>
  </g>

  <g transform="translate(628 76)">
    <path d="M14 172c0-30 16-52 40-52s40 22 40 52H14Z" fill="#3F63B8"/>
    <path d="M18 122c0-20 16-34 36-34s36 14 36 34l-6 30H24l-6-30Z" fill="#F2B93C"/>
    <path d="M40 84h28v18a14 14 0 0 1-28 0V84Z" fill="#F2C9A0"/>
    <circle cx="54" cy="62" r="27" fill="#F7D6B0"/>
    <path d="M27 60a27 27 0 0 1 54 0c0 12-4 30-8 30-3 0-3-16-6-20-6 6-20 8-30 4-4 8-4 16-6 16-3 0-4-18-4-30Z" fill="#8A5A34"/>
    <circle cx="45" cy="62" r="3" fill="#3B2A1C"/><circle cx="63" cy="62" r="3" fill="#3B2A1C"/>
    <circle cx="38" cy="70" r="4.5" fill="#F0A7A0" opacity=".6"/>
    <circle cx="70" cy="70" r="4.5" fill="#F0A7A0" opacity=".6"/>
    <path d="M47 72c4 4 10 4 14 0" stroke="#B5714A" stroke-width="2.4" stroke-linecap="round"/>
    <path d="M84 100c14-6 22-20 18-34" stroke="#F2B93C" stroke-width="15" stroke-linecap="round"/>
    <circle cx="100" cy="62" r="9" fill="#F7D6B0"/>
    <path d="M96 54v-9M101 53v-10M106 55v-8" stroke="#F7D6B0" stroke-width="4" stroke-linecap="round"/>
  </g>

  <g transform="translate(714 136)">
    <path d="M10 112c0-22 12-38 28-38s28 16 28 38H10Z" fill="#6B4A2E"/>
    <path d="M12 74c0-15 12-26 26-26s26 11 26 26l-4 22H16l-4-22Z" fill="#E4574C"/>
    <path d="M28 46h20v13a10 10 0 0 1-20 0V46Z" fill="#F2C9A0"/>
    <circle cx="38" cy="30" r="20" fill="#F7D6B0"/>
    <path d="M18 28a20 20 0 0 1 40 0c0 7-5 3-8 1a19 19 0 0 0-24 0c-3 2-8 6-8-1Z" fill="#3A2A1E"/>
    <circle cx="52" cy="14" r="6" fill="#3A2A1E"/>
    <circle cx="32" cy="31" r="2.6" fill="#3B2A1C"/><circle cx="45" cy="31" r="2.6" fill="#3B2A1C"/>
    <circle cx="26" cy="38" r="3.6" fill="#F0A7A0" opacity=".6"/>
    <circle cx="51" cy="38" r="3.6" fill="#F0A7A0" opacity=".6"/>
    <path d="M33 39c3 3 7 3 10 0" stroke="#B5714A" stroke-width="2" stroke-linecap="round"/>
    <path d="M62 70c11-4 16-14 14-24" stroke="#E4574C" stroke-width="12" stroke-linecap="round"/>
    <circle cx="76" cy="42" r="7.5" fill="#F7D6B0"/>
  </g>

  <g transform="translate(782 74)">
    <path d="M12 174c0-30 16-52 40-52s40 22 40 52H12Z" fill="#6B4A2E"/>
    <path d="M16 124c0-20 16-34 36-34s36 14 36 34l-6 32H22l-6-32Z" fill="#4E9E63"/>
    <path d="M46 122h12v34H46z" fill="#EDF3F8"/>
    <path d="M38 86h28v18a14 14 0 0 1-28 0V86Z" fill="#F2C9A0"/>
    <circle cx="52" cy="64" r="27" fill="#F7D6B0"/>
    <path d="M25 62a27 27 0 0 1 54 0c0 8-6 2-10-2a30 30 0 0 0-34 0c-4 4-10 10-10 2Z" fill="#D8DEE6"/>
    <path d="M27 58c4-16 14-24 25-24s21 8 25 24c-7-9-15-13-25-13s-18 4-25 13Z" fill="#EFF3F7"/>
    <circle cx="42" cy="64" r="8" stroke="#4A5568" stroke-width="2.4" fill="#fff" fill-opacity=".5"/>
    <circle cx="62" cy="64" r="8" stroke="#4A5568" stroke-width="2.4" fill="#fff" fill-opacity=".5"/>
    <path d="M50 64h4" stroke="#4A5568" stroke-width="2.4"/>
    <circle cx="34" cy="72" r="4.5" fill="#F0A7A0" opacity=".55"/>
    <circle cx="70" cy="72" r="4.5" fill="#F0A7A0" opacity=".55"/>
    <path d="M45 75c4 4 10 4 14 0" stroke="#B5714A" stroke-width="2.4" stroke-linecap="round"/>
    <path d="M22 104c-12 6-16 18-12 30" stroke="#4E9E63" stroke-width="15" stroke-linecap="round"/>
    <circle cx="9" cy="138" r="9" fill="#F7D6B0"/>
  </g>
</svg>`;

/* ---------------------------------------------------------------- roles ---
   Three audiences, three navs. A rider has no business on the admin screens and
   a driver has no business reading the other five drivers' numbers, so the role
   decides what the sidebar offers rather than everything being one long list
   with the sensitive things at the bottom.

   This is a demo, so the switch is open - it shapes the UI, it is not a security
   boundary, and the API is unauthenticated either way. Worth saying out loud in
   a viva rather than letting someone assume otherwise. */

const ROLES = {
    rider: {
        icon: '🧍',
        groups: [
            { label: 'nav.group.rider', items: [
                { view: 'plan', icon: '🚏', label: 'nav.plan' },
                { view: 'map', icon: '🗺️', label: 'nav.map' },
                { view: 'arrivals', icon: '⏱️', label: 'nav.arrivals' }
            ]},
            { label: 'nav.group.evidence', items: [
                { view: 'trace', icon: '🔍', label: 'nav.trace' },
                { view: 'prolog', icon: '🧠', label: 'nav.prolog' },
                { view: 'about', icon: 'ℹ️', label: 'nav.about' }
            ]}
        ]
    },
    driver: {
        icon: '🧑‍✈️',
        groups: [
            { label: 'nav.group.shift', items: [
                { view: 'shift', icon: '🎫', label: 'nav.shift' },
                { view: 'map', icon: '🗺️', label: 'nav.driverMap' },
                { view: 'arrivals', icon: '⏱️', label: 'nav.arrivals' }
            ]},
            { label: 'nav.group.operations', items: [
                { view: 'fleet', icon: '🚌', label: 'nav.fleet' }
            ]}
        ]
    },
    admin: {
        icon: '🛠️',
        groups: [
            { label: 'nav.group.data', items: [
                { href: '/admin.html', icon: '🛠️', label: 'nav.admin' },
                { href: '/ops.html', icon: '📊', label: 'nav.ops' }
            ]},
            { label: 'nav.group.operations', items: [
                { view: 'fleet', icon: '🚌', label: 'nav.fleet' },
                { view: 'map', icon: '🗺️', label: 'nav.map' }
            ]},
            { label: 'nav.group.evidence', items: [
                { view: 'prolog', icon: '🧠', label: 'nav.prolog' },
                { view: 'about', icon: 'ℹ️', label: 'nav.about' }
            ]}
        ]
    }
};

const ROLE_KEY = 'ybs.role';

function readStored(key, fallback) {
    try { return localStorage.getItem(key) ?? fallback; } catch (e) { return fallback; }
}
function writeStored(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* not worth failing over */ }
}

let role = ['rider', 'driver', 'admin'].includes(readStored(ROLE_KEY, 'rider'))
    ? readStored(ROLE_KEY, 'rider') : 'rider';

/** The views this role is allowed to land on, in nav order. */
function roleViews() {
    return ROLES[role].groups.flatMap(g => g.items.filter(i => i.view).map(i => i.view));
}

/** Simulation speed belongs to whoever is running the demo, not to a passenger. */
function applyRoleChrome() {
    const foot = document.getElementById('sim-controls');
    const label = foot && foot.previousElementSibling;
    const hide = role === 'rider';
    if (foot) foot.classList.toggle('hidden', hide);
    if (label && label.classList.contains('foot-label')) label.classList.toggle('hidden', hide);
}

function renderNav() {
    document.getElementById('nav').innerHTML = ROLES[role].groups.map(g => `
        ${t(g.label) ? `<div class="nav-group">${t(g.label)}</div>` : ''}
        ${g.items.map(i => i.href
            ? `<a class="nav-item" href="${i.href}"><span class="ic">${i.icon}</span><span>${t(i.label)}</span></a>`
            : `<a class="nav-item" href="#${i.view}" data-view="${i.view}"><span class="ic">${i.icon}</span><span>${t(i.label)}</span></a>`
        ).join('')}`).join('');
    markActiveNav(currentView);
}

function renderRoleSwitch() {
    const allowed = allowedRoles();
    // Locked roles stay visible rather than disappearing: an examiner needs to see that
    // there IS a driver view and that it is the login standing between them and it.
    document.getElementById('role-switch').innerHTML = ['rider', 'driver', 'admin'].map(r => {
        const locked = !allowed.includes(r);
        return `<button type="button" class="role-btn ${r === role ? 'on' : ''} ${locked ? 'locked' : ''}"
                        data-role="${r}" title="${locked ? t('auth.signInFor') : ''}">
            ${locked ? '<span class="lock" aria-hidden="true">\uD83D\uDD12</span>' : ''}
            <img class="role-photo" src="/img/role-${r}.png" alt=""
                 onerror="this.remove()" onload="this.parentElement.classList.add('has-photo')">
            ${ROLE_ART[r]}
            <span>${t('role.' + r)}</span>
         </button>`;
    }).join('');

    document.querySelectorAll('#role-switch .role-btn').forEach(b => {
        b.addEventListener('click', () => setRole(b.dataset.role));
    });
    renderIdentity();
}

/** Who is signed in, and the way out. Empty for a rider, who never signed in. */
function renderIdentity() {
    const el = document.getElementById('identity');
    if (!el) return;
    if (!session.authenticated) { el.innerHTML = ''; return; }
    el.innerHTML = `<div class="signed-in">
        <span class="who">${session.driverName || session.username}</span>
        <button type="button" id="signout-btn" class="alt-btn">${t('auth.signOut')}</button>
    </div>`;
    el.querySelector('#signout-btn').addEventListener('click', signOut);
}

function setRole(next) {
    if (!ROLES[next]) return;
    // A role this visitor has not signed in for shows the login instead of the view.
    // The check is a courtesy to the UI, not the protection: the server refuses the
    // data either way, which is what makes this a boundary rather than a suggestion.
    if (!allowedRoles().includes(next)) {
        pendingRole = next;
        showSignIn();
        return;
    }
    role = next;
    writeStored(ROLE_KEY, next);
    renderRoleSwitch();
    renderNav();
    applyRoleChrome();
    // Landing on a view this role does not offer would leave the sidebar with
    // nothing highlighted and the rider's planner on screen under a driver nav.
    if (!roleViews().includes(currentView)) {
        location.hash = '#' + roleViews()[0];
        showView(roleViews()[0]);
    }
}

let currentView = 'plan';

function markActiveNav(view) {
    document.querySelectorAll('.nav-item[data-view]').forEach(a => {
        a.classList.toggle('on', a.dataset.view === view);
    });
}

function showView(name) {
    const allowed = roleViews();
    const view = allowed.includes(name) ? name : allowed[0];
    currentView = view;
    document.querySelectorAll('.view').forEach(v => {
        v.classList.toggle('active', v.dataset.view === view);
    });
    markActiveNav(view);

    // Leaflet measures the container when it is created. If the map was hidden
    // at that moment it computes a zero size and renders as a grey box, so it
    // has to be told to re-measure every time this view becomes visible.
    if (view === 'map' && window._map) {
        setTimeout(() => { window._map.invalidateSize(); applyPendingFit(); }, 30);
    }
    if (view === 'fleet') renderFleet();
    if (view === 'arrivals') loadArrivalsBoard();
    if (view === 'shift') renderShift();
    if (view === 'map') applyMapRoleFraming();
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
        `${selected.size} / ${allRoutes.length}`;

    box.innerHTML = list.slice(0, 400).map(r => `
        <label class="route-toggle ${selected.has(r.code) ? 'on' : ''}">
            <input type="checkbox" data-code="${r.code}" ${selected.has(r.code) ? 'checked' : ''}>
            ${badge(r.code)}
            <span class="route-name">${routeName(r)}<em>${r.stopCount} ${t('map.stops')}</em></span>
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
        .bindPopup(`<b>${r.code}</b><br>${routeName(r)}<br>${r.stops.length} ${t('map.stops')}`);

    const markers = r.stops.map(s => {
        const m = L.circleMarker([s.lat, s.lon], {
            radius: 4, color: '#0B1220', weight: 1.5, fillColor: '#fff', fillOpacity: 1
        }).addTo(map);
        m.bindPopup(`<b>${stopName(s)}</b>`, { minWidth: 240 });
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
    // While a journey is drawn the legend belongs to the trip - it is the key to the
    // coloured lines on screen. Ticking a route off the picker must not silently
    // replace it with a list that explains nothing the rider is looking at.
    if (journeyLayer && map.hasLayer(journeyLayer)) return;
    legend.innerHTML = [...selected].map(code => {
        const r = allRoutes.find(x => x.code === code);
        return r ? `<div class="item"><span class="swatch" style="background:${r.colour}"></span>
                    <span><b>${r.code}</b></span></div>` : '';
    }).join('');
}

// Leaflet cannot frame bounds it cannot measure. While the map view is hidden the
// container is 0x0, and fitBounds then picks the zoom that fits those bounds in no
// pixels at all - the whole world. loadNetwork() runs at boot with 'plan' showing,
// so framing had to wait until the map is on screen. Until then the map keeps the
// Yangon view it was created with.
let pendingFit = null;

function frame(bounds, pad) {
    const el = document.getElementById('map');
    if (!el.offsetWidth || !el.offsetHeight) { pendingFit = { bounds, pad }; return; }
    pendingFit = null;
    setTimeout(() => { map.invalidateSize(); map.fitBounds(bounds.pad(pad)); }, 60);
}

/** Replays the framing that was asked for while the map was still hidden. */
function applyPendingFit() {
    if (!pendingFit) return;
    const { bounds, pad } = pendingFit;
    pendingFit = null;
    map.invalidateSize();
    map.fitBounds(bounds.pad(pad));
}

/** Frames the whole network, used when no individual route is drawn. */
function fitToCity(stops) {
    if (!stops.length) return;
    frame(L.latLngBounds(stops.map(s => [s.lat, s.lon])), 0.02);
}

function fitToSelection() {
    const lines = [...routeLayers.values()].map(l => l.line);
    if (!lines.length) return;
    const bounds = lines.reduce((b, l) => b ? b.extend(l.getBounds()) : l.getBounds(), null);
    frame(bounds, 0.05);
}

/* ---------------------------------------------------- arrivals (shared) --- */

function arrivalRows(arrivals) {
    if (!arrivals.length) {
        return `<div class="board-row muted">${t('arrivals.none')}</div>`;
    }
    return arrivals.map(a => `
        <div class="board-row">
            ${badge(a.routeCode)}
            <span class="board-eta">${a.etaMinutes < 1
                ? t('arrivals.arrived')
                : Math.round(a.etaMinutes) + ' ' + t('results.minutes')}</span>
            <span class="en">${a.etaLowerBound.toFixed(0)}–${a.etaUpperBound.toFixed(0)}</span>
            <span class="board-bus en">${t('arrivals.bus')} #${a.busId}</span>
        </div>`).join('');
}

async function loadStopPopup(stop, marker) {
    const head = `<div class="board-head"><b>${stopName(stop)}</b></div>`;
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
        el.innerHTML = `<div class="board-title">${stopName(
                            { nameMy: data.stopNameMy, nameEn: data.stopNameEn })}</div>
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
                ? `⏸ ${t('bus.dwelling')} ${bus.dwellSecondsRemaining}s`
                : `🚌 ${t('bus.driving')}`;
            const nextName = stopName({ nameMy: bus.nextStopNameMy, nameEn: bus.nextStopNameEn });
            const next = nextName
                ? `<br>→ ${nextName} · ${bus.minutesToNextStop} ${t('results.minutes')}` : '';
            marker.bindTooltip(
                `${t('arrivals.bus')} #${bus.id} — ${t('bus.route')} ${bus.routeCode}<br>${state}${next}`,
                { direction: 'top' });
        }
        if (currentView === 'fleet') renderFleet();
        if (currentView === 'shift') paintShift();
    } catch (err) {
        console.warn('bus poll failed', err);
    }
}

const CROWD = { 0: ['🟢', 'bus.crowd.free'], 1: ['🟠', 'bus.crowd.standing'], 2: ['🔴', 'bus.crowd.full'] };

function renderFleet() {
    const el = document.getElementById('fleet-table');
    const shown = busesOnlySelected ? latestBuses.filter(b => selected.has(b.routeCode)) : latestBuses;
    if (!shown.length) {
        el.innerHTML = `<div class="muted">${t('shift.noBuses')}</div>`;
        return;
    }
    el.innerHTML = `
      <table class="grid">
        <tr><th>${t('arrivals.bus')}</th><th>${t('bus.route')}</th><th></th>
            <th>${t('bus.next')}</th><th>ETA</th><th>${t('bus.crowding')}</th></tr>
        ${shown.map(b => busRow(b)).join('')}
      </table>`;
}

/** One fleet row. Shared with the driver's own screen, which lists the same buses. */
function busRow(b) {
    const [icon, crowdKey] = CROWD[b.crowdingLevel] || CROWD[2];
    const dwelling = b.state === 'DWELLING';
    return `<tr>
        <td><b>#${b.id}</b></td>
        <td>${badge(b.routeCode)}</td>
        <td><span class="state-pill ${dwelling ? 'waiting' : 'driving'}">
            ${dwelling ? `⏸ ${t('bus.dwelling')} ${b.dwellSecondsRemaining}s` : `🚌 ${t('bus.driving')}`}</span></td>
        <td>${stopName({ nameMy: b.nextStopNameMy, nameEn: b.nextStopNameEn }) || '—'}</td>
        <td>${b.minutesToNextStop != null ? b.minutesToNextStop + ' ' + t('results.minutes') : '—'}</td>
        <td>${icon} ${t(crowdKey)}</td>
    </tr>`;
}

function initSimControls() {
    const el = document.getElementById('sim-controls');
    el.className = 'speed-row';
    el.innerHTML = [1, 10, 60]
        .map(x => `<button type="button" class="speed-btn" data-x="${x}">${x}×</button>`).join('');
    el.querySelectorAll('.speed-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            const res = await post(`/api/sim/speed?multiplier=${btn.dataset.x}`);
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
    { key: 'pregnant',         label: 'profile.pregnant',  icon: '🤰', info: 'chip.i.pregnant' },
    { key: 'needs_seat',       label: 'profile.needsSeat', icon: '💺', info: 'chip.i.needsSeat' },
    { key: 'mobility_limited', label: 'profile.mobility',  icon: '♿', info: 'chip.i.mobility' },
    { key: 'student',          label: 'profile.student',   icon: '🎓', info: 'chip.i.student' },
    { key: 'verified_card',    label: 'profile.card',      icon: '🪪', info: 'chip.i.card' },
    { key: 'monk',             label: 'profile.monk',      icon: '🧘', info: 'chip.i.monk' }
];

const REASON_TEXT = {
    crowded_no_seat:           'reason.crowded',
    too_many_transfers:        'reason.transfers',
    overbridge_not_accessible: 'reason.overbridge',
    flooded_segment:           'reason.flooded'
};

const BLOCKER_TEXT = {
    headway_below_target:  'blocker.headway',
    too_many_harsh_events: 'blocker.harsh',
    upheld_complaint:      'blocker.complaint'
};

function activeFlags() {
    return [...document.querySelectorAll('#profile .chip.on')].map(c => c.dataset.key);
}

function initProfile() {
    const el = document.getElementById('profile');
    const on = new Set([...el.querySelectorAll('.chip.on')].map(c => c.dataset.key));
    el.innerHTML = PROFILE_FLAGS.map(f =>
        `<button type="button" class="chip ${on.has(f.key) ? 'on' : ''}" data-key="${f.key}">
            <span class="ic">${f.icon}</span><span>${t(f.label)}</span>
            <span class="chip-i" title="${t(f.info)}">i</span>
         </button>`).join('');
    el.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => { chip.classList.toggle('on'); runSearch(); });
    });

    document.getElementById('flood-btn').addEventListener('click', async () => {
        await post('/api/disruptions/flood?stopId=15&active=true');
        runSearch();
    });
    document.getElementById('clear-btn').addEventListener('click', async () => {
        await post('/api/disruptions/clear');
        runSearch();
    });
}

/* ------------------------------------------------------------------ search */

/* ------------------------------------------------- type-and-search stops ---
   This used to be a <select> holding all 2,080 stops. On the three-stop demo
   network that was fine; on the real one it is a scroll list several screens
   long, in two scripts, with genuine duplicates - Yangon has four stops called
   Nat Sin Lan - so finding one meant dragging a scrollbar and guessing.

   Each picker is a text box plus a hidden input. The hidden input holds the
   chosen stop id, so everything downstream still reads .value off the same
   element ids as before and nothing else had to change. Matching is on both
   scripts at once, because people type whichever is on their keyboard, and the
   township in brackets is what tells the four Nat Sin Lans apart - so it is
   always shown and always searched. */

const MAX_MATCHES = 40;
const pickers = new Map();   // hidden input id -> { input, list, stops }

function matchStops(term) {
    const q = term.trim().toLowerCase();
    if (!q) return networkStops.slice(0, MAX_MATCHES);
    const hits = [];
    for (const s of networkStops) {
        if ((s.nameEn || '').toLowerCase().includes(q) || (s.nameMy || '').includes(term.trim())) {
            hits.push(s);
            if (hits.length > MAX_MATCHES) break;
        }
    }
    return hits;
}

/** Both names, always. One of them is the language you are reading in; the other disambiguates. */
function stopLabel(s) {
    return lang() === 'en'
        ? `${s.nameEn || s.nameMy}<em>${s.nameMy || ''}</em>`
        : `${s.nameMy || s.nameEn}<em>${s.nameEn || ''}</em>`;
}

function initStopPicker(hiddenId, initialStopId, onPick) {
    const hidden = document.getElementById(hiddenId);
    const input = document.getElementById(hiddenId + '-input');
    const list = document.getElementById(hiddenId + '-list');
    if (!hidden || !input || !list) return;

    const state = { input, list, hidden, active: -1, shown: [], onPick };
    pickers.set(hiddenId, state);

    const close = () => {
        list.hidden = true;
        input.setAttribute('aria-expanded', 'false');
        state.active = -1;
    };

    const open = term => {
        state.shown = matchStops(term);
        if (!state.shown.length) {
            list.innerHTML = `<li class="stop-none">${t('stops.noMatch')}</li>`;
        } else {
            const rows = state.shown.slice(0, MAX_MATCHES).map((s, i) =>
                `<li role="option" data-i="${i}" data-id="${s.id}"
                     class="${i === state.active ? 'on' : ''}">${stopLabel(s)}</li>`).join('');
            const more = state.shown.length > MAX_MATCHES
                ? `<li class="stop-none">${t('stops.more')}</li>` : '';
            list.innerHTML = rows + more;
        }
        list.hidden = false;
        input.setAttribute('aria-expanded', 'true');
    };

    const choose = stop => {
        hidden.value = stop.id;
        input.value = stopName(stop);
        state.chosen = stop;
        close();
        if (onPick) onPick(stop);
    };

    input.addEventListener('input', () => { state.active = -1; open(input.value); });
    input.addEventListener('focus', () => open(input.value));
    input.addEventListener('blur', () => {
        // A click on a suggestion fires blur first, so let the click land.
        setTimeout(() => {
            close();
            // Typing something that matches nothing must not silently keep the old
            // stop while showing new text - put the chosen stop's name back.
            if (state.chosen) input.value = stopName(state.chosen);
        }, 150);
    });

    input.addEventListener('keydown', e => {
        if (list.hidden && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) { open(input.value); return; }
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault();
            const n = Math.min(state.shown.length, MAX_MATCHES);
            if (!n) return;
            state.active = e.key === 'ArrowDown'
                ? (state.active + 1) % n
                : (state.active - 1 + n) % n;
            [...list.querySelectorAll('li[data-i]')].forEach((li, i) =>
                li.classList.toggle('on', i === state.active));
            const on = list.querySelector('li.on');
            if (on) on.scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter') {
            if (!list.hidden && state.active >= 0) {
                e.preventDefault();
                choose(state.shown[state.active]);
            } else if (list.hidden && hidden.value) {
                runSearch();
            }
        } else if (e.key === 'Escape') {
            close();
        }
    });

    list.addEventListener('mousedown', e => {
        const li = e.target.closest('li[data-i]');
        if (li) choose(state.shown[Number(li.dataset.i)]);
    });

    const initial = networkStops.find(s => s.id === initialStopId) || networkStops[0];
    if (initial) choose(initial);
}

/** Puts a stop into a picker from elsewhere - the voice parse does this. */
function setPickerStop(hiddenId, stopId) {
    const state = pickers.get(hiddenId);
    const stop = networkStops.find(s => s.id === Number(stopId));
    if (!state || !stop) return;
    state.hidden.value = stop.id;
    state.chosen = stop;
    state.input.value = stopName(stop);
}

/** Re-labels every picker after a language switch, without losing the selection. */
function relabelPickers() {
    for (const state of pickers.values()) {
        if (state.chosen) state.input.value = stopName(state.chosen);
    }
}

function initSelectors(stops) {
    networkStops = stops;
    initStopPicker('from-stop', 1);          // Nat Sin, South Dagon
    initStopPicker('to-stop', 500);          // Yoke Shin Yon, Thaketa
    initStopPicker('arrivals-stop', 6, loadArrivalsBoard);
    document.getElementById('search-btn').addEventListener('click', runSearch);
}

async function runSearch() {
    const from = document.getElementById('from-stop').value;
    const to = document.getElementById('to-stop').value;
    const flags = activeFlags().map(f => `&profile=${f}`).join('');

    const panel = document.getElementById('results-panel');
    if (panel) panel.classList.remove('hidden');
    document.getElementById('results').textContent = t('results.searching');
    const data = await (await fetch(`/api/journeys/search?from=${from}&to=${to}${flags}`)).json();
    lastSearch = data;
    renderTrace(data);
    renderResults(data);
}

function renderTrace(data) {
    const saved = data.dijkstraExpansions - data.expansions;
    document.getElementById('search-stats').innerHTML =
        `<div class="stat hero"><b>${data.expansions}</b>${t('trace.expanded')}</div>` +
        `<div class="stat"><b>${data.dijkstraExpansions}</b>${t('trace.dijkstra')}</div>` +
        `<div class="stat"><b>${saved}</b>${t('trace.saved')}</div>` +
        `<div class="stat"><b>${data.bucket.replace('_', ' ')}</b>${t('trace.bucket')}</div>`;

    const panel = document.getElementById('trace-panel');
    panel.classList.remove('muted');
    // The parameter used to be called t, which now shadows the translation helper.
    panel.innerHTML = data.trace.map(x =>
        `<div><span class="ord">${String(x.order).padStart(3, ' ')}</span> ` +
        `${x.stopName}<span class="rc"> [${x.routeCode || t('trace.onFoot')}]</span> ` +
        `g=${x.g.toFixed(1)} h=${x.h.toFixed(1)} <b>f=${x.f.toFixed(1)}</b></div>`).join('');
    panel.scrollTop = 0;
}

function renderResults(data) {
    const el = document.getElementById('results');
    // The panel is hidden until a search has run: an empty box captioned "nothing yet"
    // is one more thing a first-time user has to decode before they can ignore it.
    const panel = document.getElementById('results-panel');
    if (panel) {
        panel.classList.remove('hidden');
        panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    el.classList.remove('muted', 'empty-state');
    document.getElementById('pareto-badge').textContent =
        data.options.length ? `${t('results.pareto')} ${data.options.length}` : '';

    if (!data.options.length) { el.textContent = t('results.none'); return; }

    // Which tariff row the rider is being charged on. The rule base decides the
    // number; this only names it, so a rider can see that ticking the card chip
    // is what moved the fare rather than having to diff two screens.
    const hasCard = activeFlags().includes('verified_card');

    el.innerHTML = data.options.map(o => {
        const j = o.journey;
        const badges = j.legs.map(l => l.kind === 'walk'
            ? `<span class="route-badge walk-badge">🚶</span>`
            : badge(l.routeCode)
        ).join('<span class="route-sep">›</span>');

        const verdict = o.suitable
            ? `<div class="verdict ok"><span class="ic">✔</span><span>${t('results.suitable')}</span></div>`
            : `<div class="verdict bad"><span class="ic">⚠</span><span>` +
              o.violationReasons.map(r => t(REASON_TEXT[r] || r)).join('<br>') + `</span></div>`;

        // Riding a route from its first stop to its last is charged at the higher
        // rate, so say so on the card rather than leaving an unexplained number.
        const endToEnd = j.legs.some(l => l.fullRoute);

        return `<div class="journey ${o.suitable ? '' : 'unsuitable'}">
            <div class="journey-top">
                <div>
                    <div class="fare">${o.riderFareKyats.toLocaleString()}</div>
                    <div class="fare-unit">${t('results.kyats')}</div>
                    <div class="fare-note">${t(hasCard ? 'results.fareCard' : 'results.fareCash')}</div>
                    ${endToEnd ? `<div class="fare-flag" title="${t('results.fullRouteHint')}">
                        ${t('results.fullRoute')}</div>` : ''}
                </div>
                <div class="journey-meta">
                    <div class="meta-row">
                        <span class="ic">⏱</span><span class="mins">${j.totalMinutes.toFixed(0)}</span>
                        <span>${t('results.minutes')}</span>
                        <span class="band">${j.etaLowerBound.toFixed(0)}–${j.etaUpperBound.toFixed(0)}</span>
                    </div>
                    <div class="meta-row">
                        <span class="ic">🔁</span>
                        <span>${j.transferCount} ${t('results.transfers')}</span>
                    </div>
                </div>
            </div>
            <div class="route-badges">${badges}</div>
            ${verdict}
            <button type="button" class="pick" data-id="${j.id}" ${o.suitable ? '' : 'disabled'}>
                ${o.suitable ? '🧭 ' + t('results.pick') : '🚫 ' + t('results.unavailable')}
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

/* --------------------------------------------------------- drawing a trip ---
   One line per LEG, each in its own route's colour, rather than one dark line for
   the whole journey. The old single polyline made a two-bus trip look continuous:
   the rider could see the shape of the journey but not the moment that actually
   matters to them, which is where they have to get off one bus and find another.

   The colours are not decorative. They are the same ones the route filter, the bus
   markers and every route badge use, so the pink line on the map is the pink badge
   on the result card and the pink bus driving along it. A change of colour is a
   change of bus, and that is the whole point.

   Each ride line gets a dark casing underneath it. Yangon's map is busy and several
   route colours are light; without the casing a yellow line over a yellow road is
   invisible exactly when someone is trying to follow it. */

let journeyLayer = null;

function legPoints(leg) {
    const ids = [leg.fromStopId, ...(leg.viaStopIds || [])];
    return ids
        .map(id => networkStops.find(s => s.id === id))
        .filter(Boolean)
        .map(s => [s.lat, s.lon]);
}

function drawJourney(journey) {
    if (journeyLayer) map.removeLayer(journeyLayer);
    journeyLayer = L.layerGroup().addTo(map);

    journey.legs.forEach(leg => {
        const pts = legPoints(leg);
        if (pts.length < 2) return;

        if (leg.kind === 'walk') {
            // Walking is not a bus, so it must not look like one: no colour, and dashed,
            // which is the one convention a transit map can rely on being read correctly.
            L.polyline(pts, {
                color: '#475569', weight: 4, opacity: 0.9, dashArray: '2 9', lineCap: 'round'
            }).addTo(journeyLayer);
            return;
        }

        const colour = routeColour(leg.routeCode);
        L.polyline(pts, { color: '#0B1220', weight: 11, opacity: 0.35 }).addTo(journeyLayer);
        L.polyline(pts, { color: colour, weight: 7, opacity: 0.95, lineCap: 'round' })
            .addTo(journeyLayer)
            .bindTooltip(`${t('bus.route')} ${leg.routeCode}`, { sticky: true });
    });

    drawTransferMarkers(journey);
    renderTripLegend(journey);

    const bounds = journey.legs.flatMap(legPoints);
    if (bounds.length) frame(L.latLngBounds(bounds), 0.15);
}

/**
 * A marker at every stop where the rider changes bus, plus the two ends of the trip.
 * The colour change alone says a change happens; the marker says exactly where, which
 * is the question someone actually asks of a journey plan.
 */
function drawTransferMarkers(journey) {
    const rides = journey.legs.filter(l => l.kind === 'ride');

    const pin = (stopId, html, cls) => {
        const stop = networkStops.find(s => s.id === stopId);
        if (!stop) return;
        L.marker([stop.lat, stop.lon], {
            icon: L.divIcon({ className: '', html: `<div class="trip-pin ${cls}">${html}</div>`,
                              iconSize: [26, 26], iconAnchor: [13, 13] }),
            zIndexOffset: 1000
        }).addTo(journeyLayer).bindTooltip(stopName(stop), { direction: 'top' });
    };

    // Where one bus ends and the next begins. The alighting stop of leg i and the
    // boarding stop of leg i+1 can differ by a short walk, so both are marked.
    for (let i = 0; i < rides.length - 1; i++) {
        pin(rides[i].toStopId, '\u21C4', 'transfer');
        if (rides[i + 1].fromStopId !== rides[i].toStopId) {
            pin(rides[i + 1].fromStopId, '\u21C4', 'transfer');
        }
    }

    if (journey.stopIds.length) {
        pin(journey.stopIds[0], '\u25CF', 'start');
        pin(journey.stopIds[journey.stopIds.length - 1], '\u25A0', 'end');
    }
}

/**
 * The legend becomes the trip's own key while a journey is drawn: the routes in the
 * order they are ridden, with the change points called out between them. The route
 * picker's legend comes back when the trip is cleared.
 */
function renderTripLegend(journey) {
    const legend = document.getElementById('legend');
    if (!legend) return;
    const parts = journey.legs.map(l => l.kind === 'walk'
        ? `<div class="item"><span class="swatch walk"></span><span>${t('trace.onFoot')}</span></div>`
        : `<div class="item"><span class="swatch" style="background:${routeColour(l.routeCode)}"></span>
             <span><b>${l.routeCode}</b></span></div>`);

    const changes = journey.transferCount > 0
        ? `<div class="item change-note">\u21C4 ${journey.transferCount} ${t('results.transfers')}</div>`
        : '';
    legend.innerHTML = parts.join('<span class="leg-sep">\u203A</span>') + changes;
}

/* ------------------------------------------------------------ Prolog console */

const GOAL_PRESETS = [
    ["fare(m1,'43',F)", 'prolog.preset.fare'],
    ["fare(m1,'43',full,F)", 'prolog.preset.fareFull'],
    ["assert(verified_card(m2)),fare(m2,'43',F)", 'prolog.preset.card'],
    ["assert(monk(m1)),fare(m1,'43',F)", 'prolog.preset.monk'],
    ["concession(m1,C)", 'prolog.preset.concession'],
    ["journey_fare(j1,rider1,F)", 'prolog.preset.journeyFare'],
    ["pareto_optimal(J)", 'prolog.preset.pareto'],
    ["dominates(A,B)", 'prolog.preset.dominates'],
    ["transfer_point(S)", 'prolog.preset.transferPoint'],
    ["suitable(j1,rider1)", 'prolog.preset.suitable'],
    ["violation_reason(j1,rider1,Why)", 'prolog.preset.why'],
    ["bonus_eligible(d1,'2026-08')", 'prolog.preset.bonus'],
    ["bonus_blocker(d4,'2026-08',Why)", 'prolog.preset.blocker'],
    ["top_headway('2026-08',D)", 'prolog.preset.topHeadway'],
    ["contradictory_bonus(D,M)", 'prolog.preset.selfCheck']
];

/**
 * The preset buttons carry translated labels, so they are rebuilt on a language
 * switch. Their innerHTML is replaced wholesale, which discards the old listeners
 * with the old elements.
 */
function renderPresets() {
    const input = document.getElementById('goal-input');
    document.getElementById('goal-presets').innerHTML = GOAL_PRESETS
        .map(([g, label], i) => `<button type="button" class="preset" data-i="${i}">
            <code>${g.replace(/</g, '&lt;')}</code><em>${t(label)}</em></button>`).join('');

    document.querySelectorAll('#goal-presets .preset').forEach(b => {
        b.addEventListener('click', () => {
            input.value = GOAL_PRESETS[b.dataset.i][0];
            runGoal();
        });
    });
}

/**
 * Wired once, at boot. #goal-run and #goal-input survive every re-render, so
 * binding them from the language handler would stack a second click listener on
 * each switch and fire the goal twice - and would also wipe whatever the examiner
 * had half-typed into the box.
 */
function initProlog() {
    const input = document.getElementById('goal-input');
    input.value = GOAL_PRESETS[0][0];
    renderPresets();
    document.getElementById('goal-run').addEventListener('click', runGoal);
    input.addEventListener('keydown', e => { if (e.key === 'Enter') runGoal(); });
}

async function runGoal() {
    const goal = document.getElementById('goal-input').value.trim();
    const out = document.getElementById('goal-output');
    if (!goal) return;
    out.classList.remove('muted');
    out.innerHTML = `<div class="echo">?- ${goal}</div><div class="muted">${t('prolog.solving')}</div>`;

    try {
        const d = await (await fetch('/api/prolog/query?goal=' + encodeURIComponent(goal))).json();
        let body;
        if (d.error) {
            body = `<div class="sol err">${d.error}</div>`;
        } else if (!d.proved) {
            body = `<div class="sol no">${t('prolog.false')}</div>`;
        } else if (!d.solutions.length || d.solutions.every(s => !Object.keys(s).length)) {
            // A ground goal has nothing to bind; proving it IS the answer.
            body = `<div class="sol yes">true</div>`;
        } else {
            body = d.solutions.map((s, i) =>
                `<div class="sol yes"><span class="n">${i + 1}.</span> ` +
                Object.entries(s).map(([k, v]) => `<code>${k} = ${v}</code>`).join(', ') +
                `</div>`).join('');
        }
        const count = d.proved
            ? `<div class="sol-count en">${d.solutions.length} ${t('prolog.solutions')}</div>` : '';
        out.innerHTML = `<div class="echo">?- ${goal}</div>${body}${count}`;
    } catch (err) {
        out.innerHTML = `<div class="echo">?- ${goal}</div><div class="sol err">${t('prolog.failed')}</div>`;
    }
}

/* ------------------------------------------------------------------- about */

const TOPICS = [
    ['🔍', 'about.search', 'planning/', '#trace'],
    ['🧠', 'about.kr', 'reasoning/', '#prolog'],
    ['🚦', 'about.csp', 'reasoning/', '#plan'],
    ['🎲', 'about.uncertainty', 'eta/', '#arrivals'],
    ['🗣️', 'about.nlp', 'voice/', '#plan']
];

function initAbout() {
    document.getElementById('about-grid').innerHTML = TOPICS.map(([ic, key, pkg, href]) => `
        <a class="topic" href="${href}">
            <div class="topic-ic">${ic}</div>
            <div class="topic-title">${t(key + '.title')}</div>
            <code class="topic-pkg">${pkg}</code>
            <p>${t(key + '.body')}</p>
        </a>`).join('') + `
        <div class="topic flat">
            <div class="topic-ic">📐</div>
            <div class="topic-title">${t('about.oneProcess.title')}</div>
            <p>${t('about.oneProcess.body')}</p>
        </div>`;
}

/* ----------------------------------------------------------------- sign in ---
   Spring Security's own filter handles the credentials at /api/auth/login, so no
   password passes through code in this file. It answers in JSON rather than with a
   302, because a redirect arriving at fetch() is opaque and the page could not act
   on it. */

let pendingRole = null;

function showSignIn() {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.querySelector('.view[data-view="signin"]').classList.add('active');
    markActiveNav(null);
    const err = document.getElementById('signin-error');
    if (err) err.textContent = '';
    const user = document.getElementById('signin-user');
    if (user) user.focus();
}

async function signIn(event) {
    if (event) event.preventDefault();
    const username = document.getElementById('signin-user').value.trim();
    const password = document.getElementById('signin-pass').value;
    const err = document.getElementById('signin-error');
    err.textContent = '';

    // Form-encoded, because that is what Spring Security's login filter reads.
    const body = new URLSearchParams({ username, password });
    let data;
    try {
        const res = await post('/api/auth/login', {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body
        });
        data = await res.json();
    } catch (e) {
        err.textContent = t('auth.failed');
        return;
    }

    if (!data.authenticated) {
        // One message for a wrong username and a wrong password alike - telling them
        // apart tells an attacker which accounts exist.
        err.textContent = t('auth.badCredentials');
        document.getElementById('signin-pass').value = '';
        return;
    }

    session = data;
    document.getElementById('signin-pass').value = '';
    const target = pendingRole && allowedRoles().includes(pendingRole)
        ? pendingRole
        : (session.role === 'ADMIN' ? 'admin' : session.role === 'DRIVER' ? 'driver' : 'rider');
    pendingRole = null;
    shift = null;
    renderRoleSwitch();
    setRole(target);
}

async function signOut() {
    try {
        await post('/api/auth/logout');
    } catch (e) {
        // Even if the call fails, drop what this page knows - leaving a stale identity
        // on screen after a sign-out is worse than a wasted request.
    }
    session = { authenticated: false, role: 'RIDER' };
    shift = null;
    renderRoleSwitch();
    setRole('rider');
}

function initSignIn() {
    const form = document.getElementById('signin-form');
    if (form) form.addEventListener('submit', signIn);
}

/* ------------------------------------------------------- driver: my shift ---
   The ops dashboard answers "which of my drivers earn the bonus". A driver
   asking about their own month wants the opposite - not the ranking, but the
   reason - so this screen shows one driver and asks the engine both directions:
   bonus_eligible/2 for the verdict, bonus_blocker/3 for what withheld it.

   Buses come from the same 1 Hz feed the map polls, filtered to this driver's
   route, rather than from a second endpoint that could disagree with it. */

let shift = null;

async function renderShift() {
    const el = document.getElementById('shift-body');
    if (!el) return;
    try {
        // No id in the URL, on purpose. The server reads which driver this is from the
        // session; there is nothing here for anyone to change to somebody else's number.
        const res = await fetch('/api/ops/me/shift');
        if (res.status === 401 || res.status === 403) {
            session = { authenticated: false, role: 'RIDER' };
            renderRoleSwitch();
            showSignIn();
            return;
        }
        shift = await res.json();
    } catch (err) {
        el.innerHTML = `<div class="card muted">${t('prolog.failed')}</div>`;
        return;
    }
    paintShift();
}

/** Re-renders from the shift already fetched - used by the 1 Hz bus poll and by a language switch. */
function paintShift() {
    const el = document.getElementById('shift-body');
    if (!el || !shift || !shift.driverId) return;

    const month = (shift.months || [])[0];
    const buses = latestBuses.filter(b => b.routeCode === shift.routeCode);

    const identity = `<section class="card shift-id">
        <div class="shift-who">
            <div class="shift-avatar">🧑‍✈️</div>
            <div>
                <div class="shift-name">${shift.driverName}</div>
                <div class="shift-route">${t('shift.route')}:
                    ${shift.routeCode ? badge(shift.routeCode) : '—'}
                    <span class="en">${shift.routeNameMy || ''}</span></div>
            </div>
        </div>
    </section>`;

    let bonus = '';
    if (month) {
        const blockers = month.blockers || [];
        bonus = `<section class="card">
            <div class="card-head"><span class="ic">🏅</span><h2>${t('shift.bonus')} · ${month.month}</h2></div>
            <div class="stats">
                <div class="stat"><b>${month.headwayScore}</b>${t('shift.headway')}</div>
                <div class="stat"><b>${month.harshEvents}</b>${t('shift.harsh')}</div>
                <div class="stat"><b>${month.upheldComplaints ?? 0}</b>${t('shift.complaints')}</div>
            </div>
            <div class="verdict ${month.bonusEligible ? 'ok' : 'bad'}">
                <span class="ic">${month.bonusEligible ? '✔' : '⚠'}</span>
                <span><b>${t(month.bonusEligible ? 'shift.eligible' : 'shift.notEligible')}</b></span>
            </div>
            ${month.bonusEligible
                ? `<p class="hint">${t('shift.becauseOk')}</p>`
                : `<div class="blockers"><div class="blockers-head">${t('shift.blockedBy')}</div>
                     ${blockers.map(b => `<div class="blocker">🚫 ${t(BLOCKER_TEXT[b] || b)}</div>`).join('')}
                   </div>`}
        </section>`;
    }

    const bunching = (shift.bunching || []).map(a => `
        <div class="disruption-row alert">
            <span class="ic">🚨</span>
            <div class="grow">
                <div class="my">${lang() === 'en' ? a.messageEn : a.messageMy}</div>
                <div class="en">${a.action}</div>
            </div>
        </div>`).join('');

    const busTable = `<section class="card">
        <div class="card-head"><span class="ic">🚌</span><h2>${t('shift.myBuses')}</h2></div>
        ${bunching}
        ${buses.length
            ? `<table class="grid">
                 <tr><th>${t('arrivals.bus')}</th><th>${t('bus.route')}</th><th></th>
                     <th>${t('bus.next')}</th><th>ETA</th><th>${t('bus.crowding')}</th></tr>
                 ${buses.map(b => busRow(b)).join('')}
               </table>`
            : `<div class="muted">${t('shift.noBuses')}</div>`}
    </section>`;

    const incidents = shift.incidents || [];
    const incidentCard = `<section class="card">
        <div class="card-head"><span class="ic">📈</span><h2>${t('shift.incidents')}</h2></div>
        ${incidents.length
            ? `<table class="grid">
                 ${incidents.map(i => `<tr>
                     <td>${String(i.hour).padStart(2, '0')}:00</td>
                     <td><b>${i.type}</b></td>
                     <td>${stopName({ nameMy: i.stopNameMy, nameEn: i.stopNameEn }) || '—'}</td>
                   </tr>`).join('')}
               </table>`
            : `<div class="muted">${t('shift.noIncidents')}</div>`}
    </section>`;

    el.innerHTML = identity + bonus + busTable + incidentCard;
}

/* --------------------------------------------------------- map, per role ---
   A driver opening the map wants their own line, not a picker over all 118. The
   map itself is one Leaflet instance shared by every role - a second one would
   mean a second copy of 2,080 stops - so the role changes what is framed and
   what the picker offers, not which map is on screen. */

function applyMapRoleFraming() {
    const picker = document.getElementById('route-picker-card');
    const isDriver = role === 'driver';
    picker.classList.toggle('hidden', isDriver);
    document.getElementById('map-title').textContent = t(isDriver ? 'nav.driverMap' : 'map.h1');
    document.getElementById('map-lede').textContent = t(isDriver ? 'shift.driverMapLede' : 'map.lede');

    if (!isDriver || !shift || !shift.routeCode) return;
    // Show exactly this driver's route, and nothing anyone else is running.
    [...selected].filter(c => c !== shift.routeCode).forEach(c => { selected.delete(c); hideRoute(c); });
    if (!selected.has(shift.routeCode)) {
        selected.add(shift.routeCode);
        showRoute(shift.routeCode);
    }
}

/* ------------------------------------------------------------------- voice */

/* ------------------------------------------------------ interactive voice ---
   The rider talks; the browser records, encodes and uploads; MMS reads it back.

   Three things here are not obvious and are worth the words.

   **Why the browser encodes a WAV.** MediaRecorder gives WebM/Opus, and wav2vec2
   wants 16 kHz mono PCM. Transcoding on the server would mean an ffmpeg on the
   PATH - a second install, on a project whose whole promise is one command and no
   dependencies. Decoding through an AudioContext and writing the header here is
   about forty lines and keeps the server contract as "post a wav", which is also
   what the packaged samples post.

   **Why the resample is a plain average.** Browsers capture at 44.1 or 48 kHz.
   Dropping to 16 kHz by averaging each source window is a box filter: cheap, and
   it low-passes as it goes, which is what stops the aliasing that naive
   pick-every-third-sample would fold into the speech band.

   **Why there is no fallback transcript.** If the recogniser is down the samples
   still answer from memory, because their sentence is known in advance. Live
   speech has no such sentence, so this path reports the failure instead. Replying
   with someone else's words would invent an understanding that never happened and
   route the rider somewhere they never asked for. */

const MIC_SAMPLE_RATE = 16000;
const MIC_MIN_MS = 400;
const MIC_MAX_MS = 15000;

let micState = 'idle';          // idle | recording | working
let micRecorder = null;
let micChunks = [];
let micStream = null;
let micStopTimer = null;
let micMeterRaf = null;

function micStatus(key, isError) {
    const el = document.getElementById('voice-status');
    if (!el) return;
    el.textContent = t(key);
    el.classList.toggle('voice-error', !!isError);
    el.classList.toggle('muted', !isError);
}

function setMicState(next) {
    micState = next;
    const btn = document.getElementById('mic-btn');
    if (!btn) return;
    btn.classList.toggle('recording', next === 'recording');
    btn.classList.toggle('working', next === 'working');
    btn.disabled = next === 'working';
    const label = btn.querySelector('.mic-label');
    if (label) {
        label.textContent = t(next === 'recording' ? 'voice.recording'
                           : next === 'working' ? 'voice.thinking'
                           : 'voice.hold');
    }
}

/** Live input level, so a rider can see the mic is hearing them before they trust it. */
function startMeter(stream) {
    const bar = document.querySelector('#mic-meter span');
    if (!bar) return;
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const analyser = ctx.createAnalyser();
    analyser.fftSize = 512;
    ctx.createMediaStreamSource(stream).connect(analyser);
    const buf = new Uint8Array(analyser.frequencyBinCount);

    const tick = () => {
        analyser.getByteTimeDomainData(buf);
        let peak = 0;
        for (const v of buf) peak = Math.max(peak, Math.abs(v - 128));
        bar.style.width = Math.min(100, (peak / 128) * 260) + '%';
        micMeterRaf = requestAnimationFrame(tick);
    };
    tick();
    return () => { cancelAnimationFrame(micMeterRaf); ctx.close(); bar.style.width = '0%'; };
}

let stopMeter = null;

async function toggleMic() {
    if (micState === 'recording') { stopRecording(); return; }
    if (micState === 'working') return;

    if (!navigator.mediaDevices || !window.MediaRecorder) {
        micStatus('voice.micMissing', true);
        return;
    }

    try {
        micStream = await navigator.mediaDevices.getUserMedia({
            audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
    } catch (err) {
        // NotAllowedError is a refused prompt; NotFoundError is a machine with no mic.
        micStatus(err && err.name === 'NotFoundError' ? 'voice.micMissing' : 'voice.micDenied', true);
        return;
    }

    micChunks = [];
    micRecorder = new MediaRecorder(micStream);
    micRecorder.ondataavailable = e => { if (e.data.size) micChunks.push(e.data); };
    micRecorder.onstop = finishRecording;
    micRecorder.start();

    stopMeter = startMeter(micStream);
    setMicState('recording');
    micStatus('voice.recording');
    document.getElementById('voice-entities').innerHTML = '';

    // A recorder left running because someone walked away must not upload minutes of room
    // tone, so it stops itself.
    micStopTimer = setTimeout(stopRecording, MIC_MAX_MS);
}

function stopRecording() {
    clearTimeout(micStopTimer);
    if (micRecorder && micRecorder.state !== 'inactive') micRecorder.stop();
}

async function finishRecording() {
    if (stopMeter) { stopMeter(); stopMeter = null; }
    micStream.getTracks().forEach(tr => tr.stop());
    setMicState('working');
    micStatus('voice.thinking');

    try {
        const blob = new Blob(micChunks, { type: micRecorder.mimeType });
        const wav = await blobToWav(blob);
        if (wav.durationMs < MIC_MIN_MS) { setMicState('idle'); micStatus('voice.tooShort', true); return; }

        const body = new FormData();
        body.append('audio', wav.blob, 'speech.wav');

        // No Content-Type header: the browser has to set it, because only it knows the
        // multipart boundary it just generated.
        const res = await post(`/api/voice/transcribe?lang=${lang()}`, { body });
        const data = await res.json();

        if (!res.ok) {
            micStatus(data.reason === 'silent' ? 'voice.silent' : 'voice.noModel', true);
            setMicState('idle');
            return;
        }
        showVoiceResult(data);
    } catch (err) {
        console.warn('live voice failed', err);
        micStatus('voice.noModel', true);
    } finally {
        setMicState('idle');
    }
}

/** Decode whatever the recorder produced, downmix, resample and write a 16-bit WAV. */
async function blobToWav(blob) {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const decoded = await ctx.decodeAudioData(await blob.arrayBuffer());

    // Downmix to mono first: averaging the channels keeps anything panned off-centre,
    // which taking channel 0 alone would silently drop.
    const chans = [];
    for (let c = 0; c < decoded.numberOfChannels; c++) chans.push(decoded.getChannelData(c));
    const mono = new Float32Array(decoded.length);
    for (let i = 0; i < decoded.length; i++) {
        let sum = 0;
        for (const ch of chans) sum += ch[i];
        mono[i] = sum / chans.length;
    }

    const ratio = decoded.sampleRate / MIC_SAMPLE_RATE;
    const outLen = Math.floor(mono.length / ratio);
    const out = new Float32Array(outLen);
    for (let i = 0; i < outLen; i++) {
        // Average the whole source window rather than sampling one point of it - that
        // averaging IS the anti-alias filter.
        const from = Math.floor(i * ratio);
        const to = Math.min(mono.length, Math.floor((i + 1) * ratio));
        let sum = 0;
        for (let j = from; j < to; j++) sum += mono[j];
        out[i] = to > from ? sum / (to - from) : 0;
    }
    ctx.close();

    const buffer = new ArrayBuffer(44 + outLen * 2);
    const view = new DataView(buffer);
    const ascii = (off, str) => { for (let i = 0; i < str.length; i++) view.setUint8(off + i, str.charCodeAt(i)); };

    ascii(0, 'RIFF');
    view.setUint32(4, 36 + outLen * 2, true);
    ascii(8, 'WAVEfmt ');
    view.setUint32(16, 16, true);            // PCM header size
    view.setUint16(20, 1, true);             // format 1 = PCM
    view.setUint16(22, 1, true);             // mono
    view.setUint32(24, MIC_SAMPLE_RATE, true);
    view.setUint32(28, MIC_SAMPLE_RATE * 2, true);   // byte rate
    view.setUint16(32, 2, true);             // block align
    view.setUint16(34, 16, true);            // bits per sample
    ascii(36, 'data');
    view.setUint32(40, outLen * 2, true);
    for (let i = 0; i < outLen; i++) {
        const clamped = Math.max(-1, Math.min(1, out[i]));
        view.setInt16(44 + i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7FFF, true);
    }

    return {
        blob: new Blob([buffer], { type: 'audio/wav' }),
        durationMs: (outLen / MIC_SAMPLE_RATE) * 1000
    };
}

/** Shared by the live path and the sample path - the result looks the same either way. */
function showVoiceResult(res) {
    const status = document.getElementById('voice-status');
    const chips = document.getElementById('voice-entities');
    status.classList.remove('voice-error');
    status.classList.add('muted');
    status.innerHTML = `<b>“${res.transcript}”</b> <span class="en">${res.mode}</span>`;

    chips.innerHTML =
        `<span class="entity"><span class="mark">${t('voice.from')}</span>` +
        `<b>${res.originName || '?'}</b></span>` +
        `<span class="entity"><span class="mark">${t('voice.to')}</span>` +
        `<b>${res.destName || '?'}</b></span>`;

    if (res.understood) {
        setPickerStop('from-stop', res.originStopId);
        setPickerStop('to-stop', res.destStopId);
        runSearch();
    } else {
        status.innerHTML += ` — <span class="en">${t('voice.notUnderstood')}</span>`;
    }
}

/* -------------------------------------------------------------------- voice ---
   The samples and the transcription both follow the page's language: the API is
   asked for `lang`, which picks the recording, the MMS language adapter, and which
   half of the entity extractor parses what comes back. Switching language therefore
   has to reload the sample list, which is why loadVoiceSamples() is separate from
   the one-time wiring in initVoice(). */

async function loadVoiceSamples() {
    const select = document.getElementById('voice-sample');
    if (!select) return;
    const keep = select.value;
    try {
        const data = await (await fetch(`/api/voice/samples?lang=${lang()}`)).json();
        select.innerHTML = Object.entries(data.samples)
            .map(([k, text]) => `<option value="${k}">${text}</option>`).join('');
        if (keep) select.value = keep;
    } catch (err) {
        console.warn('voice samples unavailable', err);
    }
}

async function initVoice() {
    const select = document.getElementById('voice-sample');
    await loadVoiceSamples();

    document.getElementById('voice-btn').addEventListener('click', async () => {
        const status = document.getElementById('voice-status');
        document.getElementById('voice-entities').innerHTML = '';
        status.classList.remove('voice-error');
        status.textContent = '\u{1F3A7} ' + t('voice.listening');

        const res = await (await post(
            `/api/voice/query?sample=${select.value}&lang=${lang()}`)).json();
        showVoiceResult(res);
    });

    const mic = document.getElementById('mic-btn');
    if (mic) {
        mic.addEventListener('click', toggleMic);
        setMicState('idle');
    }
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
                const dueName = due
                    ? stopName({ nameMy: due.stopNameMy, nameEn: due.stopNameEn }) : '';
                el.innerHTML = due
                    ? `🚌 ${dueName} ${t('alert.until')} <b>${Math.round(due.etaMinutes)}</b> ${t('results.minutes')}
                       <span class="en">(${due.etaLowerBound.toFixed(0)}–${due.etaUpperBound.toFixed(0)} ·
                       ${Math.round(s.distanceMetres)} ${t('alert.away')})</span>`
                    : `🚌 ${Math.round(s.distanceMetres)} ${t('alert.away')}`;
            }
        }
    } catch (err) {
        console.warn('alert poll failed', err);
    }
}

function showArrival(state) {
    document.getElementById('arrival-stop').textContent =
        stopName({ nameMy: state.stopNameMy, nameEn: state.stopNameEn });
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
        await post('/api/alert/cancel');
        watchedBusId = null;
    });
}

async function startTrip(journey) {
    const routeCode = journey.routeCodes[0];
    const buses = await (await fetch('/api/buses/live')).json();
    const bus = buses.find(b => b.routeCode === routeCode);
    if (!bus) return;
    const alightStopId = journey.stopIds[journey.stopIds.length - 1];
    await post(`/api/alert/start?busId=${bus.id}&stopId=${alightStopId}`);
    watchedBusId = bus.id;
    const last = journey.legs.at(-1);
    const alightName = stopName({ nameMy: last.toNameMy, nameEn: last.toNameEn });
    document.getElementById('trip-countdown').innerHTML =
        `🚌 ${t('alert.watching')} #${bus.id} — ${alightName} ${t('alert.alightAt')}`;
}

/* ---------------------------------------------------------- preferences ---
   Dark mode and text size, in the sidebar rather than behind a settings screen.
   Both persist, because someone who needs larger type needs it every visit and
   should not have to ask twice. */

const THEME_KEY = 'ybs.theme';
const TEXT_KEY = 'ybs.text';
const TEXT_STEPS = { sm: '13.5px', md: '15px', lg: '17.5px' };

function applyTheme(dark) {
    document.body.classList.toggle('dark', dark);
    const glyph = document.getElementById('theme-glyph');
    const label = document.getElementById('theme-label');
    if (glyph) glyph.textContent = dark ? '\u{1F319}' : '\u2600\uFE0F';
    if (label) label.textContent = t(dark ? 'pref.dark' : 'pref.light');
}

function applyTextSize(step) {
    document.documentElement.style.setProperty('--text', TEXT_STEPS[step] || TEXT_STEPS.md);
    document.querySelectorAll('#text-size button').forEach(b => {
        b.classList.toggle('on', b.dataset.step === step);
    });
}

function initPrefs() {
    const dark = readStored(THEME_KEY, 'light') === 'dark';
    applyTheme(dark);

    const toggle = document.getElementById('theme-toggle');
    if (toggle) {
        toggle.addEventListener('click', () => {
            const next = !document.body.classList.contains('dark');
            writeStored(THEME_KEY, next ? 'dark' : 'light');
            applyTheme(next);
            // Leaflet keeps its own idea of the container size; a repaint that changes
            // nothing about layout still leaves it happier than not being told.
            if (window._map) setTimeout(() => window._map.invalidateSize(), 60);
        });
    }

    const sizes = document.getElementById('text-size');
    if (sizes) {
        sizes.innerHTML = [['sm', 'A-'], ['md', 'A'], ['lg', 'A+']]
            .map(([k, lbl]) => `<button type="button" class="t-${k === 'sm' ? 'sm' : k === 'lg' ? 'lg' : 'md'}"
                                        data-step="${k}">${lbl}</button>`).join('');
        sizes.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
            writeStored(TEXT_KEY, b.dataset.step);
            applyTextSize(b.dataset.step);
        }));
    }
    applyTextSize(readStored(TEXT_KEY, 'md'));
}

/* ------------------------------------------------------------ quick picks ---
   Somewhere to start for a rider who cannot yet name a stop. The destinations are
   real seeded stops looked up by name, so a pick that cannot be resolved is simply
   not offered rather than being offered and then failing. */

const QUICK_PICKS = [
    { key: 'quick.home',   match: 'Hledan' },
    { key: 'quick.work',   match: 'Sule Myodaw Khan Ma' },
    { key: 'quick.school', match: 'Insein Zay' },
    { key: 'quick.market', match: 'Myaynigone' }
];

function renderQuickPicks() {
    const el = document.getElementById('quick-picks');
    if (!el) return;
    const found = QUICK_PICKS
        .map(q => ({ ...q, stop: networkStops.find(s => (s.nameEn || '').startsWith(q.match)) }))
        .filter(q => q.stop);

    el.innerHTML = `<span class="quick-label">${t('quick.label')}</span>` + found
        .map(q => `<button type="button" class="quick-chip" data-stop="${q.stop.id}">${t(q.key)}</button>`)
        .join('')
        // Favourites is the rider's own list rather than one of ours, so it is marked
        // apart and simply empty until they save something.
        + `<button type="button" class="quick-chip fav" id="fav-chip">\u2B50 ${t('quick.fav')}</button>`;

    // Favourites is the rider's own shortlist. Nothing is saved yet, so it says so
    // rather than looking broken - an empty list is a state, not a failure.
    const fav = document.getElementById('fav-chip');
    if (fav) fav.addEventListener('click', () => {
        const status = document.getElementById('voice-status');
        if (status) { status.classList.remove('voice-error'); status.textContent = t('quick.favEmpty'); }
    });

    el.querySelectorAll('.quick-chip[data-stop]').forEach(b => {
        b.addEventListener('click', () => {
            // A quick pick sets the DESTINATION. Where someone is starting from is the
            // thing they can see out of the window; where they are going is the thing
            // they had to remember the name of.
            setPickerStop('to-stop', Number(b.dataset.stop));
            runSearch();
        });
    });
}

/* ------------------------------------------------------------- promises ----
   Four claims the rest of the interface then has to keep. */

const PROMISES = [
    ['\u{1F441}\uFE0F', 'promise.see'],
    ['\u{1F449}', 'promise.use'],
    ['\u2764\uFE0F', 'promise.safe'],
    ['\u2705', 'promise.trust']
];

function renderPromises() {
    const el = document.getElementById('promises');
    if (!el) return;
    el.innerHTML = PROMISES.map(([ic, key]) => `
        <div class="promise">
            <span class="promise-ic" aria-hidden="true">${ic}</span>
            <div><b>${t(key + '.t')}</b><span>${t(key + '.s')}</span></div>
        </div>`).join('');
}

/* ------------------------------------------------------------------ help --- */

/** The floating button is the only way in now; the top-bar pill was removed. */
function initHelp() {
    const fab = document.getElementById('help-fab');
    if (fab) fab.addEventListener('click', () => { location.hash = '#about'; showView('about'); });
}

/** Swaps the two stops, so a return trip is one tap rather than two searches. */
function initSwap() {
    const btn = document.getElementById('swap-btn');
    if (!btn) return;
    btn.addEventListener('click', () => {
        const a = pickers.get('from-stop');
        const b = pickers.get('to-stop');
        if (!a || !b || !a.chosen || !b.chosen) return;
        const from = a.chosen, to = b.chosen;
        setPickerStop('from-stop', to.id);
        setPickerStop('to-stop', from.id);
        if (lastSearch) runSearch();
    });
}

/**
 * The three illustrations.
 *
 * <p>Each slot prefers a real file and falls back to the SVG drawn above only when the
 * file is absent. The fallback exists so a fresh checkout renders something rather than
 * an empty box or a broken-image icon - it is not the intended artwork, and it disappears
 * the moment the PNG is dropped in.
 *
 * <p>Drop files at these paths and refresh. The frontend is served from disk, so there is
 * no build step and no restart:
 *
 * <pre>
 *   frontend/img/hero-bus.png      beside the "Plan a trip" heading
 *   frontend/img/tips-family.png   behind the Tips card copy
 *   frontend/img/people.png        foot of the sidebar
 * </pre>
 */
const ART_SLOTS = [
    { el: 'head-art',  src: '/img/hero-bus.jpg',    fallback: () => HEAD_ART,   cls: 'head-photo' },
    { el: 'tips-art',  src: '/img/tips-family.jpg', fallback: () => FAMILY_ART, cls: 'tips-photo' },
    { el: 'hello-art', src: '/img/hello-sky.jpg',   fallback: () => '',         cls: 'hello-photo' },
    { el: 'side-art',  src: '/img/people.png',      fallback: () => FAMILY_ART, cls: 'side-photo' }
];

function paintArt() {
    for (const slot of ART_SLOTS) {
        const host = document.getElementById(slot.el);
        if (!host) continue;

        // Draw the fallback immediately so nothing flashes empty, then replace it if the
        // file turns out to exist. Ordering it this way means a slow disk or a 404 both
        // end in the same, already-rendered state.
        host.innerHTML = slot.fallback();
        host.classList.remove('has-photo');

        const img = new Image();
        img.onload = () => {
            img.className = slot.cls;
            img.alt = '';
            host.innerHTML = '';
            host.appendChild(img);
            host.classList.add('has-photo');
        };
        img.src = slot.src;
    }
}

/* --------------------------------------------------------- language switch ---
   Static markup is rewritten by applyLang(). Everything below was built by
   JavaScript, so its text was baked in when it rendered and has to be rebuilt. */

function reRenderDynamic() {
    renderRoleSwitch();
    renderNav();
    applyRoleChrome();
    initProfile();
    renderRouteFilter();
    relabelPickers();
    renderPresets();
    initAbout();
    loadVoiceSamples();
    renderQuickPicks();
    renderPromises();
    applyTheme(document.body.classList.contains('dark'));
    updateLegend();
    if (lastSearch) { renderTrace(lastSearch); renderResults(lastSearch); }
    if (currentView === 'fleet') renderFleet();
    if (currentView === 'arrivals') loadArrivalsBoard();
    if (currentView === 'shift') paintShift();
    if (currentView === 'map') applyMapRoleFraming();
}

document.addEventListener('ybs:lang', reRenderDynamic);

/* -------------------------------------------------------------------- boot */

initLangSwitch(document.getElementById('lang-switch'));
initLangSwitch(document.getElementById('lang-switch-top'));

loadNetwork()
    .then(async net => {
        initSimControls();
        initProfile();
        initSelectors(net.stops);
        initSignIn();
        await loadSession();
        // A role remembered from a previous session is only honoured if this visitor is
        // still entitled to it - otherwise a logged-out browser would open on a driver
        // nav whose every request then 401s.
        if (!allowedRoles().includes(role)) role = 'rider';
        renderRoleSwitch();
        renderNav();
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
        initPrefs();
        initSwap();
            initHelp();
        renderQuickPicks();
        renderPromises();
        paintArt();
        applyLang();
        showView(location.hash.slice(1) || roleViews()[0]);
        pollBuses();
        setInterval(pollBuses, 1000);
        setInterval(pollAlert, 1000);
    })
    .catch(err => {
        console.error('Failed to load network', err);
        document.getElementById('map-note').classList.remove('hidden');
    });
