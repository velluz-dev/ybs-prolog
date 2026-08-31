# Database — ER diagram

Nine tables in H2, created by Hibernate from the JPA entities (`ddl-auto=create-drop`) and
seeded from `data.sql` on every startup. There are no migrations: the schema is whatever the
entities say, and the data is whatever `data.sql` says.

Everything below was read off the entity classes rather than written from memory.

---

## The diagram

```mermaid
erDiagram
    ROUTE ||--o{ ROUTE_STOP : "has an ordered itinerary of"
    STOP  ||--o{ ROUTE_STOP : "is visited at"
    ROUTE ||--o{ BUS        : "is run by"
    ROUTE ||--o{ DRIVER     : "is assigned"
    DRIVER ||--o{ DRIVER_MONTHLY_SCORE : "is scored each month"
    DRIVER ||--o| APP_USER  : "signs in as"
    STOP  ||--o{ INCIDENT   : "is the site of"
    ROUTE ..o{ INCIDENT     : "route_id, no FK"

    APP_USER {
        bigint  id PK
        varchar username      "UNIQUE, NOT NULL"
        varchar password_hash "BCrypt, NOT NULL - never the password"
        varchar role          "ADMIN or DRIVER. Riders have no row at all"
        bigint  driver_id     FK "set for DRIVER, null for ADMIN"
    }

    ROUTE {
        bigint  id PK
        varchar code          "e.g. 43, 37-Okekan. Looked up by name, NOT unique"
        varchar name_my       "Burmese, NOT NULL"
        int     base_fare_kyats "flat fare per boarding, NOT NULL"
    }

    STOP {
        bigint  id PK
        varchar name_my       "Burmese, NOT NULL"
        varchar name_en       "NOT NULL"
        double  lat           "NOT NULL"
        double  lon           "NOT NULL"
        varchar landmark_photo_url "nullable"
    }

    ROUTE_STOP {
        bigint  route_id      PK "part of composite key, FK to ROUTE"
        int     sequence_no   PK "position on the route, 0-based"
        bigint  stop_id       FK "NOT part of the key, NOT NULL"
        int     cumulative_minutes "free-flow minutes from the first stop"
    }

    BUS {
        bigint  id PK
        bigint  route_id      FK
        double  current_lat
        double  current_lon
        int     current_segment_index
        double  progress_along_segment "0..1 along the current segment"
        int     crowding_level "0 free, 1 standing, 2 full"
    }

    DRIVER {
        bigint  id PK
        varchar name          "NOT NULL"
        bigint  route_id      FK "nullable"
    }

    DRIVER_MONTHLY_SCORE {
        bigint  driver_id     PK "part of composite key, FK to DRIVER"
        varchar month_key     PK "e.g. 2026-08"
        int     headway_score
        int     harsh_events
        int     upheld_complaints
        boolean bonus_eligible "stored but ALWAYS recomputed by Prolog"
    }

    INCIDENT {
        bigint    id PK
        varchar   type      "enum as STRING: BREAKDOWN, CROWDING, FLOOD, ..."
        bigint    route_id  "plain column - NOT a foreign key"
        bigint    stop_id   FK "nullable"
        timestamp timestamp
    }

    TRAFFIC_FACTOR {
        bigint  id PK        "surrogate; the natural key is (segment_id, bucket)"
        varchar segment_id   "DEFAULT, or routeCode:fromStopId-toStopId"
        varchar bucket       "morning_peak, midday, evening_peak, night"
        double  factor       "1.0 = free flow, >1 slower, <1 faster"
    }
```

`TRAFFIC_FACTOR` stands alone: it is keyed by a *string* segment id rather than by a foreign
key, so it can describe a segment that has no row of its own and fall back to `DEFAULT`.

---

## Design decisions worth defending

### `route_stop` is keyed by (route, **sequence**) — not (route, stop)

The obvious key is (route, stop), and that is what this started as. It is wrong. A route can
legitimately visit the same stop twice: loop services return to their terminus, and some
routes double back through a junction. In the real YBS data **23 route/stop pairs repeat**,
and a (route, stop) key rejected them outright.

Keying by position fixes it, because a position is genuinely unique on a route. `stop_id`
becomes an ordinary non-key foreign key.

### `bus` carries no dwell column

`Bus.dwellSecondsRemaining` is `@Transient` — live simulation state, not something the seed
data describes. Persisting it would mean writing a value every tick that nobody reads on the
next startup.

### `driver_monthly_score.bonus_eligible` is stored but never trusted

The column exists and `data.sql` seeds it `false` for **every** driver. The ops dashboard
recomputes it per request by asking Prolog `bonus_eligible(dN, 'YYYY-MM')`. Seeding it false
is deliberate: if the rule engine ever died, every badge would read "not eligible" — a
visible failure rather than stale data quietly passing as a correct answer.

### `id` values are explicit in the seed

Every table uses `GenerationType.IDENTITY`, but `data.sql` supplies ids anyway, because
`route_stop`, `bus`, `driver_monthly_score` and `incident` all reference them. H2 accepts
explicit ids into an identity column, which is what makes the seed reproducible.

---

## Two gaps you should know about

These are real, and worth being able to answer rather than being caught by.

**No unique constraints anywhere.** There is not one `unique = true` or `@UniqueConstraint`
in the schema. Two cases matter:

- `route.code` is used as a lookup key — `NetworkService.routeByCode`, and the URL
  `/api/routes/{code}`. Two routes sharing a code would make that lookup silently return
  whichever came first.
- `traffic_factor` has a surrogate `id`, so nothing stops two rows for the same
  `(segment_id, bucket)`. The admin screen upserts by reading first, so it will not create
  duplicates itself, but a hand-written `data.sql` could, and the second row would simply be
  ignored by `findBySegmentIdAndBucket`.

**`incident.route_id` is a plain `bigint`, not a foreign key.** Every other route reference
(`route_stop`, `bus`, `driver`) is a real `@ManyToOne`. `Incident` declares
`private Long routeId`, so the database will happily accept an incident on a route that does
not exist. It is drawn as a dashed line above for that reason.

Both are safe as the demo stands — the data is generated and validated before it is seeded —
and both would need fixing before anything real wrote to this database.

---

## Where the schema lives

| Thing | Where |
|---|---|
| Table definitions | the JPA entities, one per feature package |
| Seed data | `backend/src/main/resources/data.sql` |
| Schema creation | Hibernate `ddl-auto=create-drop`, every startup |
| Editing data | `/admin.html`, then export a new `data.sql` |

There is no migration tool and no schema file to edit by hand. Change an entity and the table
changes with it on the next run.
