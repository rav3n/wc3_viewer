# wc3-viewer

In-browser analyzer for Warcraft III replays (`.w3g`). Goes deeper than the match page on W3Champions: metrics over time, side-by-side build orders, hero skill order, hotkey usage and an economy estimate, in the spirit of StarCraft II replay tools.

Everything runs on the client. Drop a replay into the page and it is parsed and analyzed locally; nothing is uploaded.

## Scope

- Patch 3.0+ (current W3Champions ladder).
- 1v1 only. Team games and broken files get a clear error message.
- UI in English.

## Features

- **Overview**: head-to-head stats (APM, EPM, spam share, hotkey groups), hero and tier timings, key differences between players.
- **Timeline**: both players' heroes, skills, buildings, units, research and items on one time axis, with a shared hover cursor.
- **Economy** (estimate): cumulative gold and lumber spent, food used vs cap with upkeep thresholds, spending by category.
- **Build order**: two columns side by side on a shared time scale, filters by object type.
- **APM & hotkeys**: APM/EPM over time, hotkey heatmap (key × minute).
- **Heroes**: hire order, skill order, level (lower bound).

### What a replay does and does not contain

A `.w3g` file stores player commands only, not game state. Exact: APM, hotkeys, build and research orders, hero skills, item purchases, winner (on W3Champions replays). Estimated from orders and marked `≈ ESTIMATE` in the UI: resources spent, food, hero level. Not available at all: unit deaths, resources gathered, unit positions, fight outcomes.

## Tech stack

- Kotlin 2.4.21, Kotlin Multiplatform: `js(IR)` browser target for the app, `jvm` target for fast logic tests.
- Compose Multiplatform 1.12.2 on the JS target (canvas rendering via Skiko, no Wasm build of the app).
- Gradle 9.8.2, JDK 17.
- Own `.w3g` parser and own inflate implementation (replay zlib streams are not terminated, so standard decoders fail).

## Modules

| Module | Purpose |
|---|---|
| `:domain` | Entities, game events, repository interfaces |
| `:core:binary` | Little-endian byte reader, inflate |
| `:data:parser` | `.w3g` parser: header, blocks, metadata, actions → domain events |
| `:data:gamedata` | Game data repository, Kotlin code generated from `gamedata/3.0/*.json` at build time |
| `:analytics` | Metric collectors (APM/EPM, build order, heroes, hotkeys, economy) |
| `:presentation` | View model and UI state mapping |
| `:app:web` | Compose web UI: theme, components, charts, screens |

## Getting started

```sh
# Run the app with live reload, then open the printed URL (http://localhost:8080/)
./gradlew :app:web:jsBrowserDevelopmentRun --continuous

# Tests (JVM + Node)
./gradlew jvmTest jsNodeTest

# Production bundle -> app/web/build/dist/js/productionExecutable/
./gradlew :app:web:jsBrowserDistribution
```

Dev helpers: `?replay=<same-origin url>` loads a replay on start, `?tab=<overview|timeline|economy|build|apm|heroes>` opens a tab. Example: `http://localhost:8080/?replay=/testdata/replays/3.00/6ae0bcf801d5ffcb2ad75e54.w3g&tab=heroes`.

## Repository layout

```
docs/product/PRD.md                 product requirements, metric catalog (Russian)
docs/architecture/ARCHITECTURE.md   modules, interfaces, ADRs (Russian)
docs/architecture/W3G_FORMAT.md     .w3g format notes verified on real files (Russian)
docs/testing/TEST_STRATEGY.md       test strategy, coverage matrix (Russian)
gamedata/3.0/                       game data JSON collected from Liquipedia
gamedata/sources/                   w3gjs ID mappings (MIT)
tools/gamedata/                     Liquipedia collector (Python, stdlib only)
testdata/replays/                   replay fixtures: 3.00/ (1v1), team/, broken/
testdata/expected/                  golden values from an independent Python generator
testdata/w3c-api/                   W3Champions API responses used for cross-checking
testdata/tools/                     golden generator and API comparison scripts
```

## Data sources and licenses

- Game data (costs, build times, food, abilities): [Liquipedia Warcraft](https://liquipedia.net/warcraft/), CC BY-SA 3.0. Attribution and revision IDs are kept in every JSON file in `gamedata/3.0/`. See `gamedata/README.md`.
- Object ID names: [w3gjs](https://github.com/PBug90/w3gjs) `mappings.ts`, MIT.
- Font: JetBrains Mono, SIL Open Font License (`app/web/src/jsMain/resources/fonts/`).
- No Blizzard art or icons are bundled.

Warcraft is a trademark of Blizzard Entertainment. This project is not affiliated with Blizzard Entertainment or W3Champions.
