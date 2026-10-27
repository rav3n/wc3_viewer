# Формат .w3g (реплей Warcraft III)

Документ для реализации парсера. Источники:

- [w3gjs](https://github.com/PBug90/w3gjs) (TypeScript, MIT, v4.3.0 от 2026-09-22, поддерживает патч 3.0) — основной ориентир;
- `w3g_format.txt` (Blue, Nagger) и `w3g_actions.txt` — классическое описание формата до Reforged;
- проверка на реальных реплеях из `Replay_2026_10_25_2224/` (скрипты в scratchpad, только чтение).

Пометки:

- **✔** — проверено на наших файлах (патч 3.0, version 10200, build 7000/7003, W3Champions/FLO);
- **?** — взято из источников, на наших файлах не встречалось или смысл не подтверждён. Проверить на реальных файлах.

Все числа little-endian. `cstr` — строка UTF-8 с завершающим `0x00`. `fourcc` — 4-байтовый ID объекта (см. «Идентификаторы»).

## 1. Общая структура

```
[Header 48 байт][SubHeader 20 байт]  = 68 байт (headerSize = 0x44)  ✔
[Block 0][Block 1]...[Block N-1]     — каждый блок: заголовок + zlib-поток
```

После распаковки блоки склеиваются в один буфер: метаданные игры, затем поток игровых блоков (TimeSlot и др.).

## 2. Header

| Смещение | Тип | Поле | Наши файлы |
|---|---|---|---|
| 0x00 | char[28] | `"Warcraft III recorded game\x1A\0"` | ✔ |
| 0x1C | u32 | headerSize | 68 ✔ |
| 0x20 | u32 | compressedSize (размер файла целиком) | = размер файла ✔ |
| 0x24 | u32 | headerVersion (0 — до 1.07, 1 — 1.07+) | 1 ✔ |
| 0x28 | u32 | decompressedSize (реальные данные без паддинга) | ✔ |
| 0x2C | u32 | blockCount | совпадает с фактическим ✔ |

w3gjs ищет magic через `indexOf`, а не с нулевого смещения: так переживаются обёртки с мусором в начале. Нам это тоже стоит делать.

## 3. SubHeader (headerVersion = 1)

| Смещение | Тип | Поле | Наши файлы |
|---|---|---|---|
| 0x30 | char[4] | product, перевёрнут: `"PX3W"` = W3XP, `"3RAW"` = WAR3 | `PX3W` ✔ |
| 0x34 | u32 | version | 10200 ✔ |
| 0x38 | u16 | build | 7000, 7003 ✔ |
| 0x3A | u16 | flags: 0x0000 — одиночная, 0x8000 — сетевая | 0x8000 ✔ |
| 0x3C | u32 | длительность, мс | ✔ (сумма timeIncrement расходится на ≤ 6 мс) |
| 0x40 | u32 | CRC32 заголовка (по w3g_format — считается с обнулённым полем) | не валидировали ? |

Перевод `version` в патч (по w3gjs `convert.ts`):

- `10030` → `1.30.2+`;
- `10031..10099` → `1.xx` (две последние цифры);
- `≥ 10100` → `${version/100 - 99}.${две последние цифры}`. Значит, `10100` → 2.00, `10200` → **3.00** (w3gjs 4.2.0: «support Warcraft III patch 3.0 replays», build 7000).

`headerVersion = 0` (до 1.07) не поддерживаем.

## 4. Сжатые блоки

Заголовок блока зависит от build:

| build | Заголовок | Размер |
|---|---|---|
| < 6089 (classic) | u16 compSize, u16 decompSize, u32 checksum | 8 байт |
| ≥ 6089 (Reforged) | u32 compSize, u32 decompSize, u32 checksum | 12 байт ✔ |

w3gjs читает Reforged-вариант как `u16 + skip 2`. В LE при размерах < 65536 это то же самое. Мы читаем u32.

Наблюдения ✔:

- каждый блок — **отдельный** zlib-поток с заголовком `78 01`;
- поток **не завершён**: нет финального DEFLATE-блока и adler32 (0 из 254 потоков завершены). Блоки сброшены через sync flush, поэтому w3gjs распаковывает с `Z_SYNC_FLUSH`. Распаковщик должен отдавать данные без ошибки при обрыве потока;
- `decompSize` всегда 8192. Последний блок добит нулями, поэтому сумма блоков (`blockCount × 8192`) ≥ `decompressedSize` из Header;
- w3gjs отбрасывает блоки с `decompSize ≠ 8192`. У нас таких нет;
- checksum: по w3g_format — CRC32 заголовка блока и данных, свёрнутые в 16 бит. Простые CRC32 (сжатых и распакованных данных) не совпали. Не валидируем ?.

## 5. Метаданные игры (начало распакованного буфера)

```
u32         unknown (0x00000110 ✔)
PlayerRecord host                        ✔
cstr        gameName                     ✔ "w3c-20-<id>-<id>"
cstr        privateString (пусто)        ✔
cstr        encodedSettings              ✔ (см. 5.2)
u32         playerCount                  ✔ 24 — число слотов, не игроков
u32         gameType                     ✔
u32         localeHash                   ✔ 0
{ 0x16 PlayerRecord u32 unknown }*       ✔ дополнительные игроки
{ 0x38|0x39 Reforged-чанк }*             в наших файлах НЕТ (см. 5.3)
0x19 GameStartRecord                     ✔ (см. 5.4)
```

### 5.1 PlayerRecord

```
u8   recordId    0x00 — хост, 0x16 — остальные
u8   playerId
cstr name        BattleTag, "RavenGG#21468" ✔
u8   addLen      1 — custom, 8 — ladder (classic); 2 в наших файлах ✔
u8[addLen]
```

### 5.2 Encoded settings

Строка закодирована. Алгоритм (w3gjs `decodeGameMetaString`):

```
for i in 0 until enc.size:
  if i % 8 == 0: mask = enc[i]
  else: out += if (mask and (1 shl (i % 8))) == 0 then enc[i] - 1 else enc[i]
```

Раскодированный буфер:

| Смещение | Поле |
|---|---|
| 0 | speed (2 = fast ✔) |
| 1 | биты: 0 hideTerrain, 1 mapExplored, 2 alwaysVisible, 3 default, 4–5 observerMode, 6 teamsTogether |
| 2 | биты 1–2: fixedTeams |
| 3 | биты: 0 sharedUnitControl, 1 randomHero, 2 randomRaces, 6 referees |
| 4..8 | unknown ? |
| 9..12 | map checksum |
| 13 | cstr mapPath ✔ `Maps/W3Champions\5693_w3c_..._FadingAutumn_v1.3.w3m` |
| … | cstr creator ✔ `FLO` |
| … | 1 байт skip, затем SHA1 карты (20 байт) |

### 5.3 Reforged-чанки 0x38 / 0x39 (protobuf)

```
u8   tag        0x39 — Reforged; 0x38 — «post-2.02», w3gjs по нему включает новую нумерацию actions
u8   subtype    3 — игрок, 4 — скины
u32  length
u8[length]      protobuf
```

- subtype 3 (`ReforgedPlayerData`): 1 playerId u32, 2 battleTag string, 3 clan string, 4 portrait string, 5 team u32, 6 unknown string;
- subtype 4 (`ReforgedSkinData`): 1 playerId, 2 repeated SkinData{1 unitId u32, 2 skinId u32, 3 skinName string}.

**Наличие чанков в реплеях W3Champions (хост FLO) разное ✔:**

- 3 файла `Replay_2026_*` (build 7000/7003) — чанков нет;
- 7 файлов `6ae0….w3g` (скачаны с W3C, build 7003) — по 9 чанков 0x38 (subtype 3, 4, 5).

Нумерация action-ов в обеих группах одинаковая (7.3). Значит, формат action-ов нельзя определять по наличию 0x38, как делает w3gjs. Надо смотреть на version/build (раздел 9).

Для protobuf хватит ручного декодера varint/length-delimited на ~60 строк. Библиотека не нужна.

### 5.4 GameStartRecord (0x19)

```
u8   0x19
u16  bytesRemaining   = 1 + slotCount*9 + 6 ✔ (223 при 24 слотах)
u8   slotCount        24 ✔
Slot[slotCount] по 9 байт:
  u8 playerId, u8 downloadPct, u8 slotStatus (0 пусто, 1 закрыт, 2 занят),
  u8 computer, u8 team, u8 color, u8 raceFlag, u8 aiStrength, u8 handicap
u32  randomSeed
u8   selectMode
u8   startSpotCount   2 ✔
```

`raceFlag`: 0x01 Human, 0x02 Orc, 0x04 Night Elf, 0x08 Undead, 0x20 Random. Бит 0x40 — «раса выбираемая». Например, 0x41 — тоже Human.

Наблюдение ✔: в W3C-реплеях занято 3 слота — 2 игрока (team 0/1) и бот FLO (playerId 24, team 24, race 0x20). FLO — наблюдатель-хост. Его надо отфильтровать: `team == 24` или слот «observer». Порог проверить для 2.x/3.x, где лимит игроков 24.

## 6. Игровые блоки (после метаданных)

Цикл до конца буфера, `u8 blockId`:

| ID | Размер | Смысл | Наши файлы |
|---|---|---|---|
| 0x00 | — | паддинг, конец данных | ✔ |
| 0x17 | 13 | LeaveGame: u32 reason, u8 playerId, u32 result, u32 unknown | ✔ |
| 0x1A, 0x1B, 0x1C | 4 | первые блоки игры (u32 = 1) | ✔ |
| 0x1E | var | TimeSlot (старый ID) | — |
| 0x1F | var | TimeSlot | ✔ ~25–40 тыс. на матч |
| 0x20 | var | Chat: u8 playerId, u16 len, u8 flags, [u32 mode, если flags = 0x20], cstr text | ✔ |
| 0x22 | 1+n | u8 n + n байт (контрольная сумма после каждого TimeSlot, n = 4) | ✔ |
| 0x23 | 10 | unknown | ? |
| 0x2F | 8 | forced game end countdown | ? |

### 6.1 TimeSlot (0x1F / 0x1E)

```
u16 byteCount        (включает следующее поле)
u16 timeIncrement    мс, игровое время += timeIncrement
CommandBlock* до offset + byteCount - 2:
  u8  playerId
  u16 actionBlockLength
  u8[actionBlockLength]  — последовательность actions
```

Длина CommandBlock известна заранее. Поэтому неизвестный action ломает только остаток своего блока, следующий блок парсится нормально. На этом строим устойчивость парсера.

### 6.2 LeaveGame

| reason | Смысл |
|---|---|
| 0x01 | remote (соединение закрыто) |
| 0x0C | local / game over |
| 0x0E | ? |

`result` (по w3g_format): 0x07 — вышел, 0x08 — проиграл, 0x09 — победил, 0x0A — ничья, 0x0B — observer вышел ?

Наблюдения ✔:

- Файлы `Replay_2026_*` (без чанков 0x38): в двух из трёх все leave-блоки пришли в последний тик с одинаковым результатом 0x07. Победителя по ним не определить.
- Файлы `6ae0….w3g` (с чанками 0x38): у двух игроков по одному leave-блоку, `reason == result`. Проигравший — 0x07 (или 0x01 при дисконнекте), победитель — 0x09. В командных играх (2v2, 4v4) 0x09 получает каждый игрок команды-победителя. Сверено с API W3Champions (`/api/matches/{id}`, поле `won`): 18 из 18 матчей, из них 16 1v1, один 2v2 и один 4v4.

Правило для парсера: есть игрок с `result = 0x09` → он победитель. Иначе — «не определён» (или эвристика w3gjs: кто вышел первым, проиграл; при reason 0x0C — победил).

## 7. Actions

Формат action: `u8 actionId` + payload. Общие типы:

- `NetTag` (ObjectId) — 2 × u32. `FFFFFFFF FFFFFFFF` = нет объекта ✔;
- `Vec2` — 2 × float32 (x, y) ✔;
- `AgentTag` — 8 байт, в 0x10–0x15 всегда `FF…FF` ✔.

### 7.1 Таблица (payload без байта ID)

| ID | Размер | Смысл | APM (w3gjs) | Наши файлы |
|---|---|---|---|---|
| 0x01 | 0 | pause (w3gjs пропускает 1 байт — на наших данных payload пустой ✔) | – | ✔ |
| 0x02 | 0 | resume | – | ✔ |
| 0x03 | 1 | set game speed | – | |
| 0x04, 0x05 | 0 | speed up / down | – | |
| 0x06 | cstr, cstr, u8 | save game | – | |
| 0x07 | 4 | save finished | – | |
| **0x10** | 14 | order без цели: u16 flags, fourcc order, AgentTag | + | ✔ |
| **0x11** | 22 | order на точку: …, Vec2 target | + | ✔ |
| **0x12** | 30 | order на точку + объект: …, Vec2, NetTag | + | ✔ |
| **0x13** | 38 | передать/бросить предмет: …, Vec2, NetTag unit, NetTag item | + | ✔ |
| **0x14** | 43 | два order (напр. right-click по fog-объекту): u16, fourcc, AgentTag, Vec2, fourcc order2, u32 flags, u32 category, u8 owner, Vec2 | + | ✔ |
| 0x15 | 51 | 0x14 + NetTag | ? | |
| **0x16** | 3+8n | change selection: u8 mode (1 add, 2 remove), u16 n, NetTag×n | + (*) | ✔ |
| **0x17** | 3+8n | assign group hotkey: u8 group, u16 n, NetTag×n | + | ✔ |
| **0x18** | 2 | select group hotkey: u8 group, u8 unknown (0x03) | + | ✔ |
| 0x19 | 12 | select subgroup: fourcc, NetTag | – | ✔ (всегда после 0x1A) |
| 0x1A | 0 | pre-subselection | – | ✔ |
| 0x1B | 9 | u8 + NetTag (синхронизация выбора) | – | ✔ самый частый |
| 0x1C | 9 | select ground item: u8 + NetTag | + | |
| 0x1D | 8 | cancel hero revival: NetTag | + | |
| 0x1E | 5 | remove from queue: u8 slot, fourcc | + | ✔ |
| 0x1F | 5 | как 0x1E ? | ? | |
| 0x20–0x2F | разн. | чит-коды (одиночная игра) | – | |
| 0x50 | 5 | change ally options | – | |
| 0x51 | 9 | transfer resources: u8 slot, u32 gold, u32 lumber | – | |
| 0x60 | 8 + cstr | map trigger chat (например, `-gg`) | – | ✔ |
| 0x61 | 0 | ESC | + | ✔ |
| 0x62 | 12 | scenario trigger | – | |
| 0x63 | 8 | ? | – | |
| 0x64, 0x65 | 8 | track/untrack object | 0x65: + | |
| 0x66 | 0 | открыть меню навыков героя | + | ✔ |
| 0x67 | 0 | открыть меню строительства | + | ✔ |
| 0x68 | 12 | minimap ping: Vec2, float duration | – | |
| 0x69, 0x6A | 16 | continue game | – | ✔ |
| 0x6B–0x73 | cache | SyncStored* (W3MMD-статистика карты): cstr file, cstr mission, cstr key + значение | – | |
| 0x75 | 1 | arrow key | – | |
| 0x76 | 10 | mouse: u8 event, Vec2, u8 button | – | |
| 0x77 | см. 7.3 | | – | ✔ |
| 0x78 | cstr, cstr, u32 | sync data (по w3gjs) | – | |
| 0x79 | 8 + u32 + float + cstr | | – | |
| 0x7A | см. 7.3 | | – | ✔ |
| 0x7B | 16 | | – | |

(*) 0x16 в w3gjs: пара «deselect (mode 2) + select (mode 1)» считается за одно действие.

Распределение на нашем 12-минутном матче (0240), игрок 1 / игрок 2:

- 0x12: 764 / 1042;
- 0x16: 607 / 522;
- 0x18: 580 / 727;
- 0x1B: 1600 / 2873;
- 0x7A: 218 / 297;
- 0x10: 81 / 75.

По правилам w3gjs это примерно 166 / 198 APM.

### 7.2 Order ID в 0x10–0x14

`order` — 4 байта, бывает двух видов:

- **строковый ID объекта** (fourcc), если `byte[3] ∈ 0x41..0x7A`: юнит, здание, апгрейд, предмет, навык. Байты в файле перевёрнуты: в файле `70 73 77 65` («pswe») = `ewsp` (Wisp) ✔;
- **числовой order** `0x000D00xx` (в файле `xx 00 0D 00`). w3gjs: `xx = 0x03, byte[1] = 0` — right-click (smart) ✔; `xx ≤ 0x19` — «базовая» команда (move/attack/stop/hold/patrol). Частые у нас:
  - `0x000D0003` — right-click;
  - `0x000D000F` — атака (вероятно) ?;
  - `0x000D0012` — move ?;
  - `0x000D020A`, `0x000D009B` и подобные — способности юнитов.

  Таблица имён — `w3g_actions.txt` и Order ID из `common.j` (`OrderId2String`). Сверить ?.

Как события выглядят на практике ✔:

| Событие | Actions |
|---|---|
| Тренировка/исследование | `0x7A (здание, 'Aque', 'ewsp')` + `0x10 flags 0x0042, order 'ewsp'` |
| Закладка здания | `0x11 flags 0x0004, order 'eate'` + точка |
| Навык героя | `0x66` (меню) → `0x7A (герой, 'AHer', 0x000D0020)` → `0x10 flags 0x0042, order 'AOmi'` |
| Покупка предмета | `0x7A (магазин, 'Amai', 'plcl')` + `0x10 flags 0x0040, order 'plcl'` |
| Атака/движение | `0x7A (юнит, 0, 0x000D000F)` + `0x12 order 0x000D000F` |

**Order ≠ результат.** Реплей хранит только ввод игроков. В нём нет смертей юнитов, ресурсов, отказов «not enough gold». Отмены видны частично: 0x1E и cancel-order 0x000D0008 ?.

### 7.3 Нумерация после 2.0.2 (важно)

w3gjs при `isPost202ReplayFormat` (чанк 0x38) делает `if (id > 0x77) id++`. Значит, ID в файле на 1 меньше «классических».

Что видим на наших файлах (patch 3.0, **без** чанка 0x38) ✔:

- **0x7A** — payload 16 байт: `NetTag subject, fourcc abilityId, u32 order`. Это совпадает со старым 0x7B (skip 16). Встречается перед большинством order-ов и несёт ID способности (`Aque`, `AHer`, `AEbu`, `AObu`, `Amai`, `Aast`…);
- **0x77** — payload `cstr, cstr, u32`, например `"T"`, `"AAE==QRXH8A=="`, `0`. Это layout старого **0x78** (вероятно, `BlzSendSyncData` скрипта карты W3C). w3gjs в post-2.02-режиме 0x77 не сдвигает и читает как `u32, u32, u32 len, buf`. На наших данных это даёт неверную длину.

Вывод:

- в наших файлах сдвиг начинается **с 0x77 включительно**, а не с 0x78;
- включать новую нумерацию надо по `version`/`build`, а не по чанку 0x38. Точная граница версии неизвестна;
- проверить на реплеях 2.0.0–2.0.4 и на реплеях не-W3C.

Без этой поправки весь поток 0x7A разбирается со сдвигом, а за ним ломаются все 0x10/0x12 того же CommandBlock.

С таблицей из 7.1 и поправками 7.3 на всех трёх файлах ✔ все 22 884 CommandBlock разобраны точно до своей границы. На 7 файлах `6ae0….w3g` (с чанками 0x38) ✔ — все 43 639 CommandBlock, неизвестных action-ов нет. Без поправки для 0x77 ломаются 6 блоков; с payload 1 байт для 0x01 (как в w3gjs) — ещё один.

## 8. Идентификаторы (fourcc)

- Первая буква — раса/тип: `h` Human, `o` Orc, `e` Night Elf, `u` Undead, `n` нейтралы. Заглавная — герой: `Hpal`, `Obla`, `Npal` (новый нейтральный герой 3.0, Forsaken Paladin).
- `R…` — апгрейды, `A…` — способности (`AHbz` — Blizzard).
- Предметы — 4 строчные буквы без расового префикса (`plcl`, `bspd`).
- Хранятся как u32 LE. Строка = байты в обратном порядке.

## 9. Стратегия версий (для парсера)

| Признак | Влияет на |
|---|---|
| `headerVersion` | формат SubHeader (поддерживаем только 1) |
| `build ≥ 6089` | заголовок блока 12 байт вместо 8 |
| наличие 0x38/0x39 | только чтение protobuf-метаданных (опционально) |
| `version ≥ ?` (2.0.2) **или** 0x38 | нумерация actions ≥ 0x77 (7.3) |

## 10. Что осталось проверить

1. Граница версии для сдвига ID ≥ 0x77. Нужны реплеи 2.0.x и не-W3C.
2. Payload 0x01 (pause): у нас 0 байт, в w3gjs 1. Проверить на реплеях с паузой и возобновлением.
3. Смысл `0x1B` (самый частый action) и полей `0x7A` при нулевой способности.
4. Таблица числовых order ID `0x000D00xx` для 3.0.
5. Победитель в реплеях без чанков 0x38 (там у всех leave-результат 0x07). В файлах с 0x38 решено, см. 6.2.
6. Алгоритм checksum блока и CRC заголовка (для валидации, не обязателен).
7. Флаги u16 в 0x10–0x14 (0x0040, 0x0042, 0x0004, 0x0000): что означают биты (shift-queue и т.п.).
