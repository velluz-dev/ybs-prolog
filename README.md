
# YBS AI transit demo

A university AI-course demo: an AI-assisted journey planner over three Yangon Bus Service
routes. One Spring Boot process, H2 in memory, reseeded on every startup.

Each component maps to a recognisable AI topic:

| AI topic | Feature package | Start reading at |
|---|---|---|
| Knowledge representation & reasoning | `reasoning/` | `src/main/resources/rules/ybs.pl` |
| Heuristic search | `planning/` | `planning/AStarPlanner.java` |
| Constraint satisfaction | `reasoning/` | `violates/2` in `ybs.pl` |
| Reasoning under uncertainty | `eta/` | `eta/EtaService.java` |
| NLP / speech | `voice/` | `voice/EntityExtractor.java` |

---

> **How it all fits together:** [`docs/WORKFLOW.md`](docs/WORKFLOW.md) traces one request end
> to end — Burmese audio → entity extraction → A\* → Pareto → Prolog → trip → 400 m alert —
> naming the class and endpoint at every hop, with diagrams.

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

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8081>.

- **Port 8081, not 8080.** Port 8080 is occupied on this machine by an unrelated
  `AgentService` process. Change `server.port` in `application.properties` if you prefer.
- **First build needs the internet** to download Maven and the dependencies. After that
  `~/.m2` is warm and everything runs offline.
- Run the tests with `./mvnw test`.

### The Avast TLS workaround (already applied)

Avast Web/Mail Shield intercepts HTTPS on this machine and re-signs certificates with its
own root CA. That root is trusted by Windows (so browsers and `curl` work) but not by the
JDK, so Maven could not reach Maven Central — it failed with `PKIX path building failed`.

The fix is committed and needs no action from you:

- `.mvn/cacerts` — a copy of the JDK truststore with the Avast root added
- `.mvn/jvm.config` — points Maven at it

Nothing outside the project was modified. If you ever disable Avast's HTTPS scanning, you
can delete both files.

---

## Status — all seven components built and verified

- **Data & map** — 28 stops with coordinates looked up from OpenStreetMap (each row in
  `data.sql` cites its source), 3 routes, Leaflet rendering. Leaflet is vendored in
  `static/vendor/` so the page works with no network; only the map *tiles* need the
  internet, and the routes stay visible without them.
- **Bus simulation** — 1 Hz tick, 6 buses, 1× / 10× / 60× speed control. Buses alternate
  between **driving** a segment and **waiting** at the stop they reach, and predict their
  own arrival times. See "Driving, waiting and arriving" below.
- **A\* search** — Pareto set over (minutes, fare, transfers), live search trace with
  g/h/f per expanded node, plus an A*-vs-Dijkstra expansion count.
- **Prolog** — all four rule families, live filtering by rider profile with a named reason
  per rejection, `/api/prolog/query` debug endpoint, flood injection.
- **ETA** — hand-authored traffic prior with a ±25% confidence band.
- **Voice** — 3 recorded Burmese queries with entity extraction; a real self-hosted
  Whisper client that falls back to the recorded path if the endpoint is down.
- **Arrival alert** — 400 m geofence, fires exactly once, large banner plus clip playback.
- **Ops dashboard** — `/ops.html`, bonus badges computed live by Prolog.

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
- **Tests** — 11 passing, including heuristic admissibility across every reachable stop pair.

### Replacing the placeholder audio

The repo ships **silent/tone WAV placeholders** so the playback path runs end to end.
Record real Burmese clips and drop them into `src/main/resources/static/audio/` — the
player tries `.mp3` first and falls back to `.wav`, so **no code change is needed**.

| File | What to say |
|---|---|
| `prefix.mp3` | "နောက်မှတ်တိုင်" *(next stop is…)* |
| `stop_1.mp3` … `stop_28.mp3` | the stop's Burmese name — ids are in `data.sql` |
| `suffix.mp3` | "ဆင်းရန် အဆင်သင့်ဖြစ်ပါ" *(get ready to alight)* |

The three voice-query samples in `static/voice/` are silent placeholders too. Their
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

**0:00 — Set the scene.** Open <http://localhost:8081>. Three routes on a Yangon map,
buses already moving. Point out the console: it is about to fill with Prolog goals.

**0:30 — Ask by voice.** Pick the second sample
(`အင်းစိန်ဈေးမှ ဗိုလ်တထောင်ဘုရားကို သွားချင်တယ်`) and press Speak. After a beat the
transcript appears, then two green chips: **မှ Insein Market** and **ကို Botataung**.
Say plainly: *this is string matching on the two postpositions* `မှ` *(from) and* `ကို`
*(to) — not a language model. The chips exist so you can see the parse, not just trust it.*
The search then runs itself.

**0:45 — The journey options.** Insein Market → Botataung Pagoda.
Three options come back, fare shown first and largest:

| | time | fare | transfers |
|---|---|---|---|
| fastest | 77 min | 1400 K | 3 |
| middle | 91 min | 1000 K | 2 |
| cheapest | 131 min | 700 K | 1 |

Say: *this is a Pareto set — none of these beats the others on all three at once, so the
planner refuses to pretend there is a single best answer.*

**2:00 — The A\* trace.** Scroll the trace panel. Every expanded node with its g, h and f.
Point at the stats line: A* expanded **54** nodes, Dijkstra **56**.

> Be honest about this number. The saving is small because the network is 28 stops and the
> heuristic is weak — the fastest segment in the data runs at 42.8 km/h, so `distance ÷
> 42.8` is a loose lower bound. The heuristic is provably admissible (`AStarHeuristicTest`
> checks it on every stop pair), which is the examinable claim; dramatic pruning at this
> scale is not.

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
