# gamedata

Справочник баланса Warcraft III для оценки экономики и армии по командам из реплея.

## Источник и лицензия

Данные извлечены из [Liquipedia Warcraft Wiki](https://liquipedia.net/warcraft/Main_Page) через MediaWiki API (`action=query`, `prop=revisions`, wikitext).

Контент Liquipedia распространяется по лицензии [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Поэтому JSON из `gamedata/` тоже под CC BY-SA 3.0.

Атрибуция:

- Источник — Liquipedia Warcraft Wiki, авторы — участники вики (история правок каждой страницы).
- Список страниц с `revid`, ссылкой и постоянной ссылкой на ревизию (`index.php?oldid=…`) — в `3.0/meta.json`, поле `pages`.
- У каждой сущности есть `source: {page, revid, url}`.
- Изменения: данные из инфобоксов и карточек перенесены в JSON, часть полей вычислена (см. «Вычисляемые поля»).

Часть fourcc ID сопоставлена по имени с `gamedata/sources/w3gjs-mappings.ts` (w3gjs, MIT).

## Патч

`patch: "3.0.1"`. На Liquipedia в патчах 3.0.0 и 3.0.1 нет изменений баланса рас. Есть только новый герой таверны Forsaken Paladin (3.0.0) и его правки (3.0.1). Числа юнитов, зданий, апгрейдов и предметов — это баланс 2.0.4, он действует и в 3.0.x.

Проверка актуальности:

- `meta.json` → `patchChecks`: 23 значения из патчноутов 2.0.2–2.0.4 сверены с инфобоксами. Все совпали.
- `possiblyStale` у сущности — ревизия страницы старше релиза патча, в патчноутах которого она упомянута. Это эвристика. Для расовых сущностей все флаги проверены вручную: изменения касаются способностей или апгрейдов с других страниц, хранимые поля (стоимость, время, food, HP, броня) не затронуты. Флаги у крипов не проверялись.

## Файлы

```
gamedata/
├── README.md
└── 3.0/
    ├── meta.json        патч, дата сбора, источник, revid всех страниц, сверки
    ├── units.json       381 (74 расовых, остальное — крипы, вызываемые, нейтральные)
    ├── buildings.json   67
    ├── heroes.json      25 (16 расовых + 9 таверны, включая Npal)
    ├── upgrades.json    80
    ├── items.json       142
    └── UNMATCHED.md     что не сопоставилось с fourcc, покрытие эталонных билд-ордеров
```

Файлы разделены по типу:

- схема полей у типов разная (уровни у апгрейдов, способности у героев, магазины у предметов);
- совпадает с раскладкой из ARCHITECTURE.md (`units`, `buildings`, `upgrades`, `heroes`, `items`);
- в diff видно, какой тип изменился.

`meta.json` вынесен отдельно, чтобы не дублировать список страниц.

## Как пересобрать

```
python3 -I tools/gamedata/fetch.py      # скачать wikitext в tools/gamedata/cache/ (кеш не в git)
python3 -I tools/gamedata/build.py      # cache → gamedata/3.0/*.json и UNMATCHED.md
```

- Только stdlib Python 3.
- `fetch.py` берёт ответы из кеша, если они уже есть. Чтобы скачать свежие данные, удалите `tools/gamedata/cache/`.
- `fetch.py --offline` падает, если чего-то нет в кеше. Сеть при этом не трогается.
- Правила Liquipedia API соблюдены: свой User-Agent, gzip, не чаще 1 запроса в 2,5 с, `action=parse` не используется. Полный сбор — около 90 запросов.
- `build.py` детерминирован: при том же кеше выход тот же.

## Схема

Общие поля (у всех файлов `{schemaVersion, patch, source, license, count, entities: [...]}`):

| Поле | Тип | Что это |
|---|---|---|
| `id` | string \| null | fourcc из реплея. `null`, если ID не найден |
| `idSource` | string | `liquipedia` (поле `id` инфобокса), `liquipedia:template` (имя шаблона `UpgradeCard/Rxxx`), `w3gjs:name`, `w3gjs:abilityToHero` |
| `name` | string | имя на Liquipedia |
| `race` | string \| null | `human`, `orc`, `undead`, `nightelf`, `neutral` |
| `type` | string | `unit`, `building`, `hero`, `upgrade`, `item` |
| `goldCost`, `lumberCost` | number \| null | стоимость |
| `foodCost`, `foodProvided` | number \| null | food: потребляет / даёт |
| `buildTimeSec` | number \| null | время постройки или тренировки |
| `hp`, `armor` | number \| null | HP и броня (без апгрейдов) |
| `requirements` | `[{name, id}]` | требования из инфобокса |
| `producedBy` | `[{name, id}]` | кто строит или тренирует |
| `tier` | 1–3 | вычисляется, см. ниже |
| `source` | `{page, revid, url}` | страница-источник |
| `nullFields` | `{field: reason}` | почему поле `null` |
| `possiblyStale` | `{patches, reason}` | см. «Патч» |

Дополнительно по типам:

- **units**: `armorType`, `level`, `classes`, `isWorker` (класс `Peon`).
- **buildings**: `trains`, `researches`, `unlocks`, `upgradesTo`, `upgradesFrom`, `upgradeCost`.
- **heroes**: `firstHeroCost`, `primaryAttribute`, `attributes` (база и прирост), `abilities: [{id, name, inW3gjs, source}]`, `costSource`.
- **upgrades**: `researchedAt`, `levels: [{level, name, goldCost, lumberCost, researchTimeSec, requirements, tier}]`, `costFormula` (только у карточек `UpgradeCard`), `abilityId` (если апгрейд описан карточкой способности).
- **items**: `level`, `itemClass` (код класса Liquipedia), `charges`, `stock`, `stockStartSec`, `stockReplenishSec`, `soldFrom: [{name, id}]`, `tier`, `sharedNameIds` (несколько fourcc с одним именем, запись продублирована на каждый ID).

## Вычисляемые поля и оговорки

- **Улучшаемые здания** (Keep, Castle, башни и т.п.). Liquipedia даёт накопленную стоимость: Keep 705/415 = Town Hall 385/205 + апгрейд. Для трат по приказу апгрейда используйте `upgradeCost` (разность с предыдущим зданием, `derived: true`). Цепочки апгрейдов на Liquipedia не связаны ссылками, они заданы константой `MORPHS` в `build.py`.
- **Стоимость героев** — со страницы [Hero](https://liquipedia.net/warcraft/Hero):
  - алтарь — 400/100, 5 food, 55 с; первый герой бесплатный (`firstHeroCost`);
  - таверна — 425/135, покупка мгновенная, поэтому `buildTimeSec: null`.
- **HP героя** — `null`: Liquipedia считает его в Lua-модуле из атрибутов, в wikitext числа нет. `armor` у героя — базовая броня без бонуса ловкости.
- **Уровни апгрейдов** из `UpgradeCard`: уровень N = база + (N − 1) × прирост. Исходные пары — в `costFormula`.
- **`tier`**: 2 или 3, если в требованиях (транзитивно) есть Keep/Stronghold/Halls of the Dead/Tree of Ages или Castle/Fortress/Black Citadel/Tree of Eternity. По производителям берётся минимум.
- **Предметы**: `soldFrom` заполнен только у 33 из 142 — у остальных поле на Liquipedia пустое (магазин строится Lua-модулем). `lumberCost: null`, если поле пустое.
- Апгрейды, описанные карточкой способности (Defend, Burrow и т.п.), сопоставлены с fourcc исследования по имени и расе. Обёртка `SpellCard/UnitInventory` (Backpack) раскрыта: её параметры в `costSource`.
