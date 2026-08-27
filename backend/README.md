# Backend

Spring Boot 3.5 on Java 25 source level, MySQL-backed JPA, tuProlog in-JVM. One process; it
also serves `../frontend`.

```bash
cd backend
./mvnw spring-boot:run     # http://localhost:8081
./mvnw test                # 21 tests
```

> Run Maven **from this folder**, not the repo root — `pom.xml` and `mvnw` live here.

## Feature packages

Organised **by feature, not by layer**. No `entity/`, `service/` or `controller/` package —
each feature owns its entities, repositories, services and endpoints together, and every
package carries a `package-info.java` naming the AI topic it demonstrates.

| Package | AI topic | Contains |
|---|---|---|
| `planning/` | **heuristic search** | `AStarPlanner`, `StopGraph`, Pareto filter, search trace |
| `reasoning/` | **KR&R + constraint satisfaction** | `PrologService`, `FactAssembler`, `PrologController` |
| `eta/` | **reasoning under uncertainty** | `EtaService` (traffic prior, ±25% band), `DwellModel` |
| `voice/` | **NLP / speech** | `VoiceTranscriber` + two impls, `EntityExtractor` |
| `network/` | — | stops, routes, itineraries; the world everything is defined against |
| `simulation/` | — | `BusSimulator` (DRIVING ⇄ DWELLING @1Hz), `ArrivalPredictor` |
| `alerts/` | — | 400 m arrival geofence |
| `disruption/` | — | inject a flood; the rule base reacts |
| `ops/` | — | driver scores, bunching, incidents |
| `shared/` | — | haversine, health endpoint. Kept deliberately thin |

The rule base itself is `src/main/resources/rules/ybs.pl` — **that is where the reasoning
lives.** Java only asserts ground facts and asks goals.

## Where to change things

| To change | Edit |
|---|---|
| stops, routes, fares, traffic prior, drivers | `src/main/resources/data.sql` |
| reasoning rules | `src/main/resources/rules/ybs.pl` |
| transfer penalty, walk radius | `planning/StopGraph.java` (constants at the top) |
| dwell times | `eta/DwellModel.java` |
| geofence radius | `alerts/ArrivalAlertService.java` |
| port, voice mode, static paths | `src/main/resources/application.properties` |

`data.sql` is seeded into MySQL on startup with `INSERT IGNORE`, so on a fresh database it
loads the network once; a change to `data.sql` needs a restart, and edits made via the admin
screens persist across restarts.

## Things that will bite you

**`@PostConstruct` runs before `data.sql`.** With
`spring.jpa.defer-datasource-initialization=true`, seed data is inserted *after* beans are
constructed, so a `@PostConstruct` loader sees an empty database. `NetworkService`,
`BusSimulator`, `StopGraph` and `FactAssembler` all load on `ApplicationReadyEvent` with an
explicit `@Order` instead. Follow that pattern for anything new that reads seed data.

**Reset the world before each query.** `JourneyPlanningService` and `OpsService` both call
`facts.resetWorld()` — reset *plus* re-assert of the standing facts in one step. Skip it and
facts from the previous request linger — which looks exactly like a hardcoded answer. Resetting
without re-asserting is just as wrong: `base_fare/2` would vanish and console goals fail.

**Never reintroduce a dwell multiplier on driving time.** Dwell is modelled explicitly now.
Multiplying driving time by crowding *as well* charges it twice.
`SimulationTest.drivingTimeDoesNotSecretlyIncludeDwell` guards this.

**Cost decreases break A\* admissibility, increases do not.** Adding cost to an edge is
always safe. Making an edge cheaper than the heuristic assumes is not — sub-1.0 night-time
traffic factors did exactly that once, and
`AStarHeuristicTest.heuristicSpeedBeatsEverySegmentInEveryTrafficBucket` now catches it at
any hour of day.

## Tests

```bash
./mvnw test
```

| Suite | Covers |
|---|---|
| `AStarHeuristicTest` | h never overestimates, across every reachable stop pair and every traffic bucket |
| `PrologBridgeTest` | fare rules, constraint rules, bonus eligibility, goal parsing |
| `SimulationTest` | dwell scaling, driving/waiting states, arrival prediction ordering and bands |

## If Maven cannot reach Maven Central

Only relevant if you see `PKIX path building failed` — that means antivirus HTTPS scanning is
re-signing certificates with a root the JDK does not trust. See the Avast section in the root
[README](../README.md). The fix is local to your machine and intentionally not committed.
