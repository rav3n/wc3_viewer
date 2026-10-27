# WC3 Replay Analyzer — архитектура

Браузерное приложение без бэкенда. Пользователь бросает `.w3g`, получает аналитику матча 1v1: экономику и армию (оценка), APM/EPM, хоткеи, билд-ордер, тайминги, героев, навыки и предметы.

Формат файла — в [W3G_FORMAT.md](W3G_FORMAT.md).

## 1. Стек

| Что | Версия (проверено 2026-10-27) |
|---|---|
| Kotlin | 2.4.21 (Maven Central). 2.5.0 пока в Beta |
| Compose Multiplatform | 1.12.2 (1.13.0 — beta01) |
| Gradle | 9.8.2 (services.gradle.org/versions/current) |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization | 1.12.0 (только для codegen и отладочного экспорта) |
| Koala Plot | 0.12.1 (MIT, есть js и wasm артефакты) — запасной вариант для графиков |
| JDK для сборки | 21 |

Перед стартом обновить версии в `libs.versions.toml`. Совместимость Kotlin и CMP сверить по [таблице JetBrains](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).

## 2. Kotlin/JS

**Решение пользователя 2026-10-27: только Kotlin/JS (`js(IR) { browser() }`), без `wasmJs`.**

Compose Multiplatform для web рисует UI в `<canvas>` (Skiko). CMP 1.12.2 публикует артефакты для `js`-таргета (`ui-js`, `foundation-js`), `ComposeViewport` работает на чистом `js(IR)` без Wasm-бандла. Skiko-рантайм (`skiko.mjs` + `skiko.wasm`) плагин кладёт в ресурсы сам.

- **Логика** (domain, parser, analytics, gamedata) — `commonMain` без платформенных зависимостей. Таргеты: `jvm` (быстрые тесты) и `js(IR)` (`browser()` + `nodejs()` для `jsNodeTest`).
- **UI** (`:app:web`) — только `js(IR)`. Платформенный код (файлы, drag-and-drop) — в `jsMain`.
- JS-интероп нам почти не нужен: графики рисуем на Compose Canvas, inflate написан на Kotlin.

## 3. Модули

```
                    ┌──────────────────────────────┐
                    │          :app:web            │  composition root, Compose UI,
                    │  (js(IR), jsMain)            │  графики, file input, DI вручную
                    └──────┬─────────┬─────────┬───┘
                           │         │         │
              ┌────────────▼──┐      │         │
              │ :presentation │      │         │   ViewModel, UiState (без Compose UI)
              └──────┬────────┘      │         │
                     │               │         │
        ┌────────────▼───┐  ┌────────▼───────┐ │ ┌──────────────────┐
        │  :analytics    │  │ :data:parser   │ └─► :data:gamedata   │
        │ метрики        │  │ w3g → events   │   │ справочник по    │
        └───────┬────────┘  └──┬─────────┬───┘   │ патчам (codegen) │
                │              │         │       └────────┬─────────┘
                │              │   ┌─────▼───────┐        │
                │              │   │ :core:binary│        │
                │              │   │ reader,     │        │
                │              │   │ inflate     │        │
                │              │   └─────────────┘        │
                │              │                          │
                └──────────────▼──────────────────────────▼
                          ┌──────────┐
                          │ :domain  │  сущности, порты, use cases
                          └──────────┘
   tools/gamedata (Python, Liquipedia) ──► gamedata/3.0/*.json ──codegen──► :data:gamedata
```

### Правило зависимостей

- `:domain` ни от кого не зависит (кроме `kotlinx-coroutines-core` ради `suspend`/`Flow`).
- `:data:*` и `:analytics` зависят только от `:domain`. `:data:parser` ещё от `:core:binary`.
- `:analytics` не знает о парсере и формате файла. Работает только с доменными событиями.
- `:presentation` зависит от `:domain` и **интерфейсов** метрик. Конкретные реализации не трогает.
- `:app:web` собирает граф: создаёт парсер, репозиторий, реестр метрик, ViewModel.
- Compose UI есть только в `:app:web`.

Правило проверяется сборкой: модуль не объявляет запрещённую зависимость. Опционально — тест на Konsist для `jvmTest`.

### Структура каталогов

```
wc3-viewer/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── build-logic/                         # convention plugins
│   └── src/main/kotlin/
│       ├── kmp-library.gradle.kts       # jvm + js(IR) browser/nodejs, commonTest
│       └── kmp-web-app.gradle.kts       # compose, js(IR) browser executable
├── core/binary/src/commonMain/kotlin/…/binary/
│   ├── ByteReader.kt                    # LE u8/u16/u32/f32, cstr, fourcc, bounds-check
│   └── Inflater.kt                      # pure-Kotlin DEFLATE (zlib, без требования конца потока)
├── domain/src/commonMain/kotlin/…/domain/
│   ├── model/        Replay, Player, Race, GameEvent, ObjectId, GameTime, Patch
│   ├── gamedata/     GameObject, Cost, GameDataRepository
│   ├── metric/       Metric, MetricId, MetricResult, AnalysisContext
│   ├── port/         ReplayParser
│   └── usecase/      AnalyzeReplay
├── data/parser/src/commonMain/kotlin/…/parser/
│   ├── W3gParser.kt                     # реализует ReplayParser
│   ├── container/    HeaderReader, BlockReader
│   ├── metadata/     MetadataReader, EncodedSettings, ReforgedChunks (protobuf-lite)
│   ├── gamedata/     GameBlockReader, TimeSlotReader
│   ├── action/       ActionDialect, ActionCodecs (Legacy, Reforged, Post202)
│   ├── raw/          RawReplay, RawAction (sealed)
│   └── mapping/      RawToDomainMapper
├── data/parser/src/jvmTest/             # golden-тесты на реплеях + сверка с java.util.zip
├── data/gamedata/
│   ├── src/commonMain/resources-src/    # JSON — источник правды, по патчам
│   │   └── (JSON лежит в корневом gamedata/3.0/, см. 6.3)
│   └── build/generated/…                # Kotlin-код из JSON (Gradle task)
├── analytics/src/commonMain/kotlin/…/analytics/
│   ├── MetricRegistry.kt
│   ├── apm/  hotkeys/  buildorder/  heroes/  economy/  (timings — в ReportMapper; army — позже)
├── presentation/src/commonMain/kotlin/…/presentation/
│   ├── ReplayViewModel.kt  ReplayUiState.kt  model/ (UI-модели графиков)
├── app/web/src/
│   ├── jsMain/kotlin/…/ui/              # Compose UI, см. 7.4
│   ├── jsMain/kotlin/…/platform/        # FileSource (input/drag-drop), Worker bridge
│   └── jsMain/resources/index.html
├── tools/gamedata/                      # Python: Liquipedia API → gamedata/3.0/*.json (см. 6.2)
├── testdata/replays/                    # фикстуры .w3g (+ ожидаемые JSON-снимки)
└── docs/architecture/
```

## 4. Ключевые интерфейсы

### 4.1 Domain

```kotlin
@JvmInline value class ObjectId(val raw: Int) {          // fourcc: 'hpea', 'Hpal'
    val code: String get() = /* 4 символа из raw */
    companion object { fun of(code: String): ObjectId }
}
@JvmInline value class GameTime(val ms: Long)
@JvmInline value class PlayerId(val value: Int)

enum class Race { HUMAN, ORC, NIGHT_ELF, UNDEAD, RANDOM, UNKNOWN }

data class Patch(val major: Int, val minor: Int, val build: Int)   // 3.00 / 7003

data class Player(
    val id: PlayerId, val name: String, val race: Race, val detectedRace: Race?,
    val team: Int, val color: Int, val isObserver: Boolean,
)

data class Replay(
    val patch: Patch,
    val duration: GameTime,
    val map: MapInfo,
    val players: List<Player>,
    val events: List<GameEvent>,          // отсортированы по времени
    val chat: List<ChatMessage>,
    val leaves: List<LeaveEvent>,
    val diagnostics: List<ParseDiagnostic>,
)

sealed interface GameEvent {
    val time: GameTime; val player: PlayerId
    data class Train(…, val unit: ObjectId, val queued: Boolean) : GameEvent        // юнит/герой
    data class Build(…, val building: ObjectId, val x: Float, val y: Float) : GameEvent
    data class Research(…, val upgrade: ObjectId) : GameEvent
    data class LearnSkill(…, val hero: UnitRef?, val ability: ObjectId) : GameEvent
    data class BuyItem(…, val item: ObjectId, val shop: UnitRef?) : GameEvent
    data class CancelQueue(…, val slot: Int, val obj: ObjectId) : GameEvent
    data class Order(…, val order: OrderKind, val ability: ObjectId?, val target: Target) : GameEvent
    data class Select(…, val mode: SelectMode, val units: List<UnitRef>) : GameEvent
    data class HotkeyAssign(…, val group: Int, val units: List<UnitRef>) : GameEvent
    data class HotkeySelect(…, val group: Int) : GameEvent
    data class ItemUse(…) : GameEvent
    data class Misc(…, val kind: MiscKind) : GameEvent    // ESC, submenu, pause…
}

data class ParseDiagnostic(val time: GameTime?, val offset: Int, val message: String)
```

`Train`/`Build`/`Research` — это **приказы**, а не факт появления юнита (см. W3G_FORMAT §7.2). Имена и UI это отражают.

### 4.2 Порты

```kotlin
interface ReplayParser {
    /** Бросает ReplayFormatException только на фатальных ошибках контейнера. */
    suspend fun parse(bytes: ByteArray): Replay
}

interface GameDataRepository {
    fun forPatch(patch: Patch): GameData          // ближайший ≤ patch, иначе последний
}

interface GameData {
    val patch: Patch
    fun find(id: ObjectId): GameObject?
}

sealed interface GameObject {
    val id: ObjectId; val name: String; val race: Race?
    data class Unit(…, val cost: Cost, val food: Int, val buildTimeSec: Int, val isWorker: Boolean) : GameObject
    data class Hero(…, val abilities: List<ObjectId>) : GameObject
    data class Building(…, val cost: Cost, val buildTimeSec: Int, val foodProvided: Int, val tier: Int?) : GameObject
    data class Upgrade(…, val levels: List<Cost>, val researchTimeSec: List<Int>) : GameObject
    data class Item(…, val cost: Cost) : GameObject
    data class Ability(…, val hero: ObjectId?, val isUltimate: Boolean) : GameObject
}
data class Cost(val gold: Int, val lumber: Int)
```

### 4.3 Метрики (точка расширения)

Одна метрика — один класс. Метрика получает события по одному (один проход по `Replay.events`) и в конце возвращает результат.

```kotlin
@JvmInline value class MetricId(val value: String)

interface MetricResult        // маркер; конкретные результаты — data-классы метрик

interface Metric<R : MetricResult> {
    val id: MetricId
    fun newCollector(ctx: AnalysisContext): MetricCollector<R>
}

interface MetricCollector<R : MetricResult> {
    fun onEvent(event: GameEvent)
    fun finish(): R
}

class AnalysisContext(
    val replay: Replay,                 // метаданные, игроки; events отдаются через onEvent
    val gameData: GameData,
    val options: AnalysisOptions,       // окно APM, шаг таймлайна и т.п.
)

class AnalyzeReplay(
    private val parser: ReplayParser,
    private val gameData: GameDataRepository,
    private val metrics: List<Metric<*>>,
) {
    suspend operator fun invoke(bytes: ByteArray, options: AnalysisOptions = AnalysisOptions()): AnalysisReport
}

class AnalysisReport(val replay: Replay, val results: Map<MetricId, MetricResult>) {
    inline fun <reified R : MetricResult> get(id: MetricId): R? = results[id] as? R
}
```

Регистрация — явный список в `:analytics`, без рефлексии (её нет в Kotlin/JS):

```kotlin
object MetricRegistry {
    val default: List<Metric<*>> = listOf(
        ApmMetric, EpmMetric, BuildOrderMetric,
        HeroSkillsMetric, HotkeyMetric, EconomyEstimateMetric,   // ArmyEstimateMetric — позже
    )
}
```

Новая метрика:

1. Класс `Metric` + `MetricCollector` + `MetricResult` в `:analytics`.
2. Строка в `MetricRegistry.default`.
3. Карточка в UI: `MetricCard<R>` в реестре `metricCards` в `:app:web`.

Остальной код не меняется.

### 4.4 Метрики MVP: как считать

| Метрика | Алгоритм | Оговорки |
|---|---|---|
| APM | Считать actions по правилам w3gjs: 0x10–0x14, 0x16 (пара deselect+select = 1), 0x17, 0x18, 0x1C–0x1E, 0x61, 0x65–0x67. Не считать 0x19, 0x1A, 0x1B, 0x7A. Ряд по окнам 60 с + среднее за время игрока в игре | Мапперу нужен флаг `countsForApm` на событии |
| EPM | APM без повторов (решение пользователя): тот же приказ (order, цель/точка) в пределах 500 мс от предыдущего такого же; повторное выделение того же набора юнитов; повторный выбор той же хоткей-группы подряд. Порог — `AnalysisOptions.epmRepeatThresholdMs` | Своё определение. Описать в UI |
| Хоткеи | Группы 0–9: число назначений и использований, состав группы (типы юнитов по ID), использование по времени | Номер клавиши: w3gjs берёт `(group + 1) % 10`. Проверить |
| Билд-ордер | `Build`/`Train`/`Research`/`BuyItem` по времени, с food-счётчиком (оценка) | Время приказа, не завершения. Отмены 0x1E вычитать |
| Экономика (`EconomyEstimateMetric`) | Цены приказов `Train`/`Build`/`UpgradeBuilding`/`Research`/`BuyItem` нарастающим итогом по времени приказа; улучшения зданий по `upgradeCost`; первый герой бесплатный; уровень апгрейда = номер исследования; отмены из очереди (0x1E) возвращают цену. Food used — сумма food юнитов и героев, cap — сумма `foodProvided` зданий (≤ 100), пороги 50/80. Категории: economy/army/tech/heroes/items | Оценка. Смерти, отмены строительства и неудачные приказы не видны. Повторы отсекаются эвристиками (тот же герой, та же точка постройки, исследование до окончания предыдущего уровня) |
| Тайминги | Первые: 2-й/3-й герой, tier 2/3, экспанд (второе главное здание), ключевые апгрейды | Конфиг таймингов по расам в gamedata |
| Герои | Порядок героев, навыки по времени (`LearnSkill`), уровень = число навыков, ретрейн (`tret`/`tert`) | Логика w3gjs `inferHeroAbilityLevels` |
| Предметы | `BuyItem` по времени и стоимость | Покупка у нейтральных магазинов и в своих зданиях |
| Экономика (оценка) | Кумулятивно потрачено gold/lumber = Σ cost приказов; разбивка на рабочих, армию, технологии, здания, предметы | Нет данных о добыче, отказах по ресурсам и upkeep |
| Армия (оценка) | Кумулятивная стоимость и food заказанных боевых юнитов по времени | **Смертей в реплее нет** — это «произведено», а не «живо» |

В UI все оценки помечены «оценка по командам».

## 5. Парсер

### 5.1 Конвейер

```
ByteArray
  → HeaderReader            (magic, Header, SubHeader → FormatProfile)
  → BlockReader             (заголовки блоков 8/12 байт)
  → Inflater                (каждый блок отдельно, 8192 байт, конкатенация)
  → MetadataReader          (игроки, encoded settings, Reforged-чанки, слоты)
  → GameBlockReader         (TimeSlot, chat, leave; время из timeIncrement)
  → ActionDialect.decode    (CommandBlock → List<RawAction>)
  → RawToDomainMapper       (склейка 0x7A + 0x10 → Train/LearnSkill/BuyItem, расы, observer-фильтр)
  → Replay
```

### 5.2 Версии патчей

```kotlin
data class FormatProfile(
    val blockHeader: BlockHeaderLayout,     // U16 (build < 6089) | U32
    val dialect: ActionDialect,             // Legacy | Reforged | Post202
    val hasReforgedChunks: Boolean,         // определяется при чтении, не из версии
)

object FormatProfiles {
    fun detect(sub: SubHeader, hint: MetadataHints?): FormatProfile
}

interface ActionDialect {
    /** Читает один action. null — неизвестный ID: остаток CommandBlock пропускается. */
    fun read(id: Int, r: ByteReader): RawAction?
}
```

- Различия патчей собраны в `FormatProfile`. Остальной парсер от версии не зависит.
- `ActionDialect` — таблица `id → codec`. Post202 = Reforged + сдвиг ID ≥ 0x77 (W3G_FORMAT §7.3).
- Новый патч:
  1. положить реплей в `testdata/replays/<patch>/`;
  2. если golden-тест упал — добавить диалект или правило в `detect`;
  3. добавить справочник `gamedata/<patch>/`.
- Устойчивость: CommandBlock ограничен длиной. Неизвестный action пропускает остаток блока и пишет `ParseDiagnostic`, парсинг продолжается. Фатальны только битый header и битый zlib.
- Защита от недоверенных данных: каждый `read` проверяет границы, `blockCount` и размеры ограничены (≤ 64 МБ распакованного), циклы — по смещению с проверкой продвижения.

### 5.3 Тесты

- `jvmTest`: golden-снимки (JSON `Replay` + `AnalysisReport`) для каждого файла в `testdata`.
- Инвариант: сумма `timeIncrement` ≈ длительность из SubHeader (±50 мс).
- Инвариант: 100% CommandBlock разобраны до границы.
- `Inflater`: сверка с `java.util.zip.Inflater` на всех блоках и property-тест на случайных данных.
- `commonTest` прогоняется и на JS (`jsNodeTest`/`jsBrowserTest`) — ловит различия платформ (Int/Long, float).

## 6. Справочник игровых данных

### 6.1 Что нужно

Для каждого ID: имя, раса, тип, стоимость gold/lumber, food (потребляет/даёт), время, tier, флаги (рабочий, герой, ультимейт). Сейчас ~300 объектов melee: юниты, герои, здания, апгрейды по уровням, предметы магазинов, навыки героев.

### 6.2 Откуда брать (легально)

| Источник | Плюсы | Минусы |
|---|---|---|
| Файлы игры (`UnitBalance.slk`, `UnitData.slk`, `UpgradeData.slk`, `ItemData.slk`, `AbilityData.slk`, строки из `war3.w3mod` через CASC) | Точные числа под патч | Данные Blizzard. Не распространять файлы, только извлечённые факты. Нужна своя установка игры |
| Liquipedia Warcraft | Удобно сверять, CC BY-SA 3.0 | Ручной перенос. При копировании текста — атрибуция и share-alike |
| w3gjs `mappings.ts` (MIT) | Готовые имена ID и связка навык → герой | Нет стоимостей |

Рекомендация:

- **Решение пользователя 2026-10-27: числа берём с Liquipedia.** Скрипт `tools/gamedata/` (Python, MediaWiki API, кеш ответов) собирает `gamedata/3.0/*.json`. Fourcc ID Liquipedia отдаёт сама: поле `id` инфобокса, имя шаблона `UpgradeCard/Rxxx`. Атрибуция CC BY-SA 3.0 — в `gamedata/README.md` и в каждом JSON. Подробности и несопоставленные ID — `gamedata/README.md`, `gamedata/3.0/UNMATCHED.md`.
- **Имена** — из w3gjs `mappings.ts` с указанием MIT-лицензии в `NOTICE`.
- Картинки и иконки Blizzard в бандл не кладём. В UI — текст и цвет расы.

Карты W3Champions — свои сборки ладдерных карт. Карта может переопределить object data. Для melee-карт W3C принимаем стандартные данные; это записано в открытых вопросах.

### 6.3 Хранение

- Источник правды — JSON по патчам: `gamedata/<patch>/*.json` (сейчас `gamedata/3.0/`). Читается и правится руками, удобен в diff.
- Gradle-task `generateGameData` (класс `GenerateGameDataTask` в `build-logic/src/main/kotlin/wc3viewer/build/`) читает JSON через `groovy.json.JsonSlurper` из Gradle API. kotlinx-serialization в `build-logic` не нужен.
- Выход — `data/gamedata/build/generated/gamedata/commonMain/kotlin/…/GameData_3_0.kt`. Каталог подключён в `commonMain` через `kotlin.srcDir(task)`, в git не попадает.
- В сгенерированном объекте:
  - `objects()` — ~790 `GameObject`, разбиты на функции по 120 штук (лимит размера метода JVM);
  - константы атрибуции `SOURCE`, `LICENSE` (CC BY-SA 3.0), `ATTRIBUTION` из `meta.json`.
- Имена навыков героев — из `heroes.json`. Двенадцать ID, которых там нет (`ANc1`–`ANg3`, `AHfa`, `AUfa`, `ANic`), задача добирает из `gamedata/sources/w3gjs-mappings.ts` (MIT). Источник — константа `ABILITY_NAMES_SUPPLEMENT` и комментарий у строки.
- Некорректные ID (например, `ngm`) пропускаются с предупреждением в логе сборки.
- Подключение: `ProductionGameData.repository` (`:data:gamedata`, ленивый) → `Main.kt` в `:app:web` передаёт его в `AnalyzeReplay` и `ReplayViewModel` (DI вручную, ADR 13). `:domain` и `:analytics` видят только интерфейс `GameDataRepository`/`GameData`. `ReportMapper.sections(report, gameData)` берёт из справочника имена героев, навыков и таймингов.
- Почему codegen, а не загрузка JSON в рантайме:
  - нет IO и асинхронной загрузки ресурсов в common-коде;
  - одинаково работает в `jvmTest` и JS;
  - ошибки схемы ловятся на сборке.
- Покрытие на 21 реплее `testdata/replays/3.00`: имя есть у 99,76% ID в приказах (3764 из 3773). Без имени: `ritd` (Ritual Dagger, нет ID на Liquipedia) и `hmtt` (Siege Engine, на Liquipedia `hrtt`). Тест — `GameDataOnReplaysTest`.
- `GameDataRepository.forPatch` выбирает ближайший справочник ≤ патча реплея, иначе самый новый.

## 7. Presentation и UI

### 7.1 Состояние

```kotlin
sealed interface ReplayUiState {
    data object Empty : ReplayUiState
    data class Loading(val fileName: String) : ReplayUiState
    data class Loaded(val overview: MatchOverview, val sections: List<SectionUi>) : ReplayUiState
    data class Failed(val message: String, val diagnostics: List<String>) : ReplayUiState
}

class ReplayViewModel(
    private val analyze: AnalyzeReplay,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<ReplayUiState>
    fun onFileSelected(name: String, bytes: ByteArray)
    fun onPlayerFilter(player: PlayerId?)
    fun onTimeRange(range: ClosedRange<GameTime>)
}
```

- Обычный класс на `StateFlow`, без `androidx.lifecycle`: на web он ничего не даёт.
- Маппинг `AnalysisReport → SectionUi` — в `:presentation` (чистый Kotlin, тестируется на JVM).
- Графики получают готовые серии `ChartSeries(points: FloatArray, …)`.

### 7.2 Файлы

```kotlin
interface FileSource { suspend fun pick(): PickedFile? }     // jsMain: <input type=file> + drag-and-drop
class PickedFile(val name: String, val bytes: ByteArray)
```

`FileReader`/`Blob.arrayBuffer()` через JS-интероп в `jsMain`. Альтернатива — FileKit (KMP, есть web).

### 7.3 Графики

Нужны:

- линии (APM, потрачено ресурсов, армия) для двух игроков;
- stacked area (структура трат);
- таймлайн билд-ордера (дорожки по игрокам, иконка/подпись по времени);
- столбцы (хоткеи).

| Вариант | За | Против |
|---|---|---|
| **Свой Canvas на Compose `DrawScope`** | Единый рендер с UI, работает на js, полный контроль над таймлайном, ноль интеропа | Оси, тики и тултипы писать самим (~600–900 строк) |
| Koala Plot 0.12.1 | KMP, js+wasm, MIT, линии/столбцы/area готовы | Таймлайна нет. Молодая библиотека (0.x) |
| ECharts (6.1.0) через external | Богатые графики, zoom из коробки | Compose web рисует в canvas: ECharts надо встраивать HTML-элементом поверх, плюс проблемы со скроллом и z-order; +~1 МБ JS |

Рекомендация:

- свой небольшой модуль `ui/charts` на Compose Canvas: `LineChart`, `StackedAreaChart`, `TimelineChart`, `BarChart`, общий `TimeAxis` и hover-курсор, синхронный между графиками;
- Koala Plot — если сроки поджимают и таймлайн можно сделать отдельно;
- ECharts — нет.

### 7.4 UI-слой (`app/web/src/jsMain/kotlin/wc3viewer/app/ui/`)

| Пакет | Что внутри |
|---|---|
| `theme/` | Токены: `Palette`, `PlayerColors`, `Space`, `Shapes`, `Type`, цвета рас и видов объектов; `Fonts` (JetBrains Mono для fourcc). Экраны не задают цвета и размеры напрямую |
| `components/` | `Basics` (Txt, Panel, кнопки, SegmentedControl, FilterChip), `DataDisplay` (бейджи, `EstimateBadge`, `ObjectIcon`, `ObjectLabel` — имя + fourcc мелко, `StatTile`, `TabBar`, `DataTable`), `States` (пустое состояние, ошибка, загрузка, drop-зона) |
| `charts/` | Compose Canvas: `ChartKit` (общий `TimeCursor`, оси, тултипы), `LineChart` (`TimeSeriesChart`, пунктир = оценка, `RefLine` для порогов 50/80), `TimelineChart`, `HotkeyHeatmap` |
| `match/` | `MatchScreen` (шапка, вкладки) и вкладки: Overview, Timeline, Economy, Build order, APM & hotkeys, Heroes |
| `screens/` | `LandingScreen` |
| `App.kt` | Переключение состояний `ReplayUiState` |

Имена объектов приходят готовыми в UI-моделях (`BuildOrderRow.name`, `HeroUi.hero`, `SkillPickUi.name`, `HeroOrderUi.name`, `TimingUi.name`). Если ID нет в справочнике, показывается fourcc. Экономика (`EconomyEstimateMetric` → `EconomyUi`) везде помечена ESTIMATE.

Dev-хелперы: `?replay=<url того же origin>&tab=<overview|timeline|economy|build|apm|heroes>`.

## 8. Производительность и Web Worker

Замеры на наших файлах:

- реплей 19 минут: 368 КБ сжатого, 852 КБ распакованного;
- ~40 тыс. TimeSlot, ~9,6 тыс. CommandBlock, ~33 тыс. actions;
- полный разбор на CPython — 0,08 с вместе со стартом интерпретатора.

Kotlin/JS справится за десятки миллисекунд. Замер на JVM: 10 реплеев разбираются за ~0,3 с вместе с прогревом.

Решение:

- **MVP — без Worker.** `AnalyzeReplay` — `suspend`. Парсер отдаёт управление (`yield()`) каждые ~2000 TimeSlot, чтобы не морозить UI. На web `Dispatchers.Default` — тот же поток, так что `yield` обязателен.
- **Worker — когда понадобится**: пакетный анализ десятков реплеев или если замер `performance.now()` на реальном устройстве покажет > 100 мс. Порт `ReplayParser` уже `suspend`, поэтому замена прозрачна:
  - модуль `:app:worker` (отдельный бандл, `js`);
  - `postMessage(bytes, [buffer])` с передачей `ArrayBuffer`;
  - ответ — `AnalysisReport`, сериализованный kotlinx-serialization (ProtoBuf/JSON).
- Память: исходные байты + 1 МБ распакованных + список событий. Терпимо. События хранить компактно: `Train` и т.п. без `List` там, где это не нужно.

## 9. ADR

| # | Решение | Альтернативы | Почему |
|---|---|---|---|
| 1 | Только браузер, без бэкенда | Сервер-парсер | Решение пользователя. Приватность, нулевой хостинг (статика) |
| 2 | Логика в `commonMain`, тесты на JVM | Тесты только в браузере | Быстрый цикл, golden-тесты на фикстурах, `java.util.zip` как эталон |
| 3 | UI: Compose MP web только на `js(IR)` (canvas, Skiko). **Решение пользователя 2026-10-27** | `wasmJs` + `js` fallback (compatibility mode); Compose HTML (DOM) | Решение пользователя. CMP 1.12.2 поддерживает `js`-таргет без Wasm-бандла. Один таргет — одна сборка и один набор платформенного кода. Compose HTML проще для ECharts, но это другой UI-стек |
| 4 | Свой inflate на Kotlin в `:core:binary` | `DecompressionStream`; pako 3.0.3 через npm | Потоки в .w3g не завершены (нет final-блока и adler32). `DecompressionStream` по спецификации отдаёт ошибку на незавершённом потоке (в браузере не проверяли); обходить надо чтением до ошибки, это хрупко. pako тянет npm и JS-интероп. Свой inflate (~300 строк) одинаков на JVM и JS и тестируется против `java.util.zip` |
| 5 | Свой парсер на Kotlin по мотивам w3gjs | Запускать w3gjs через JS-интероп | w3gjs завязан на Node (`zlib`, `Buffer`, `EventEmitter`). По коду, без чанка 0x38 он читает 0x7A как 20 байт вместо 16, а такого чанка в W3C-реплеях 3.0 нет (§7.3 формата). w3gjs на наших файлах не запускали |
| 6 | `FormatProfile` + `ActionDialect` по версии/build | `if` по версии внутри парсера | Различия патчей в одном месте; новый патч = новый профиль + фикстура |
| 7 | Неизвестный action пропускает остаток CommandBlock + диагностика | Падать | Длина блока известна; один неизвестный ID не должен ломать матч |
| 8 | Метрики — streaming-коллекторы, явный реестр | Каждая метрика сама обходит события; аннотации и рефлексия | Один проход; в Kotlin/JS рефлексии нет; новая метрика = класс + строка в реестре |
| 9 | Экономика и армия — «оценка по командам» | Симуляция игры | В реплее только ввод; смертей и добычи нет. Честная подпись в UI |
| 10 | Справочник: JSON по патчам → Kotlin codegen | JSON в рантайме; руками в Kotlin | Diff-friendly источник; нет асинхронной загрузки; ошибки на сборке |
| 14 | Числа справочника — с Liquipedia (MediaWiki API). **Решение пользователя 2026-10-27** | SLK из установки игры; w3gjs | Liquipedia обновляют под каждый патч, есть fourcc ID. Минусы: HP героев нет в wikitext, у улучшенных зданий накопленная стоимость (считаем `upgradeCost`), цепочки улучшений заданы в `tools/gamedata/build.py` |
| 11 | Графики на Compose Canvas | ECharts; Koala Plot | Единый canvas-рендер; таймлайн всё равно свой; без интеропа |
| 12 | Без Web Worker в MVP, порт готов к выносу | Worker сразу | Разбор — десятки мс; Worker добавляет второй бандл и сериализацию |
| 13 | DI вручную в `:app:web` | Koin | Графа мало (~10 объектов) |

## 10. Открытые вопросы

1. **Победитель в W3C-реплеях.** Правило (W3G_FORMAT §6.2): игрок с leave `result = 0x09` — победитель, иначе Unknown. Правило применяется после фильтра наблюдателей: у FLO тоже бывает 0x09. В реплеях `6ae0…` (с чанками 0x38) работает. В `Replay_*` у всех 0x07 — победитель Unknown. Открыто: нужна ли эвристика w3gjs или ручной выбор для таких файлов.
2. **Граница сдвига action ID** (0x77+) по версиям 2.0.x. Нужны реплеи 2.0.0–2.0.4 и не-W3C (обычный Battle.net, custom).
3. **Битые файлы** в `Replay_2026_10_25_2224/`: 4 файла по 0 байт и 1 файл (343 009 байт) из одних нулей. Это ошибки сохранения или копирования? Приложению нужно понятное сообщение для таких файлов.
4. **Справочник для 3.0** — собран с Liquipedia (ADR 14). Баланс рас на 3.0.x совпадает с 2.0.4, `Npal` есть. Открыто: HP героев, `soldFrom` у большинства предметов, 44 записи `possiblyStale` у крипов.
5. **Определение EPM** — правило пользователя, порог 500 мс (§4.4). Повторные приказы на производство (Train/Build) — не спам, решение пользователя 2026-10-27.
6. **Object data карт W3C** — переопределяют ли карты стоимости? Проверить на `.w3x` из реплеев, если доступны.
7. **Наблюдатели и 2v2+** вне MVP, но `Player.isObserver` и `team` закладываем сразу.
8. **Номера хоткеев**: соответствие `group` клавишам 1–0 проверить на реплее с известными нажатиями.
