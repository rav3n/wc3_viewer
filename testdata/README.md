# testdata

Фикстуры и эталоны для тестов парсера и метрик. Стратегия — в [docs/testing/TEST_STRATEGY.md](../docs/testing/TEST_STRATEGY.md).

Реплеи — недоверенные данные. Тесты и скрипты их только читают.

## Что где лежит

```
testdata/
├── replays/
│   ├── 3.00/        21 рабочий реплей 1v1, патч 3.00 (build 7000/7003), W3Champions
│   ├── team/        2v2 и 4v4 (W3Champions) — для негативного теста «Only 1v1», эталонов нет
│   └── broken/      5 битых: 4 файла по 0 байт, Replay_2026_09_14_2320.w3g — 343 009 нулей
├── expected/        эталон на каждый рабочий реплей: <имя файла>.json
└── w3c-api/         ответы W3Champions API: <w3cMatchId>.json
```

- `replays/` — копии из `Replay_2026_10_25_2224/`. Файлы `6ae0….w3g` и `6adf….w3g` названы ID матча W3Champions, `Replay_*` — так их сохранил клиент игры.
- `expected/` — результат независимого Python-генератора, не Kotlin-кода. Поле `sha256` привязывает эталон к конкретному файлу.
- `w3c-api/` — `GET https://website-backend.w3champions.com/api/matches/{id}`, без изменений. Внешний эталон для игроков, рас, победителя, карты, героев и уровней. Содержит публичные данные игроков (BattleTag, MMR, страна, пинг).

Командные реплеи `team/`:

| Файл | Режим | Карта | Игроков | Заметки |
|---|---|---|---|---|
| `6ad50154eae5e82cc9a4f183.w3g` | 2v2 | Twisted Meadows v1.9 | 4 + FLO | |
| `6ae0be4201d5ffcb2ad75ee3.w3g` | 4v4 | Arathor v1.3 | 8 + FLO | 2 Random, action `0x60` (map trigger chat) |

Оба разбираются `gen_expected.py` без ошибок. Ожидание для приложения — отказ «Only 1v1 replays are supported».

Связь реплея и матча W3C — поле `w3cMatchId` в эталоне. Для `Replay_*` ID найдены через `GET /api/matches/search?playerId=RavenGG%2321468&gateway=20&season=25` по `gameName`:

| Реплей | w3cMatchId |
|---|---|
| `Replay_2026_09_16_0103.w3g` | `6aa9c0a1e066667e29f4448a` |
| `Replay_2026_10_23_0033.w3g` | `6ada8143a49325c22434fc18` |
| `Replay_2026_10_23_0240.w3g` | `6ada9f0aa49325c224350436` |
| `6ae0….w3g`, `6adf….w3g` | совпадает с именем файла |

## Поля эталона

| Поле | Смысл |
|---|---|
| `source` | Генератор, источник классификации ID, правило APM. `externallyVerified` — какие поля совпали с W3C API |
| `file`, `fileSize`, `sha256` | Идентификация фикстуры |
| `version`, `build`, `patch` | Из SubHeader |
| `durationMs` | Длительность из SubHeader. В `6ae0…` и `6adf…` включает 45–50 с после выхода игроков (FLO ещё в игре). Паузу не включает (проверено на `Replay_2026_09_16_0103`) |
| `gameTimeSumMs` | Сумма `timeIncrement` всех TimeSlot. Включает паузы |
| `matchEndMs` | Время последнего leave игрока (не наблюдателя) |
| `gameName`, `map` | `map.path` как в файле, `map.name` — без префикса `NNN_w3c_…_` и расширения |
| `reforgedChunks` | Число чанков 0x38/0x39 (9 в `6ae0…` и `6adf…`, 0 в `Replay_*`) |
| `players[]` | Игроки без наблюдателей: `pid`, `name` (BattleTag), `team`, `color`, `raceFlag`, `race`, `raceResolved` (для Random — по первому приказу) |
| `players[].commandBlocks` | Число CommandBlock игрока |
| `players[].actionCounts` | Число action-ов по ID (нумерация файла 3.0) |
| `players[].apmW3gjs` | APM по правилу w3gjs: `countedActions`, `lastCommandBlockMs`, `apm` |
| `players[].heroes` | `id`, `firstOrderMs` (приказ найма), `firstSkillMs`, `skillOrders` (сырые приказы), `skillsLearned` (без повторов того же навыка < 500 мс). Порядок — по первому навыку |
| `players[].buildOrderFirst15` | Первые 15 приказов: `timeMs`, `id` (fourcc), `type` (`train`/`build`/`research`/`hero`/`skill`/`item`), `action` (0x10/0x11/0x12) |
| `players[].buildOrderTotal` | Всего таких приказов |
| `observers` | Наблюдатели (FLO, pid 24) |
| `leaves` | Leave-блоки: время, pid, reason, result |
| `winnerPid` | Игрок с leave `result 0x09`, иначе `null` (Unknown) |
| `apiWinnerName` | Победитель по W3C API — для сравнения, не для теста парсера |
| `chatMessages` | Число блоков чата |
| `parse` | Смещение игровых данных, число CommandBlock, разобранных до границы / сломанных, неизвестные action-ы |
| `w3cApiCheck` | Итог сверки с API: что совпало, заметки, длительность и карта из API |

Тип `build` включает улучшение главного здания и башен (`hkee`, `ostr`, `uzg2` и т.п.) — так в `mappings.ts` w3gjs.

## Как обновлять эталоны

Скрипты лежат в `testdata/tools/`:

- `gen_expected.py` — генератор `expected/*.json`;
- `compare_api.py` — сверка с `w3c-api/` и запись `w3cApiCheck`, `w3cMatchId`, `apiWinnerName`;
- справочник ID — `gamedata/sources/w3gjs-mappings.ts` (`mappings.ts` из [w3gjs](https://github.com/PBug90/w3gjs) 4.3.0, лицензия MIT); он общий с прод-сборкой.

Порядок:

```sh
# 1. эталоны из реплеев (только чтение реплеев)
python3 -I testdata/tools/gen_expected.py gamedata/sources/w3gjs-mappings.ts testdata/expected testdata/replays/3.00/*.w3g
# 2. сверка с API и дозапись полей сверки
python3 -I testdata/tools/compare_api.py testdata --write
# 3. посмотреть diff, каждое изменение объяснить в коммите
git diff testdata/expected
```

Новый реплей:

1. Положить в `testdata/replays/<patch>/`. Битый — в `replays/broken/`, командный — в `replays/team/`; эталон не нужен.
2. Если реплей с W3Champions: скачать `GET /api/matches/{id}` в `w3c-api/<id>.json`. ID — имя файла с сайта W3C или поиск по игроку и `gameName`.
3. Шаги 1–3 выше.
4. Дополнить матрицу покрытия в `TEST_STRATEGY.md` §11.

Когда эталон расходится с Kotlin-парсером:

- сначала проверить по W3G_FORMAT и hex-дампу, кто прав;
- если ошибается генератор — исправить его и перегенерировать все эталоны;
- эталон руками не править.

API W3Champions — только GET, без ключей. Сохранённые ответы — снимок на 2026-10-27; перекачивать без причины не нужно.
