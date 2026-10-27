#!/usr/bin/env python3
"""Сверка testdata/expected/<id>.w3g.json с ответами W3Champions API (testdata/w3c-api/<id>.json).
Запуск: python3 -I compare_api.py <testdata_dir> [--write]
--write: дописывает в expected JSON поле source.externallyVerified и w3cApiCheck.
"""
import sys, json, os, re

HERO = {'archmage': 'Hamg', 'mountainking': 'Hmkg', 'paladin': 'Hpal', 'bloodmage': 'Hblm',
        'blademaster': 'Obla', 'farseer': 'Ofar', 'taurenchieftain': 'Otch', 'shadowhunter': 'Oshd',
        'deathknight': 'Udea', 'dreadlord': 'Udre', 'lich': 'Ulic', 'cryptlord': 'Ucrl',
        'demonhunter': 'Edem', 'keeperofthegrove': 'Ekee', 'priestessofthemoon': 'Emoo', 'warden': 'Ewar',
        'alchemist': 'Nalc', 'avatarofflame': 'Nfir', 'beastmaster': 'Nbst', 'bansheeranger': 'Nbrn',
        'darkranger': 'Nbrn', 'pandarenbrewmaster': 'Npbm', 'pitlord': 'Nplh', 'seawitch': 'Nngs',
        'nagaseawitch': 'Nngs', 'sorceror': 'Hblm', 'forsakenpaladin': 'Npal', 'tinker': 'Ntin', 'firelord': 'Nfir'}
RACE = {1: 'HUMAN', 2: 'ORC', 4: 'NIGHT_ELF', 8: 'UNDEAD', 0: 'RANDOM', 16: 'RANDOM'}


def norm_map(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


def main():
    td = sys.argv[1]; write = '--write' in sys.argv
    for fn in sorted(os.listdir(os.path.join(td, 'w3c-api'))):
        mid = fn[:-5]
        api = json.load(open(os.path.join(td, 'w3c-api', fn), encoding='utf8'))['match']
        # Сопоставление: gameName реплея = "w3c-<gw>-<original-ongoing-match-id>[-...]"
        epath = None
        for en in sorted(os.listdir(os.path.join(td, 'expected'))):
            cand = json.load(open(os.path.join(td, 'expected', en), encoding='utf8'))
            pref = 'w3c-%s-%s' % (api['gateWay'], api['original-ongoing-match-id'])  # id может содержать '-'
            if cand['gameName'] == pref or cand['gameName'].startswith(pref + '-'):
                epath = os.path.join(td, 'expected', en); exp = cand
        if epath is None: print('==', mid, 'нет реплея'); continue
        issues = []; ok = []
        aps = {p['battleTag']: p for t in api['teams'] for p in t['players']}
        rps = {p['name']: p for p in exp['players']}
        if set(aps) == set(rps): ok.append('players')
        else: issues.append('players: replay %s vs api %s' % (sorted(rps), sorted(aps)))
        race_ok = hero_ok = True
        for tag, ap in aps.items():
            rp = rps.get(tag)
            if not rp: continue
            api_race = RACE.get(ap['race'], 'race%s' % ap['race'])
            api_res = RACE.get(ap['rndRace']) if ap.get('rndRace') is not None else None
            if api_race != rp['race']:
                race_ok = False; issues.append('race %s: replay %s vs api %s' % (tag, rp['race'], api_race))
            if api_res and api_res != rp['raceResolved']:
                race_ok = False; issues.append('raceResolved %s: replay %s vs api rndRace %s' % (tag, rp['raceResolved'], api_res))
            api_h = [(HERO.get(h['name'], '?' + h['name']), h['level']) for h in ap['heroes']]
            rep_h = [(h['id'], h['skillsLearned']) for h in rp['heroes']]
            if sorted(h for h, _ in api_h) != sorted(h for h, _ in rep_h):
                hero_ok = False; issues.append('heroes %s: replay %s vs api %s' % (tag, rep_h, api_h))
            else:
                if [h for h, _ in api_h] != [h for h, _ in rep_h]:
                    issues.append('note: hero order %s: replay %s vs api %s (API не хронологичен)' % (tag, [h for h, _ in rep_h], [h for h, _ in api_h]))
                lv = dict(api_h)
                for h, sk in rep_h:
                    lvl = lv[h]
                    if sk > lvl: hero_ok = False; issues.append('hero %s %s: skills %d > api level %d' % (tag, h, sk, lvl))
                    elif sk != lvl: issues.append('note: hero %s %s skills %d < api level %d (неизрасходованные очки)' % (tag, h, sk, lvl))
        if race_ok: ok.append('race')
        if hero_ok: ok.append('heroes(set; skillsLearned<=level)')
        api_winner = [p['battleTag'] for p in aps.values() if p['won']]
        rep_winner = [p['name'] for p in exp['players'] if p['pid'] == exp['winnerPid']]
        if api_winner == rep_winner: ok.append('winner')
        else: issues.append('winner: replay %s vs api %s' % (rep_winner, api_winner))
        d_rep = exp['durationMs'] / 1000; d_api = api['durationInSeconds']
        delta = d_rep - d_api
        me = exp.get('matchEndMs')
        issues.append('duration: header %.1f s, matchEnd %s s vs api %d s (delta header %+.1f s)' % (d_rep, me and me / 1000, d_api, delta))
        if abs(delta) <= 2: ok.append('duration(±2s)')
        if norm_map(api['mapName']).split('v')[0] in norm_map(exp['map']['name']) or norm_map(exp['map']['name']).startswith(norm_map(api['mapName']).rstrip('0123456789')):
            ok.append('map')
        else: issues.append('map: replay %s vs api %s / %s' % (exp['map']['name'], api['map'], api['mapName']))
        print('==', mid, '->', os.path.basename(epath), 'OK:', ok); [print('   ', i) for i in issues]
        if write:
            exp['w3cMatchId'] = mid
            exp['apiWinnerName'] = api_winner[0] if len(api_winner) == 1 else None
            exp['source']['externallyVerified'] = ['W3Champions API /api/matches/%s: %s' % (mid, ', '.join(ok))]
            exp['w3cApiCheck'] = dict(apiFile='testdata/w3c-api/%s.json' % fn[:-5], matched=ok,
                                      notes=[i for i in issues], apiDurationInSeconds=d_api,
                                      apiMapName=api['mapName'])
            with open(epath, 'w', encoding='utf8') as f:
                json.dump(exp, f, ensure_ascii=False, indent=2); f.write('\n')


if __name__ == '__main__':
    main()
