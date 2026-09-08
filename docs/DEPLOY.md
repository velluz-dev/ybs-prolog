# Deploying

The app is one Spring Boot process and a MySQL database. The frontend has no build step —
`pom.xml` copies `frontend/` into the jar as static resources — so there is nothing else to
serve and nothing else to deploy.

## The short version

```bash
cp .env.example .env          # then set real passwords in it
docker compose up --build     # http://localhost:8081
```

That builds the jar, starts MySQL, waits for it to be genuinely ready rather than merely
listening, creates the schema from the JPA entities and seeds `data.sql`.

## Without Docker

```bash
cd backend && ./mvnw package -DskipTests    # → target/ybs-demo-0.0.1-SNAPSHOT.jar

SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:mysql://dbhost:3306/ybs_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Yangon' \
DB_USERNAME=ybs DB_PASSWORD='…' \
java -jar target/ybs-demo-0.0.1-SNAPSHOT.jar
```

Java 17 or newer. The jar is ~61 MB and self-contained.

`spring.web.resources.static-locations` lists `file:../frontend/` first, which is the
development convenience that lets a frontend edit appear on refresh. Beside a deployed jar
that path does not exist, Spring skips it, and the packaged copy is served — so the same
build works in both places without a flag.

## Why there is a `prod` profile

`application.properties` is tuned for a laptop. Three of its choices are actively wrong once
the app has an address, and `application-prod.properties` corrects each:

| Development | Production | Why |
|---|---|---|
| `DB_PASSWORD` defaults to `root` | no default — startup fails without it | a deploy pointing at the wrong database should fail loudly, not quietly |
| every cache disabled (`no-store`, `period=0`) | `max-age=1h` | otherwise every visitor re-downloads ~2 MB of illustrations on every page view |
| session cookie plain | `secure`, `http-only`, `same-site=strict` | the CSRF token is deliberately JS-readable, which is only safe over TLS |

It also sets `server.forward-headers-strategy=framework`, so behind nginx or Caddy the app
sees the external `https` rather than the `http` of the internal hop.

## The demo accounts will stop you, on purpose

`data.sql` seeds working accounts — `admin/admin123` and one per driver — and the whole seed
is `INSERT IGNORE`, so **every fresh database gets them**. That is right on a laptop and an
open door on a public address: the plaintext is in a comment in a public repository.

Under the `prod` profile, `DemoCredentialGuard` checks whether those passwords still work
and **stops the application** if they do:

```
Refusing to start: these accounts still use the passwords published in data.sql -
admin, umyintswe, ukyawwin, dawkhinmalar, uthanhtun, uaungmyo, dawsansanaye.
```

Three ways past it, in order of preference: change the passwords in the database, replace
the BCrypt hashes in `data.sql` and reseed, or set `YBS_ALLOW_DEMO_LOGINS=true` if the
instance is *meant* to be open — a viva, a walkthrough, a box you throw away afterwards.

With `restart: unless-stopped` a blocked app crash-loops rather than exiting once. That is
noisy in the logs and it is the intended shape: it never serves a request.

## The voice model is optional

`demo.voice.mode=recorded` is the default and needs no Python at all — the voice step answers
from the packaged transcripts. Only set `mms` if `tools/voice/server.py` is actually running
and reachable, since the client otherwise pays a timeout on every query before falling back.
It is not in `docker-compose.yml` because it wants ~4 GB of model weights and a GPU to be
tolerable, and the default stack should run on a laptop. See [VOICE.md](VOICE.md).

## Before you deploy anything

Run the tests against a real database. They are all `@SpringBootTest` and H2 is no longer on
the classpath, so they need MySQL:

```bash
docker compose up -d db
docker run --rm --network ybs_default \
  -v "$HOME/.m2:/root/.m2" -v "$PWD:/src" -w /src/backend \
  -e DB_URL='jdbc:mysql://db:3306/ybs_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Yangon' \
  -e DB_USERNAME=ybs -e DB_PASSWORD="$DB_PASSWORD" \
  maven:3.9-eclipse-temurin-17 mvn test
```

All 87 should pass. There is no CI in this repository, so this is the only thing standing
between a bad merge and a demo that fails in front of an examiner.

## Sizing

2 GB of RAM is comfortable for both containers. The JVM is told
`-XX:MaxRAMPercentage=75` rather than a fixed heap, so the same image behaves on a small box
and a large one. MySQL wants `utf8mb4` — set in the compose file, and not optional, because
MySQL's older three-byte `utf8` cannot store every codepoint the Burmese stop names use.
