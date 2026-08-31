# Frontend

Everything the browser loads. Plain HTML, CSS and vanilla JS — **no build step, no npm, no
framework.** That is deliberate: the demo must start with one command and run with the
network cable unplugged.

| File | What it is |
|---|---|
| `index.html` | the app shell — rider, driver and admin all live here |
| `ops.html` | ops dashboard |
| `i18n.js` | the string table and the Burmese/English switch |
| `app.js` | all behaviour: roles, map, polling loops, search, profile toggles, alerts |
| `style.css` | design tokens (`:root`) and every component |
| `vendor/` | Leaflet 1.9.4, vendored so the page works offline |
| `audio/` | arrival clips — silent placeholders, see root README |
| `voice/` | sample Burmese query audio |

## Working on it

The backend serves this folder **directly from disk**, so:

```bash
cd backend && ./mvnw spring-boot:run      # once
# then edit any file here and just refresh the browser
```

No rebuild. No restart. If a change does not appear, hard-refresh (Ctrl+F5) — it is the
browser cache, not the server.

## Design rules

Three constraints come from the brief's "design for low literacy". Please keep them:

1. **Fare is the largest element on screen.** It is what riders decide on.
2. **One language at a time.** The page used to print Burmese with a small English line
   under it. That reads as a translation exercise and doubles the width of every control,
   so the switch in the sidebar now picks one and the whole system follows it.
3. **Icons never stand alone.** Every control pairs an icon with words.

Layout is two zones: the decision surface (query → profile → results) on the left, the
evidence of what the AI did (map, A* trace) on the right. Both must be visible at once —
the examiner needs to see a profile toggle change the results *and* the trace that produced
them. Collapses to one column under 1100px.

### Adding a string

Never write user-facing text into the markup or into a template literal. Put it in
`i18n.js` under one key with both values, then either declare it in the HTML:

```html
<h2 data-i18n="plan.h1"></h2>          <!-- also data-i18n-ph, -aria, -title -->
```

or, if JavaScript builds the element, call `t('plan.h1')`. Anything built by JavaScript has
its text baked in at render time, so it must also be rebuilt from `reRenderDynamic()` in
`app.js`, which runs on the `ybs:lang` event. Stop and route names are **not** in the table:
they ship from the API in both languages, so call `stopName(stop)` and `routeName(route)`.

### Roles and signing in

`ROLES` in `app.js` maps rider / driver / admin to the nav each one gets, and `showView()`
refuses any view outside the current role's list. **That is the UI half only** — the server
refuses the data regardless, which is what makes it a boundary rather than a suggestion.
Which roles a visitor may switch to comes from `GET /api/auth/me`, never from `localStorage`:
a remembered role is dropped if the session no longer allows it.

Two rules for anything added here:

- **Every write goes through `post()`.** CSRF is on for all of them, including the public
  ones, and `post()` is what attaches the `X-XSRF-TOKEN` header from the cookie Spring set.
  A bare `fetch(..., {method:'POST'})` will come back 403.
- **Never send a driver id.** The driver's own screen is `GET /api/ops/me/shift`, which takes
  no parameter — the server reads who you are from the session. It replaced a dropdown over
  `/api/ops/drivers/{id}` that let anyone read anyone's file.

```
GET  /api/auth/me       -> {"authenticated":false,"role":"RIDER"}
                        -> {"authenticated":true,"username":"ukyawwin","role":"DRIVER",
                            "driverId":2,"driverName":"…"}
POST /api/auth/login    form-encoded username + password -> the same shape, or 401
POST /api/auth/logout   -> the signed-out shape
GET  /api/ops/me/shift  DRIVER only, no id, this driver's shift
```

A wrong username and a wrong password return **the same** error, deliberately: telling them
apart tells an attacker which accounts exist. Show it verbatim rather than improving on it.

---

## The API contract

This is the boundary between the two halves. As long as these shapes hold, frontend and
backend can change independently. All responses are JSON. Base URL `http://localhost:8081`.

### Network — `GET /api/network`
Everything needed to draw the map, in one call.
```json
{ "routes": [ { "id":1, "code":"36", "nameMy":"…", "baseFareKyats":300,
                "colour":"#e6194b", "stops":[ {"stopId":1,"nameMy":"…","lat":16.88,"lon":96.10,
                                               "sequence":1,"cumulativeMinutes":0} ] } ],
  "stops":  [ { "id":1, "nameMy":"အင်းစိန်ဈေး", "nameEn":"Insein Market",
                "lat":16.8866202, "lon":96.1016507, "landmarkPhotoUrl":null } ] }
```

### Live buses — `GET /api/buses/live`  *(poll at 1 Hz)*
```json
[ { "id":1, "routeCode":"36", "lat":16.8876, "lon":96.1052,
    "crowdingLevel":0, "state":"DRIVING", "dwellSecondsRemaining":0,
    "nextStopId":2, "nextStopNameMy":"အင်းစိန်", "nextStopNameEn":"Insein",
    "minutesToNextStop":1.1 } ]
```
`state` is `DRIVING` or `DWELLING`. A dwelling bus holds its stop's exact coordinates —
render it differently or it looks like the simulation froze.

### Journey search — `GET /api/journeys/search?from={id}&to={id}&profile=…`
`profile` is repeatable: `&profile=pregnant&profile=student`. Valid flags: `pregnant`,
`needs_seat`, `mobility_limited`, `student`, `verified_card`, `monk`.
```json
{ "options": [ { "journey": { "id":"j1", "totalMinutes":95.7,
                              "etaLowerBound":71.8, "etaUpperBound":119.6,
                              "totalFareKyats":1400, "transferCount":3,
                              "routeCodes":["36","43"], "stopIds":[1,2,…],
                              "legs":[ {"kind":"ride","routeCode":"36",
                                        "fromNameMy":"…","toNameMy":"…",
                                        "minutes":40.1,"fareKyats":300,
                                        "fullRoute":false,"viaStopIds":[…]} ] },
                 "suitable": false,
                 "violationReasons": ["too_many_transfers"],
                 "riderFareKyats": 1400 } ],
  "trace": [ {"order":1,"stopName":"Insein Market","routeCode":null,
              "g":0.0,"h":19.1,"f":19.1} ],
  "expansions": 52, "dijkstraExpansions": 56, "bucket": "midday" }
```

Two things the UI must get right here:

- **`riderFareKyats`, not `totalFareKyats`**, is what to show. The first is what *this*
  rider pays after the Prolog fare rules; the second is the list price the search optimised
  on. The tariff has four rows, and both axes come from the request:

  | | ordinary ride | end to end |
  |---|---|---|
  | holds a YBS card (`verified_card`) | 350 | 500 |
  | paying cash | 400 | 600 |

  A monk travels free and a student with a card pays half of the card rate for the span, so
  the concessions sit on top of the table rather than replacing it. The UI names which row
  the rider is on, because otherwise ticking the card chip changes a number with nothing to
  explain it.
- **`fullRoute` on a leg** is what picks the right-hand column: true when the ride boards at
  the route's first stop and alights at its last. Every YBS route is a loop, so in practice
  this is never true in a search result — A* walks the few hundred metres between the termini
  instead of riding the whole loop. The rate is reachable from the rule console.
- **`suitable: false` means show it, greyed, with the reason** — not hide it. A rider who
  cannot see why an option vanished learns nothing. Reason codes:
  `crowded_no_seat`, `too_many_transfers`, `overbridge_not_accessible`, `flooded_segment`.

`kind` on a leg is `"ride"` or `"walk"`; `routeCode` is `null` for a walk.

### Driver — `GET /api/ops/drivers` and `GET /api/ops/drivers/{id}`
The driver view signs in as one driver and shows only their own shift.
```json
{ "driverId":2, "driverName":"…", "routeId":2, "routeCode":"10", "routeNameMy":"…",
  "months":[ {"month":"2026-08","headwayScore":74,"harshEvents":3,"upheldComplaints":0,
              "bonusEligible":false,"blockers":["headway_below_target"]} ],
  "incidents":[ {"type":"CROWDING","hour":9,"stopNameMy":"…","stopNameEn":"…"} ],
  "bunching":[] }
```
`blockers` comes from `bonus_blocker/3`, the inverse of `bonus_eligible/2`: a driver who
missed the bonus is owed the rule that withheld it, not a bare "not eligible". Codes:
`headway_below_target`, `too_many_harsh_events`, `upheld_complaint`. Live buses are
deliberately absent — the page already polls `/api/buses/live` and filters by `routeCode`,
and a second source would give the screen two clocks to disagree with.

### Arrivals
```
GET /api/stops/{id}/arrivals     # buses due at a stop, soonest first
GET /api/buses/{id}/arrivals     # that bus's remaining stops
```
```json
{ "stopId":6, "stopNameMy":"လှည်းတန်း", "stopNameEn":"Hledan Junction",
  "arrivals":[ { "busId":2, "routeCode":"36", "sequence":7,
                 "etaMinutes":12.8, "etaLowerBound":9.6, "etaUpperBound":16.0,
                 "bucket":"evening_peak" } ] }
```
Always show the band, never `etaMinutes` alone — the model genuinely does not know better
than ±25%, and claiming a single number would overstate it.

### Voice — `POST /api/voice/query?sample=sample1|sample2|sample3&lang=my|en&mode=…`
```json
{ "mode":"recorded", "transcript":"အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်",
  "originStopId":1, "originName":"အင်းစိန်ဈေး",
  "destStopId":21,  "destName":"ဗိုလ်တထောင်ဘုရား", "understood":true }
```
`GET /api/voice/samples?lang=my|en` lists the available samples in that language. Render the
parsed origin/destination as chips — making the NLP step visible is the point, not decoration.

- **`lang` follows the page**, and picks three things at once: which recording plays, which
  MMS language adapter runs, and which half of `EntityExtractor` parses the result. Switching
  language must therefore reload the sample list, which is why `loadVoiceSamples()` is called
  from `reRenderDynamic()`.
- **`mode` is `recorded` (default), `mms` or `whisper`**, overriding `demo.voice.mode` for one
  request so the offline path can be forced on stage without a restart. `mms` is Meta's
  Massively Multilingual Speech; both model paths fall back to `recorded` when their endpoint
  is unreachable and the returned `mode` says so — `mms:eng (fell back to recorded)`. Show it
  verbatim: it is what stops anyone claiming a model ran when it did not.
  [`docs/VOICE.md`](../docs/VOICE.md) has the contract and a server that satisfies it.

### Trip and alert
```
POST /api/alert/start?busId={id}&stopId={id}    # begin watching
POST /api/alert/cancel
GET  /api/alert/state                            # poll at 1 Hz
```
`state` returns `triggered: true` **exactly once**, when the bus comes within 400 m. It also
carries `clips` — play them in order, e.g. `["prefix","stop_21","suffix"]`, trying `.mp3`
then `.wav`.

### Simulation and disruption
```
GET  /api/sim/speed                              → {"multiplier":1}
POST /api/sim/speed?multiplier=1|10|60
GET  /api/disruptions                            → {"floodedStops":[]}
POST /api/disruptions/flood?stopId=15&active=true
POST /api/disruptions/clear
```

### Ops — `GET /api/ops/dashboard`
```json
{ "drivers":[ {"driverId":1,"driverName":"ဦးမြင့်ဆွေ","headwayScore":91,
               "harshEvents":2,"upheldComplaints":0,"bonusEligible":true} ],
  "bunchingAlerts":[…], "incidentCounts":[…] }
```
`bonusEligible` is computed by the Prolog engine per request, not read from a column.

### Prolog console — `GET /api/prolog/query?goal=…`
```json
{ "goal":"fare(m1,'43',F)", "proved":true, "solutions":[{"F":"400"}] }
```
Used live in the demo to prove the reasoning is real. A trailing `.` is optional.
