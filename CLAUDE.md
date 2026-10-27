# wc3-viewer — working context

In-browser Warcraft III replay (`.w3g`) analyzer. Kotlin/JS + Compose Multiplatform, Clean Architecture. Read `README.md` first, then the docs below. This file is the hand-off context: rules, decisions, current state, what is left.

## Rules

- Code comments (KDoc, inline, TODO) — English only. Design docs in `docs/` are in Russian. Talk to the project owner in Russian.
- Commits, PRs, branch names, README: English, and **nothing about AI** — no `Co-Authored-By` trailers for assistants, no "Generated with …" lines, no mentions of assistants or agents.
- UI shows human-readable names only. A raw fourcc ID (`Hamg`, `AOmi`) appears only when game data has no name for it. No cryptic abbreviations in UI (use `≈ ESTIMATE`, not `EST`).
- Replays are untrusted input: parsing must never crash or hang; unknown actions skip the rest of their command block and emit a diagnostic.

## Decisions (owner, 2026-10-27)

- Browser only, no backend. Target `js(IR)` — **not** Wasm (owner rejected Wasm-first).
- MVP: 1v1, single match. Patch 3.0+ only. UI language English.
- Economy/army are estimates from orders (replay has no game state); always marked `≈ ESTIMATE` with an honest caption.
- EPM = APM minus repeats: same order with the same target/point within 500 ms, re-selecting the same group, re-pressing the same hotkey group. Repeated production orders (Train/Build) are **not** spam.
- Game data source: Liquipedia via MediaWiki API (ADR 14), not game SLK files.
- Design: done directly in code (no Figma), dark esports style (like sc2replaystats), match page = header + tabs Overview / Timeline / Economy / Build order / APM & hotkeys / Heroes. Owner approved the current look.

## Docs

- `docs/product/PRD.md` — personas, metric catalog (exact vs estimate, MVP/v2), screens, roadmap.
- `docs/architecture/ARCHITECTURE.md` — modules, interfaces, metric algorithms, ADRs 1–14, open questions (§10).
- `docs/architecture/W3G_FORMAT.md` — format with ✔ marks for facts verified on our replays. Key points: action IDs from 0x77 are shifted on patch 3.0 even without Reforged chunk 0x38 (detect by version/build, not by chunk); `0x7A` precedes most orders and carries the ability ID; winner = player with leave result `0x09` after filtering observers.
- `docs/testing/TEST_STRATEGY.md` — test pyramid, tolerances, negative cases, coverage matrix, missing replays.
- `gamedata/README.md`, `gamedata/3.0/UNMATCHED.md` — game data schema, provenance, known gaps.
- `testdata/README.md` — fixtures, golden fields, how to regenerate.

## Current state

Done and green (`./gradlew jvmTest jsNodeTest`, 105 tests):

- Parser: header, blocks, metadata, slots, time slots, command blocks, full action table for 3.0; observer (FLO bot, team 24) filtering; winner; match end = last player leave (FLO stays ~45–50 s longer than the header says); Random race resolved from first orders.
- Own inflate, byte-identical to `java.util.zip` on all replay blocks.
- Metrics: APM (w3gjs rules), EPM, build order, hero skills (double-click dedupe < 500 ms, reset after Tome of Retraining), hotkeys, economy estimate (spent gold/lumber, food used/cap, categories, queue-cancel refunds).
- Game data: 791 objects generated into Kotlin at build time; 99.8% of IDs in fixtures get a name (missing: `ritd`, `hmtt`).
- UI: landing/drop zone, loading, errors, all six match tabs, synced chart cursors, hover tooltips.
- Golden tests: 21 1v1 replays checked against `testdata/expected` (independent Python generator) and cross-checked with the W3Champions API (players, races incl. Random, winner, map, hero set — all match; API durations are wall-clock and not usable as a reference).

## What is left

MVP gaps (PRD §3, §4):

1. **Food chart is misleading** after the first big fight: food used grows past the 100 cap because deaths are not in the replay. Proposed (owner has not chosen yet): clamp to cap and show "ordered above cap" as a loss signal, or show the chart only until the first big fight.
2. Army estimate: produced army composition and produced army value over time (PRD "Армия и бои").
3. Main-building idle time estimate; supply at order time in build order; estimated completion times.
4. Hero death detection via revive orders.
5. Item purchases view (data is parsed, UI is minimal).
6. Tooltip on `≈ ESTIMATE` badges explaining why the value is an estimate (offered to owner, not confirmed).
7. Offline/PWA, production deploy (static hosting), bundle size (production dist is ~35 MB including Skiko wasm and source map).
8. Web Worker for parsing — not needed yet (tens of ms), the parser port is ready for it.

v2 and later (PRD §7): multi-match trends, reference replay comparison, creep routes (needs a camp database per map), export/sharing, team modes, build recognition.

Open questions: ARCHITECTURE.md §10 and TEST_STRATEGY.md (hotkey key mapping `(group + 1) % 10` unverified; meaning of action `0x1B`; numeric order IDs `0x000D00xx`; order flag bits; whether W3C maps override object data; winner for replays without `0x09`).

Replays still wanted (put new ones into a local `Replay_*/` drop folder, it is git-ignored, then curate into `testdata/replays/`): the same match recorded by both players (does the leave result depend on who recorded?), long high-APM games (40+ min, 300+ APM), draws/disconnect-ended games.

## Workflow notes and gotchas

- Run the app: `./gradlew :app:web:jsBrowserDevelopmentRun --continuous` → http://localhost:8080/. The dev server serves the repo root, so `?replay=/testdata/replays/3.00/<file>.w3g&tab=<tab>` works.
- When the dev server shows an old version, check the Gradle log: failed compilations keep the previous bundle. Do not run other Gradle builds of `:app:web` in parallel with the continuous server — they share build dirs and cause `cannot find external signature` / spurious failures; stop the server or retry.
- Headless Chrome screenshots need `--use-angle=swiftshader` (Skiko needs WebGL; `--disable-gpu` breaks it). Use `--virtual-time-budget=8000`.
- New fixture replay: copy into `testdata/replays/3.00/`, run `testdata/tools/gen_expected.py` then `testdata/tools/compare_api.py --write` (see `testdata/README.md`); golden tests pick up every file in the folder automatically.
- Game data refresh: `python3 -I tools/gamedata/fetch.py` (respects Liquipedia API terms: own User-Agent, ≥ 2 s between requests, raw cache in `tools/gamedata/cache/`, git-ignored), then `python3 -I tools/gamedata/build.py`. Run Python with `-I`.
- W3Champions API for verification: `https://website-backend.w3champions.com/api/matches/{id}` (GET; fixture file names `6ae0….w3g` are match IDs).
