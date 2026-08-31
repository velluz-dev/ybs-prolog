
# YBS AI transit demo

A university AI-course demo: an AI-assisted journey planner over the **real Yangon Bus
Service network** - 118 routes, 2,080 stops, 236 simulated buses. One Spring Boot process,
H2 in memory, reseeded from `data.sql` on every startup.

Each component maps to a recognisable AI topic:

| AI topic | Feature package | Start reading at |
|---|---|---|
| Knowledge representation & reasoning | `reasoning/` | `backend/src/main/resources/rules/ybs.pl` |
| Heuristic search | `planning/` | `planning/AStarPlanner.java` |
| Constraint satisfaction | `reasoning/` | `violates/2` in `ybs.pl` |
| Reasoning under uncertainty | `eta/` | `eta/EtaService.java` |
| NLP / speech | `voice/` | `voice/EntityExtractor.java` |

---

> **Database:** [`docs/ER.md`](docs/ER.md) is the ER diagram — eight tables, the composite
> keys and why they are shaped that way, plus two constraint gaps worth knowing about.
>
> **How it all fits together:** [`docs/WORKFLOW.md`](docs/WORKFLOW.md) traces one request end
> to end — Burmese audio → entity extraction → A\* → Pareto → Prolog → trip → 400 m alert —
> naming the class and endpoint at every hop, with diagrams.

## Repository layout — who owns what

```
YBS/
├── frontend/          ← the browser. HTML, CSS, JS, Leaflet, audio clips.
│   ├── index.html         journey planner (sidebar, one view per feature)
│   ├── ops.html           ops dashboard
│   ├── admin.html         add / edit data, export data.sql
│   ├── app.js             all behaviour: map, polling, search, alerts
│   ├── admin.js           admin screens
│   ├── style.css          design tokens + components
│   ├── vendor/            Leaflet, vendored for offline use
│   ├── audio/             arrival clips (placeholders)
│   └── voice/             sample Burmese queries
│
├── backend/           ← the JVM. Spring Boot, Prolog, A*, simulation.
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd     run everything from in here
│   └── src/
│       ├── main/java/com/ybs/demo/    feature packages (see below)
│       ├── main/resources/            application.properties, data.sql, rules/ybs.pl
│       └── test/java/                 23 tests
│
├── tools/             ← optional, not in the build
│   └── voice/             the real MMS model: ASR server, TTS sample generation,
│                          and an accuracy round-trip. The demo runs without it.
│
├── docs/
│   ├── SETUP.md       ← install guide and troubleshooting
│   ├── WORKFLOW.md    ← how a request flows through the whole system
│   ├── ER.md          ← database ER diagram
│   └── VOICE.md       ← speech to text in both languages, and how to run MMS
└── README.md
```

**This is still one process.** The split is about ownership, not architecture — the brief
requires a single command and no network dependency at demo time, so there is no separate
frontend server, no build step, and no npm. `backend/pom.xml` declares `../frontend` as a
resource directory, so it is packaged into the jar at build time.

### Working in parallel

| You are working on | You edit | To see your change |
|---|---|---|
| UI, layout, Burmese copy, map | `frontend/` | **just refresh the browser** — no rebuild, no restart |
| Search, Prolog, simulation, APIs | `backend/src/main/java/` | restart `./mvnw spring-boot:run` |
| Stops, routes, fares, traffic prior | `data.sql`, **or** `/admin.html` | admin edits are live at once; export to keep them |
| Reasoning rules | `backend/src/main/resources/rules/ybs.pl` | restart |

Frontend edits are live because `application.properties` serves `file:../frontend/` ahead of
the packaged copy. That is the one thing that makes parallel work comfortable: the frontend
teammate never waits on a Maven build.

**The contract between the two halves is the REST API** — see
[`frontend/README.md`](frontend/README.md) for every endpoint the UI consumes, with example
responses. As long as those shapes hold, both halves can change independently.

---

## Project structure

The code is organised **by feature, not by layer**. There is no `entity/`, `service/` or
`controller/` package; instead each feature owns its entities, repositories, services and
endpoints together. Every package has a `package-info.java` stating what it does and which
AI topic — if any — it demonstrates.

```
com.ybs.demo/
  network/      the stops, routes and itineraries everything else is defined against
  planning/     AI: heuristic search — A*, Pareto set, search trace
  reasoning/    AI: KR&R + constraint satisfaction — tuProlog bridge, fact assembly
  eta/          AI: reasoning under uncertainty — traffic prior, confidence band
  voice/        AI: NLP / speech — transcription, entity extraction
  simulation/   buses moving at 1 Hz, speed multiplier
  alerts/       400 m arrival geofence and clip playback
  disruption/   inject a flood, watch the rule base withdraw journeys
  ops/          driver scores, bunching, incidents
  shared/       haversine and a health endpoint, nothing else
```

Two consequences worth stating in a viva:

- **Each AI topic is one directory.** An examiner asking "show me your constraint
  satisfaction" gets pointed at `reasoning/` rather than at fragments spread across four
  layer packages.
- **Cross-feature dependencies are visible in the imports.** `planning` imports from
  `network` and `eta`; `reasoning` imports from `planning` and `ops`. Nothing is hidden
  behind a shared service layer, so the coupling is exactly what the import list says.

---

## Running it

> **New to the project?** [`docs/SETUP.md`](docs/SETUP.md) is the full install guide —
> prerequisites, first run, IDE setup and troubleshooting.

**Prerequisites: a JDK 17 or newer, and Git. That is all.** No Maven (the wrapper handles
it), no Node or npm (the frontend has no build step), no database (H2 runs in memory).

```bash
git clone https://github.com/velluzHTA-dev/ybs-prolog.git
cd ybs-prolog/backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8081>.

The project compiles to Java 17 bytecode on purpose, so any JDK from 17 onward can build it —
teammates use whatever they already have. It runs happily on 25.

- **Port 8081, not 8080.** Port 8080 is occupied on this machine by an unrelated
  `AgentService` process. Change `server.port` in `application.properties` if you prefer.
- **First build needs the internet** to download Maven and the dependencies. After that
  `~/.m2` is warm and everything runs offline.
- Run the tests with `cd backend && ./mvnw test`.

### The Avast TLS workaround (already applied)

Avast Web/Mail Shield intercepts HTTPS on this machine and re-signs certificates with its
own root CA. That root is trusted by Windows (so browsers and `curl` work) but not by the
JDK, so Maven could not reach Maven Central — it failed with `PKIX path building failed`.

**These files are deliberately NOT committed** — they are machine-specific and would break
the build for anyone whose machine does not run Avast:

- `backend/.mvn/cacerts` — a copy of the JDK truststore with the Avast root added
- `backend/.mvn/jvm.config` — points Maven at it, using an absolute path

**Teammates: you almost certainly do not need this.** If `./mvnw` works, ignore this
section. Only if you hit `PKIX path building failed`, recreate the two files for your own
machine (adjust the JDK path and your interceptor's name):

```bash
# 1. export the intercepting root CA from the Windows trust store
powershell -Command "Get-ChildItem Cert:\LocalMachine\Root | Where-Object { \$_.Subject -like '*Avast*' } | Select-Object -First 1 | ForEach-Object { [Convert]::ToBase64String(\$_.RawData,'InsertLineBreaks') }" > root.b64

# 2. copy the JDK truststore and import the cert into the copy
cp "$JAVA_HOME/lib/security/cacerts" backend/.mvn/cacerts
keytool -importcert -noprompt -trustcacerts -alias mitm-root         -file backend/.mvn/avast-root.pem         -keystore backend/.mvn/cacerts -storepass changeit

# 3. point Maven at it
echo "-Djavax.net.ssl.trustStore=$(pwd)/backend/.mvn/cacerts -Djavax.net.ssl.trustStorePassword=changeit"      > backend/.mvn/jvm.config
```

Nothing outside the project is modified. The system JDK truststore is left untouched.

---

## Status — all seven components built and verified

- **Data & map** — the real network: 2,080 stops and 118 routes imported from the YBS
  dataset, with road-following route geometry precomputed from OSRM. Leaflet rendering. Leaflet is vendored in
  `frontend/vendor/` so the page works with no network; only the map *tiles* need the
  internet, and the routes stay visible without them.
- **Bus simulation** — 1 Hz tick, 6 buses, 1× / 10× / 60× speed control. Buses alternate
  between **driving** a segment and **waiting** at the stop they reach, and predict their
  own arrival times. See "Driving, waiting and arriving" below.
- **A\* search** — Pareto set over (minutes, fare, transfers), live search trace with
  g/h/f per expanded node, plus an A*-vs-Dijkstra expansion count.
- **Prolog** — all four rule families, live filtering by rider profile with a named reason
  per rejection, `/api/prolog/query` debug endpoint, flood injection.
- **Fares** — a four-row tariff in the rule base: 350 with a YBS card, 400 in cash, and 500
  / 600 for riding a route end to end. Monk and student concessions sit on top of it. See
  "The fare tariff" below.
- **ETA** — hand-authored traffic prior with a ±25% confidence band.
- **Voice** — **press to talk**, or pick one of 12 sample queries, in Burmese or English.
  **Meta MMS** (`facebook/mms-1b-all`) does the recognition, with `mms-tts-mya`/`-eng`
  generating the sample audio. The recogniser is **constrained to the stop list**: rather
  than trusting a 30%-wrong transcript, every stop name is scored against the frames that
  carried the origin and the destination, so the answer is chosen by sound and cannot be a
  place that does not exist. That took 12 queries from **5 correct to 10**, and confidently
  wrong answers from 4 to 2. See [`docs/VOICE.md`](docs/VOICE.md). Run it from
  [`tools/voice/`](tools/voice/); the numbers and the reasoning are in
  [`docs/VOICE.md`](docs/VOICE.md).
- **Arrival alert** — 400 m geofence, fires exactly once, large banner plus clip playback.
- **Three roles with real authentication** — rider, driver and admin. Spring Security with
  BCrypt and sessions; a driver account is bound to its driver row, so "my shift" means one
  person's shift. See "Signing in" below.
- **Two languages** — Burmese or English, one at a time, from `frontend/i18n.js`.
- **Ops dashboard** — `/ops.html`, bonus badges computed live by Prolog.

---

## What the rule base can be asked

`ybs.pl` is the file to open in a viva, and it is now ten sections rather than four. Beyond
the fare and constraint families, these are worth typing into the live console:

| Goal | Shows |
|---|---|
| `journey_fare(j1, rider1, F)` | **aggregation** — `findall/3` over the legs, summed by a recursive `sum_kyats/2` |
| `concession(m1, C)` | **explanation** — which fare clause won, the way `violation_reason/3` explains a rejection |
| `pareto_optimal(J)` / `dominates(A, B)` | **preference as logic** — the planner's Pareto filter, stated in four lines instead of implemented |
| `meets(R1, R2)` / `reachable('61', To, 2)` | **recursion over a graph**, with a visited list — the reason it terminates on a network where every route is a loop |
| `transfer_point(S)` | **`setof/3`** — a stop two routes both call at |
| `top_headway('2026-08', D)` / `near_miss(D, M)` | **aggregation without `sort/2`** — best-of and count-of, written out |
| `contradictory_bonus(D, M)` | **a KB self-check** — must have *no* solutions |

Three things this is designed to make sayable out loud:

- **tuProlog has no `sort/2`, `between/3`, `sum_list/2` or `aggregate_all/3`.** The aggregates
  are therefore written out recursively, which is what a KR course is asking to see anyway.
- **Clause order only means precedence if you take the first solution.** `fare/4` is
  deliberately non-deterministic — a monk gets `0` and then, on backtracking, the general
  clause offers `400` as well, which is the whole point of the console preset. That makes
  `once/1` load-bearing inside `journey_fare/3`: without it a monk's journey would be priced
  at `0 + 400` per leg. A test pins this.
- **Section 9 is rules that check the rule base.** `unexplained_rejection/2`,
  `contradictory_bonus/2` and `missing_tariff/2` should each have no solutions; a solution
  means two rules written as opposites have drifted apart, which is the failure mode a rule
  base has and a function does not.

`serves/2`, `leg/3` and `journey_stats/4` are asserted for the journeys the last search
proposed — so, like `suitable/2`, the reachability and Pareto goals answer about that search
rather than about all 118 routes. Asserting the whole network would be 12,280 facts per
request, which would cost more than the search it supports.

---

## The fare tariff

Two things set the price, and both come from the request rather than from the route:

| | ordinary ride | end to end |
|---|---|---|
| holds a YBS card | **350** | **500** |
| paying cash | **400** | **600** |

It lives in `ybs.pl` as `fare_table/3` facts, so changing a fare is a one-line edit rather
than a hunt through clause bodies. `fare/4` takes the span; `fare/3` is the short form the
live console types and means an ordinary hop. Clause order still encodes precedence — a monk
travels free whatever the span, and a student with a card pays half of the card rate for the
span they actually rode, so an end-to-end student ride is 250 and not 175.

**One thing to know before you go looking for the 500/600 rate in a search result.** Every
one of the 118 YBS routes is a loop: the furthest any route's first stop sits from its last
is 1.76 km, and 117 of the 118 are under 1 km. A* therefore always walks between the termini
rather than riding the whole loop round, so `fullRoute` is never true in a journey the
planner returns. The rate is correct, tested, and reachable from the rule console — it is
the *network* that makes it unreachable from a search, not the rules. Pricing long rides
instead would mean triggering on the share of the route ridden rather than on its endpoints.

---

## Signing in

**A rider is never asked to sign in.** Planning a trip, the live map, the arrivals board, the
A\* trace and the Prolog console are all public — a passenger needing an account to look up a
bus would be a worse system. Signing in unlocks two things and only two.

| Role | Gets | Refused |
|---|---|---|
| **Rider** (signed out) | plan a trip, live map, arrivals, A\* trace, rule console | anything operational |
| **Driver** | **their own** shift, their route on the map, arrivals, fleet | every other driver's file, all admin |
| **Admin** | data entry, ops dashboard, the whole roster, fleet, map, rule console | `/api/ops/me/shift` — an admin has no driver row |

Demo accounts, seeded in `data.sql` as BCrypt hashes:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `umyintswe`, `ukyawwin`, `dawkhinmalar`, `uthanhtun`, `uaungmyo`, `dawsansanaye` | `driver123` | DRIVER, one per driver row |

### What changed, and why it mattered

The role switcher used to be a UI convenience with nothing behind it — this README said so.
The concrete hole was the driver screen: it was `/api/ops/drivers/{id}` behind a dropdown
listing all six drivers, so **any visitor could read any driver's headway score, harsh-event
count and complaints by changing a number in the URL.** The dropdown is gone. The endpoint is
now `/api/ops/me/shift`, it takes no id, and the driver is read from the session — there is
deliberately nothing left to change.

### Three decisions worth defending in a viva

- **Sessions, not JWT.** One server, one browser, no third-party client. A token would add
  signing keys, expiry and revocation in exchange for statelessness this app cannot use.
- **CSRF stays on for the public writes too.** `/api/sim/speed` is unauthenticated, so strictly
  a forged request has no privilege to borrow. It keeps the protection anyway: "exempt, and
  here is the threat-model argument" is a comment that rots, and the cost is one header in the
  frontend's `post()` helper.
- **Login answers in JSON, not a 302.** The frontend is a static page driving the API with
  `fetch`, and a redirect arrives as something it cannot act on. Unauthenticated API calls get
  401 with a body; the page decides what to show.

`SecurityConfig` lists every public path explicitly rather than relying on a default, so
adding an endpoint means deciding on purpose whether a passenger may reach it.

The driver's shift screen asks the engine **both** directions: `bonus_eligible/2` for the
verdict and `bonus_blocker/3` for what withheld it. A driver who missed the bonus is owed the
rule that decided it, not a bare "not eligible" over three numbers.

## Languages

The sidebar's language switch persists in `localStorage`; the role now comes from the
server, not from storage — a remembered role is honoured only if the session still allows it.

Language is one string table, `frontend/i18n.js`, with one key and two values per string.
Markup declares `data-i18n="key"`; anything JavaScript builds calls `t('key')` and is rebuilt
on the `ybs:lang` event. Stop and route names are not in the table — they arrive from the API
in both languages, so `stopName()` picks rather than translates. The admin screen
(`/admin.html`) is deliberately English-only: its copy is about `data.sql`, schema columns and
export mechanics, which is operator vocabulary rather than rider vocabulary.

---

## Driving, waiting and arriving

A bus is always in one of two states:

- **DRIVING** — covering a segment in `baseMinutes × trafficFactor(segment, bucket)`.
- **DWELLING** — stopped, holding the stop's exact coordinates until its dwell expires.

Dwell comes from `eta/DwellModel.java`: 20 s base, plus 8 s standing / 20 s full for
crowding, plus 25 s if the stop is an **interchange**, or a flat 180 s layover at a route
terminus. Interchange status is *derived* — a stop where more than one route calls — rather
than hand-listed, so it stays correct if `data.sql` changes.

Two endpoints answer "when does my bus get here?", both carrying the same ±25% band as a
planned journey because they call the same `EtaService.estimate`:

```
GET /api/stops/{id}/arrivals    # next buses due at a stop, soonest first
GET /api/buses/{id}/arrivals     # this bus's remaining stops
```

Click any stop on the map to see its arrivals board.

### Two things worth saying out loud in a viva

**Dwell used to be fake.** The old simulator multiplied *driving* time by 1.0–1.25 depending
on crowding and never actually stopped anywhere. That is cheap, but it cannot answer the
question a rider actually asks, because the waiting it was standing in for had been smeared
away. When explicit dwell went in, that multiplier had to come out — otherwise dwell is
charged twice and every bus runs slow. `SimulationTest.drivingTimeDoesNotSecretlyIncludeDwell`
exists purely to stop that regression coming back.

**The planner and the simulation share one model.** `StopGraph.rideMinutes` charges driving
*plus* the dwell at the stop the bus pulls into, so a planned journey and the bus you watch
on the map agree. Adding dwell raised quoted journey times by roughly 7–9 minutes on the
Insein → Botataung trip. Both A\* tests stayed green, and the reason is worth knowing: dwell
only ever *increases* edge costs, and a heuristic that was a lower bound on the smaller cost
is still a lower bound on the larger one. Admissibility survives cost increases; it is cost
*decreases* that break it — which is exactly what the night-time traffic factor did.
- **Tests** — 21 passing: heuristic admissibility across every reachable stop pair *and*
  every traffic bucket, the Prolog bridge, and the simulation's driving/waiting/arrival
  behaviour.

### Replacing the placeholder audio

The repo ships **silent/tone WAV placeholders** so the playback path runs end to end.
Record real Burmese clips and drop them into `frontend/audio/` — the
player tries `.mp3` first and falls back to `.wav`, so **no code change is needed**.

| File | What to say |
|---|---|
| `prefix.mp3` | "နောက်မှတ်တိုင်" *(next stop is…)* |
| `stop_1.mp3` … `stop_28.mp3` | the stop's Burmese name — ids are in `data.sql` |
| `suffix.mp3` | "ဆင်းရန် အဆင်သင့်ဖြစ်ပါ" *(get ready to alight)* |

The three voice-query samples in `frontend/voice/` are silent placeholders too. Their
transcripts come from a lookup map in `RecordedTranscriber`, so re-recording them is
optional — it only matters if you want the audio to match what the screen says.

### Using a real Whisper endpoint (optional)

```properties
demo.voice.mode=whisper
demo.voice.stt.url=http://localhost:9000/v1/audio/transcriptions
```

Point it at whisper.cpp or faster-whisper. If it is unreachable the app logs a warning,
falls back to the recorded transcript, and the UI shows
`whisper (fell back to recorded)` — the fallback is never silent.

---

## A note on honesty in the data

The stop **coordinates** are real and each is traceable to an OSM lookup. The three route
**itineraries** are a plausible simplification built from real corridors — they are not
transcribed from an official YBS timetable. The brief caps the demo at 3 routes and ~30
stops, so a faithful copy of the ~120-route network was never the goal. Say this if asked.

The traffic factors and the transfer penalty are **hand-authored assumptions**, documented
as such in the code. There is no learned model here and the demo does not claim one.

---

## The 7-minute demo script

**0:00 — Set the scene.** Open <http://localhost:8081>. 236 buses moving across the real
Yangon network, each coloured by its route. Point out the console: it is about to fill with
Prolog goals.

**0:30 — Ask by voice.** Pick the second sample
(`အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်`) and press Speak. After a beat the
transcript appears, then two green chips: **မှ အင်းစိန်ဈေး** and **ကို ဗိုလ်တထောင်ဘုရား**.
Say plainly: *this is string matching on the two postpositions* `မှ` *(from) and* `ကို`
*(to) — not a language model. The chips exist so you can see the parse, not just trust it.*
The search then runs itself.

**0:45 — The journey options.** အင်းစိန်ဈေး → ဗိုလ်တထောင်ဘုရား.
Three options come back, fare shown first and largest:

| | time | fare | transfers |
|---|---|---|---|
| fastest | 77 min | 1400 K | 3 |
| middle | 91 min | 1000 K | 2 |
| cheapest | 131 min | 700 K | 1 |

Say: *this is a Pareto set — none of these beats the others on all three at once, so the
planner refuses to pretend there is a single best answer.*

**2:00 — The A\* trace.** Open the *Search trace* view. Every expanded node with its g, h
and f. Point at the stats line: on a cross-city plan A\* expands around **1,300** nodes where
Dijkstra needs **2,400** — the heuristic saves roughly 1,000, and on the longest journeys
over 3,000.

> Dijkstra here is literally the same search with `h = 0`, which is what makes the comparison
> fair rather than a comparison of two different implementations. The heuristic is provably
> admissible — `AStarHeuristicTest` checks it on sampled stop pairs *and* exhaustively on
> every segment in every traffic bucket — and that admissibility is the examinable claim.
> The pruning is the visible payoff.

**3:00 — Constraint satisfaction, the money shot.** Tap **ကိုယ်ဝန်ဆောင် / pregnant**.
The two multi-transfer options grey out with *"too many transfers"*. The one-transfer
option survives. Nothing was re-searched — the same A* results were re-filtered by
`violates(J, R) :- pregnant(R), transfer_count(J, N), N > 1.`

**4:00 — Fares are derived, not stored.** Tap **သံဃာ / monk** — every fare becomes 0.
Swap to **student + verified card** — 1400 K becomes 700 K. Then show the card matters:
student *without* the card pays full fare, because the clause needs both facts.

**5:00 — Prove it is not hardcoded.** In a second tab:

```
http://localhost:8081/api/prolog/query?goal=fare(m1,'43',F)
```

Returns **one** solution, `F = 400`. `m1` is a rider nobody has said anything about, so the
first two `fare/3` clauses fail and it falls through to the general one — full fare.

Now assert monkhood inside the goal itself:

```
http://localhost:8081/api/prolog/query?goal=assert(monk(m1)),fare(m1,'43',F)
```

Returns **two** solutions in order: `F = 0`, then `F = 400`.

This is the strongest moment in the demo, so land it deliberately. The first answer is 0
because the monk clause now matches and it is written first. The second is 400 because
Prolog then *backtracks* and finds the general clause still applies. You are watching
clause ordering and backtracking directly — not a lookup table, and not something anyone
could have hardcoded, because the fact was invented in the URL a second ago.

**5:45 — Disruption re-planning.** Press **🌊 Flood Tamwe**. Every journey through Tamwe
is withdrawn with *"flooded segment"*. Nothing in the planner or the UI knows what a flood
is — only `ybs.pl` does. Press Clear to restore.

**6:00 — Arrival alert.** Set the speed to **60×**, then press *Start trip* on any option.
The app picks the nearest bus on the first leg's route and watches it. Within a few seconds
the bus crosses 400 m from the alight stop and a full-width red banner fires — once, not
every tick. The clip sequence `prefix + stop_NN + suffix` plays.

> Mention that the audio is currently a placeholder tone and the real Burmese recordings
> drop in without a code change. Do not oversell it.

**6:20 — Ops dashboard.** Open `/ops.html`. Six drivers, two eligible for a bonus. Point at
the console: one `bonus_eligible(dN, '2026-08')` goal per row, just now. The database column
says `false` for all six — the ticks came from the rule base.

**6:45 — Close.** The map is still moving. Let it run.

### Backup

Record a screen capture of this run before the presentation. If the venue machine
misbehaves, play the recording.
