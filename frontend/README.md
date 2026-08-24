# Frontend

Everything the browser loads. Plain HTML, CSS and vanilla JS — **no build step, no npm, no
framework.** That is deliberate: the demo must start with one command and run with the
network cable unplugged.

| File | What it is |
|---|---|
| `index.html` | journey planner — the main screen |
| `ops.html` | ops dashboard |
| `app.js` | all behaviour: map, polling loops, search, profile toggles, alerts |
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
2. **Burmese is primary, English is the secondary line** — never a replacement.
3. **Icons never stand alone.** Every control pairs an icon with words.

Layout is two zones: the decision surface (query → profile → results) on the left, the
evidence of what the AI did (map, A* trace) on the right. Both must be visible at once —
the examiner needs to see a profile toggle change the results *and* the trace that produced
them. Collapses to one column under 1100px.

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
                                        "minutes":40.1,"fareKyats":300,"viaStopIds":[…]} ] },
                 "suitable": false,
                 "violationReasons": ["too_many_transfers"],
                 "riderFareKyats": 1400 } ],
  "trace": [ {"order":1,"stopName":"Insein Market","routeCode":null,
              "g":0.0,"h":19.1,"f":19.1} ],
  "expansions": 52, "dijkstraExpansions": 56, "bucket": "midday" }
```

Two things the UI must get right here:

- **`riderFareKyats`, not `totalFareKyats`**, is what to show. The first is what *this*
  rider pays after the Prolog fare rules (a monk pays 0); the second is the list price.
- **`suitable: false` means show it, greyed, with the reason** — not hide it. A rider who
  cannot see why an option vanished learns nothing. Reason codes:
  `crowded_no_seat`, `too_many_transfers`, `overbridge_not_accessible`, `flooded_segment`.

`kind` on a leg is `"ride"` or `"walk"`; `routeCode` is `null` for a walk.

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

### Voice — `POST /api/voice/query?sample=sample1|sample2|sample3`
```json
{ "mode":"recorded", "transcript":"အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်",
  "originStopId":1, "originName":"အင်းစိန်ဈေး",
  "destStopId":21,  "destName":"ဗိုလ်တထောင်ဘုရား", "understood":true }
```
`GET /api/voice/samples` lists the available samples. Render the parsed origin/destination
as chips — making the NLP step visible is the point, not decoration.

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
