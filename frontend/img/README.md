# Illustrations

Drop three PNGs here and refresh — no build, no restart, the frontend is served from disk.

| File | Appears | Shape it should be |
|---|---|---|
| `hero-bus.png` | beside the "Plan a trip" heading | wide banner, ~3.3:1 — the bus and pagoda scene |
| `tips-family.png` | behind the Tips card copy | wide banner with an **empty left half** — the family at the stop |
| `people.png` | foot of the sidebar | wide banner — the four people waving |

Until a file exists, `paintArt()` in `app.js` draws an SVG in its place. That is a stand-in
so a fresh checkout is not full of empty boxes; it is **not** the intended artwork and it
disappears the moment the PNG is there.

Never commit a flat-colour placeholder here. A blank rectangle reads as a rendering bug,
which is worse than the drawing it replaced.
