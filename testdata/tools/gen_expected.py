#!/usr/bin/env python3
"""Независимый генератор эталонов testdata/expected/<file>.json для wc3-viewer.

Запуск: python3 -I gen_expected.py <mappings.ts> <out_dir> <replay.w3g>...
Основан на прототипе w3g_probe.py / actions2.py (scratchpad), но написан заново:
полный разбор контейнера, метаданных, TimeSlot и action-ов по W3G_FORMAT.md
(0x01 pause = 0 байт, 0x77 = cstr,cstr,u32, 0x7A = 16 байт).
Реплеи — недоверенные данные: только чтение, все смещения с проверкой границ.
"""
import sys, struct, zlib, json, re, os, collections, hashlib, math

MAGIC = b'Warcraft III recorded game\x1a\x00'
FIXED = {0x01: 0, 0x02: 0, 0x03: 1, 0x04: 0, 0x05: 0, 0x07: 4, 0x10: 14, 0x11: 22, 0x12: 30,
         0x13: 38, 0x14: 43, 0x15: 51, 0x18: 2, 0x19: 12, 0x1a: 0, 0x1b: 9, 0x1c: 9, 0x1d: 8,
         0x1e: 5, 0x1f: 5, 0x50: 5, 0x51: 9, 0x61: 0, 0x62: 12, 0x63: 8, 0x64: 8, 0x65: 8,
         0x66: 0, 0x67: 0, 0x68: 12, 0x69: 16, 0x6a: 16, 0x75: 1, 0x76: 10, 0x7a: 16, 0x7b: 16}
RACE = {0x01: 'HUMAN', 0x02: 'ORC', 0x04: 'NIGHT_ELF', 0x08: 'UNDEAD', 0x20: 'RANDOM'}
PREFIX_RACE = {'h': 'HUMAN', 'o': 'ORC', 'e': 'NIGHT_ELF', 'u': 'UNDEAD'}
# w3gjs: эти ID увеличивают APM (Player.ts / W3GReplay.handleActionBlock). 0x65 и 0x16 — особые случаи.
APM_IDS = {0x10, 0x11, 0x12, 0x13, 0x14, 0x17, 0x18, 0x1c, 0x1d, 0x1e, 0x61, 0x66, 0x67}


def load_mappings(path):
    src = open(path, encoding='utf8').read()
    out = {}
    for name in ('items', 'units', 'buildings', 'upgrades', 'abilityToHero'):
        m = re.search(r'const %s: \{[^}]*\} = \{(.*?)\n\};' % name, src, re.S)
        out[name] = dict(re.findall(r'^\s*"?([A-Za-z0-9]{4})"?:\s*"([^"]*)"', m.group(1), re.M))
    out['heroes'] = set(out['abilityToHero'].values())
    return out


class Reader:
    def __init__(self, b, o=0): self.b, self.o = b, o
    def need(self, n):
        if n < 0 or self.o + n > len(self.b): raise ValueError('out of bounds at %d (+%d)' % (self.o, n))
    def u8(self): self.need(1); v = self.b[self.o]; self.o += 1; return v
    def u16(self): self.need(2); v = struct.unpack_from('<H', self.b, self.o)[0]; self.o += 2; return v
    def u32(self): self.need(4); v = struct.unpack_from('<I', self.b, self.o)[0]; self.o += 4; return v
    def raw(self, n): self.need(n); v = bytes(self.b[self.o:self.o + n]); self.o += n; return v
    def cstr(self):
        e = self.b.find(b'\0', self.o)
        if e < 0: raise ValueError('unterminated cstr at %d' % self.o)
        v = bytes(self.b[self.o:e]); self.o = e + 1; return v


def fourcc(bs):
    """Строковый ID объекта (байты перевёрнуты) или None для числового order."""
    return bytes(bs[::-1]).decode('latin1') if 0x41 <= bs[3] <= 0x7a else None


def decode_settings(enc):
    out = bytearray(); mask = 0
    for i, c in enumerate(enc):
        if i % 8 == 0: mask = c
        else: out.append(c - 1 if (mask & (1 << (i % 8))) == 0 else c)
    return bytes(out)


def pb_fields(buf):
    """Минимальный protobuf: {field: [value]}; строки как bytes, varint как int."""
    r = Reader(buf); res = collections.defaultdict(list)
    def varint():
        s = v = 0
        while True:
            c = r.u8(); v |= (c & 0x7f) << s; s += 7
            if c < 0x80: return v
    while r.o < len(buf):
        key = varint(); f, wt = key >> 3, key & 7
        if wt == 0: res[f].append(varint())
        elif wt == 2: n = varint(); res[f].append(r.raw(n))
        elif wt == 5: res[f].append(r.u32())
        else: break
    return res


def classify(cc, M):
    if cc in M['heroes']: return 'hero'
    if cc in M['abilityToHero']: return 'skill'
    if cc in M['upgrades']: return 'research'
    if cc in M['buildings']: return 'build'
    if cc in M['units']: return 'train'
    if cc in M['items']: return 'item'
    return None


SKILL_DEDUP_MS = 500


def heroes_of(events):
    """Герои игрока. Порядок — по первому изучению навыка (герой точно существует),
    иначе по первому приказу найма. skillsLearned — без повторов того же навыка < 500 мс
    (двойной клик: второй приказ не проходит; сверено с уровнями из W3C API)."""
    h = {}
    for t, hid, ab in events:
        x = h.setdefault(hid, dict(id=hid, firstOrderMs=None, firstSkillMs=None, skillOrders=0, skillsLearned=0, _last={}))
        if ab is None:
            if x['firstOrderMs'] is None: x['firstOrderMs'] = t
            continue
        x['skillOrders'] += 1
        if x['firstSkillMs'] is None: x['firstSkillMs'] = t
        prev = x['_last'].get(ab)
        if prev is None or t - prev >= SKILL_DEDUP_MS: x['skillsLearned'] += 1
        x['_last'][ab] = t
    out = sorted(h.values(), key=lambda x: (x['firstSkillMs'] if x['firstSkillMs'] is not None else 10**12, x['firstOrderMs'] or 0))
    for x in out: del x['_last']
    return out


def parse(path, M):
    b = open(path, 'rb').read()
    if len(b) == 0: raise ValueError('empty file')
    off = b.find(MAGIC)
    if off < 0: raise ValueError('no magic')
    h = Reader(b, off + 28)
    hsize, csize, hver, dsize, nblocks = (h.u32() for _ in range(5))
    product = h.raw(4)[::-1].decode('latin1')
    version = h.u32(); build = h.u16(); flags = h.u16(); length_ms = h.u32(); h.u32()
    r = Reader(b, off + hsize); data = bytearray()
    for _ in range(nblocks):
        bc = r.u32(); bd = r.u32(); r.u32()
        data += zlib.decompressobj().decompress(r.raw(bc))
    # --- метаданные
    m = Reader(data, 4)
    names = {}
    m.u8(); pid = m.u8(); names[pid] = m.cstr().decode('utf8', 'replace'); m.raw(m.u8())
    game_name = m.cstr().decode('utf8', 'replace'); m.cstr()
    st = decode_settings(m.cstr())
    sr = Reader(st, 13); map_path = sr.cstr().decode('utf8', 'replace'); creator = sr.cstr().decode('utf8', 'replace')
    m.u32(); m.u32(); m.u32()
    while m.b[m.o] == 0x16:
        m.u8(); p = m.u8(); names[p] = m.cstr().decode('utf8', 'replace'); m.raw(m.u8()); m.u32()
    chunks = 0; pb_names = {}
    while m.b[m.o] in (0x38, 0x39):
        m.u8(); sub = m.u8(); ln = m.u32(); body = m.raw(ln); chunks += 1
        if sub == 3:
            f = pb_fields(body)
            if 1 in f and 2 in f: pb_names[f[1][0]] = f[2][0].decode('utf8', 'replace')
    if m.u8() != 0x19: raise ValueError('no GameStartRecord')
    m.u16(); ns = m.u8(); slots = []
    for _ in range(ns):
        s = m.raw(9)
        if s[2] == 2: slots.append(dict(pid=s[0], team=s[4], color=s[5], raceFlag=s[6]))
    m.u32(); m.u8(); m.u8()
    gstart = m.o
    # --- игровые блоки
    t = 0; leaves = []; chat = 0
    cmd = collections.Counter(); acts = collections.defaultdict(collections.Counter)
    apm_cnt = collections.Counter(); last_cmd_t = {}; last_desel = {}
    bo = collections.defaultdict(list); first_race = {}
    hero_order = collections.defaultdict(list)
    blocks_exact = blocks_bad = 0; unknown = collections.Counter()
    g = Reader(data, gstart)
    while g.o < len(data):
        bid = g.u8()
        if bid == 0x00: break
        if bid == 0x17:
            reason = g.u32(); p = g.u8(); res = g.u32(); g.u32()
            leaves.append(dict(timeMs=t, pid=p, reason=reason, result=res))
        elif bid in (0x1a, 0x1b, 0x1c): g.u32()
        elif bid in (0x1e, 0x1f):
            n = g.u16(); inc = g.u16(); t += inc; end = g.o + n - 2
            while g.o < end:
                p = g.u8(); al = g.u16(); a = g.raw(al)
                cmd[p] += 1; last_cmd_t[p] = t; last_desel[p] = False
                ar = Reader(a); ok = True
                while ar.o < len(a):
                    aid = ar.u8(); acts[p][aid] += 1
                    start = ar.o
                    try:
                        if aid in (0x10, 0x11, 0x12):
                            fl = struct.unpack_from('<H', a, start)[0]
                            cc = fourcc(a[start + 2:start + 6])
                            if cc:
                                kind = classify(cc, M)
                                if kind:
                                    bo[p].append(dict(timeMs=t, id=cc, type=kind, action='0x%02x' % aid))
                                    if cc[0] in PREFIX_RACE and p not in first_race: first_race[p] = PREFIX_RACE[cc[0]]
                                    if kind == 'hero': hero_order[p].append((t, cc, None))
                                    if kind == 'skill': hero_order[p].append((t, M['abilityToHero'][cc], cc))
                        if aid in APM_IDS: apm_cnt[p] += 1
                        if aid == 0x16:
                            mode = a[start]
                            if mode == 0x02:
                                last_desel[p] = True; apm_cnt[p] += 1
                            else:
                                if not last_desel[p]: apm_cnt[p] += 1
                                last_desel[p] = False
                        if aid == 0x77: ar.cstr(); ar.cstr(); ar.u32()
                        elif aid == 0x60: ar.u32(); ar.u32(); ar.cstr()  # map trigger chat
                        elif aid in FIXED: ar.raw(FIXED[aid])
                        elif aid in (0x16, 0x17): ar.u8(); k = ar.u16(); ar.raw(8 * k)
                        else: unknown[aid] += 1; ok = False; break
                    except (ValueError, struct.error, IndexError):
                        ok = False; break
                if ok and ar.o == len(a): blocks_exact += 1
                else: blocks_bad += 1
            g.o = end
        elif bid == 0x20: g.u8(); n = g.u16(); g.raw(n); chat += 1
        elif bid == 0x22: g.raw(g.u8())
        elif bid == 0x23: g.raw(10)
        elif bid == 0x2f: g.raw(8)
        else: raise ValueError('unknown game block 0x%02x at %d' % (bid, g.o - 1))
    players = [s for s in slots if s['team'] != 24]
    winners = [l['pid'] for l in leaves if l['result'] == 0x09 and any(s['pid'] == l['pid'] for s in players)]
    out_players = []
    for s in players:
        p = s['pid']; race = RACE.get(s['raceFlag'] & 0x3f, 'UNKNOWN')
        played = last_cmd_t.get(p, 0)
        apm = math.floor(apm_cnt[p] / (played / 60000) + 0.5) if played else 0  # как JS Math.round
        out_players.append(dict(
            pid=p, name=pb_names.get(p, names.get(p)), team=s['team'], color=s['color'],
            raceFlag='0x%02x' % s['raceFlag'], race=race,
            raceResolved=(first_race.get(p) if race == 'RANDOM' else race),
            commandBlocks=cmd[p],
            actionCounts={'0x%02x' % k: v for k, v in sorted(acts[p].items())},
            apmW3gjs=dict(countedActions=apm_cnt[p], lastCommandBlockMs=played, apm=apm),
            heroes=heroes_of(hero_order[p]),
            buildOrderFirst15=bo[p][:15],
            buildOrderTotal=len(bo[p]),
        ))
    observers = [dict(pid=s['pid'], name=names.get(s['pid']), team=s['team']) for s in slots if s['team'] == 24]
    mf = re.split(r'[\\/]', map_path)[-1]
    map_name = re.sub(r'\.w3[mx]$', '', re.sub(r'^\d+_w3c_\d+_\d+_', '', mf))
    return dict(
        file=os.path.basename(path), fileSize=len(b), sha256=hashlib.sha256(b).hexdigest(), product=product,
        version=version, build=build, patch='%d.%02d' % (version // 100 - 99, version % 100) if version >= 10100 else None,
        durationMs=length_ms, gameTimeSumMs=t,
        matchEndMs=max([l['timeMs'] for l in leaves if any(s['pid'] == l['pid'] for s in players)], default=None),
        gameName=game_name, map=dict(path=map_path, name=map_name, creator=creator),
        reforgedChunks=chunks,
        players=out_players, observers=observers,
        leaves=[dict(l, reason='0x%02x' % l['reason'], result='0x%02x' % l['result']) for l in leaves],
        winnerPid=winners[0] if len(winners) == 1 else None,
        chatMessages=chat,
        parse=dict(gameDataOffset=gstart, commandBlocksExact=blocks_exact, commandBlocksBroken=blocks_bad,
                   unknownActions={'0x%02x' % k: v for k, v in unknown.items()}),
    )


def main():
    M = load_mappings(sys.argv[1]); out_dir = sys.argv[2]
    for path in sys.argv[3:]:
        res = parse(path, M)
        res = dict(source=dict(
            generator='gen_expected.py (Python, независим от Kotlin-кода; основан на прототипе w3g_probe.py/actions2.py)',
            classification='w3gjs mappings.ts (MIT): units/buildings/upgrades/items/abilityToHero',
            apmRule='w3gjs: см. docs/testing/TEST_STRATEGY.md §APM',
            externallyVerified=[]), **res)
        with open(os.path.join(out_dir, res['file'] + '.json'), 'w', encoding='utf8') as f:
            json.dump(res, f, ensure_ascii=False, indent=2); f.write('\n')
        print(res['file'], 'ok', res['parse'], [(p['name'], p['race'], p['apmW3gjs']['apm']) for p in res['players']], 'winner', res['winnerPid'])


if __name__ == '__main__':
    main()
