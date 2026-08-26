# Setup — getting the demo running

For teammates joining the project. Target: from nothing to a running demo in about five
minutes.

## What you need

| | Why | Check |
|---|---|---|
| **A JDK, 17 or newer** | builds and runs everything | `java -version` |
| **Git** | to clone | `git --version` |

That is the whole list.

**You do not need Maven** — the project ships the Maven Wrapper (`mvnw`), which downloads
the right Maven version on first build.

**You do not need Node, npm or any JS toolchain.** The frontend is plain HTML, CSS and
vanilla JS with no build step. There is no `package.json` anywhere and that is deliberate.

**You do not need a database.** H2 runs in memory and reseeds from `data.sql` on every
startup, so the demo always resets clean.

### If you have no JDK

Install Temurin (free, no account): <https://adoptium.net/temurin/releases/>

Any of 17, 21 or 25 works. The project compiles to Java 17 bytecode, so pick whichever your
other coursework already uses. Verify:

```bash
java -version      # any 17+ is fine
```

---

## Run it

```bash
git clone https://github.com/velluzHTA-dev/ybs-prolog.git
cd ybs-prolog/backend
./mvnw spring-boot:run
```

On Windows in PowerShell or cmd, use `.\mvnw.cmd spring-boot:run` instead.

Then open **<http://localhost:8081>**.

**The first build takes a few minutes and needs the internet** — it downloads Maven and
about 40 dependencies into `~/.m2`. Every build after that is offline and takes seconds.
This matters for demo day: build once beforehand, and the venue's wifi becomes irrelevant.

### Check it actually works

```bash
curl http://localhost:8081/api/health          # {"status":"ok"}
```

In the browser you should see 236 numbered bus markers moving once a second across Yangon,
each coloured by its route. The map starts with no route lines drawn - switch any of the 118
routes on from the filter to draw its road-following line and its stops. Press **60×** in the header and they visibly
speed up; some will stop at stops and turn amber while passengers board.

### Run the tests

```bash
cd backend
./mvnw test        # 23 tests
```

---

## Which folder do I work in?

| You are changing | Edit | To see it |
|---|---|---|
| UI, layout, Burmese copy, map behaviour | `frontend/` | **just refresh the browser** |
| Search, Prolog, simulation, APIs | `backend/src/main/java/` | restart `./mvnw spring-boot:run` |
| Stops, routes, fares, drivers, traffic prior | `backend/src/main/resources/data.sql` | restart |
| Reasoning rules | `backend/src/main/resources/rules/ybs.pl` | restart |

Frontend changes are live because the backend serves `../frontend` straight from disk. No
rebuild, no restart. If a change does not show, hard-refresh with **Ctrl+F5** — that is the
browser cache, not the server.

Then read [`frontend/README.md`](../frontend/README.md) for the full REST contract, or
[`backend/README.md`](../backend/README.md) for the package map and the traps to avoid.

---

## ⚠ Never edit anything under `target/`

`backend/target/` is **generated output**. It contains copies of `ybs.pl`, `data.sql`,
`app.js` and everything else, and it is deleted and rebuilt on every `./mvnw clean`.

Editing `backend/target/classes/rules/ybs.pl` looks like it works — the running app may even
pick it up — and then your change vanishes at the next build with no warning and nothing in
Git. It is the single easiest way to lose an hour on this project.

**The real files are always under `src/` or `frontend/`.** `target/` is gitignored, so if
your change does not appear in `git status`, you edited the wrong copy.

---

## IDE setup

Neither is required — `./mvnw` is enough — but both work with no configuration.

**VS Code**: install the *Extension Pack for Java*. Open the **`backend`** folder (not the
repo root), or the Java tooling will not find `pom.xml`. Open the repo root in a second
window for `frontend/`.

**IntelliJ IDEA**: *File → Open* → select `backend/pom.xml` → *Open as Project*.

---

## Troubleshooting

### `Web server failed to start. Port 8081 was already in use.`

Something else has the port — often a previous run of this app that did not shut down.

```bash
# Windows
netstat -ano | findstr :8081        # last column is the PID
taskkill /PID <pid> /F

# macOS / Linux
lsof -ti:8081 | xargs kill -9
```

Or change `server.port` in `backend/src/main/resources/application.properties`.

### `PKIX path building failed` while downloading dependencies

Your antivirus is intercepting HTTPS and re-signing certificates with a root the JDK does not
trust. Avast, Kaspersky, ESET and Bitdefender all do this.

Easiest fix: turn off the antivirus's **HTTPS/SSL scanning** for the first build, then turn it
back on. Alternatively see the Avast section in the root [README](../README.md) for how to
build a project-local truststore instead — that leaves your system JDK untouched.

### `release version 17 not supported` or similar

Your JDK is older than 17. Check with `java -version` and install a newer one from Adoptium.

### `./mvnw: Permission denied` (macOS / Linux)

```bash
chmod +x backend/mvnw
```

### `./mvnw` not found, or "not recognized"

You are in the wrong directory. `mvnw` lives in `backend/`, not the repo root:

```bash
cd backend && ./mvnw spring-boot:run
```

### Burmese text shows as boxes (□□□)

Your OS lacks a Myanmar font. Install **Padauk** or **Noto Sans Myanmar** — the CSS asks for
both. On Windows 10/11, *Settings → Time & Language → Language → Add a language → Burmese*
installs the fonts.

### The map is blank but routes and buses still show

Map tiles come from OpenStreetMap and need the internet. Everything demo-critical — routes,
stops, buses, alerts — is drawn by the app and works offline. A note appears on the page when
tiles fail. This is expected behaviour, not a bug.

---

## Committing

The project uses [Conventional Commits](https://www.conventionalcommits.org/):

```
feat:     a new capability
fix:      a bug fix
refactor: restructuring with no behaviour change
docs:     documentation only
test:     tests only
chore:    build, tooling, dependencies
```

Example: `feat: add night-bus warning to journey cards`

Please run `./mvnw test` before pushing. All 21 should pass.
