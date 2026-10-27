"""Build gamedata/3.0/*.json from cached Liquipedia wikitext.

Input:  tools/gamedata/cache/pages.json (see fetch.py), gamedata/sources/w3gjs-mappings.ts,
        testdata/expected/*.json (read-only, for the build-order coverage check).
Output: gamedata/3.0/{meta,units,buildings,heroes,upgrades,items}.json, gamedata/3.0/UNMATCHED.md.
Run:    python3 -I tools/gamedata/build.py
"""
import glob
import json
import os
import re
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wikitext import (bullet_values, find_templates, links, norm_name, num,  # noqa: E402
                      strip_comments)

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CACHE = os.path.join(ROOT, "tools", "gamedata", "cache")
MAPPINGS = os.path.join(ROOT, "gamedata", "sources", "w3gjs-mappings.ts")
EXPECTED = os.path.join(ROOT, "testdata", "expected")
OUT = os.path.join(ROOT, "gamedata", "3.0")

WIKI = "https://liquipedia.net/warcraft/"
PATCH = "3.0.1"
SCHEMA_VERSION = 1

RACES = {"human": "human", "h": "human", "orc": "orc", "o": "orc", "undead": "undead", "u": "undead",
         "nightelf": "nightelf", "night elf": "nightelf", "n": "nightelf", "e": "nightelf"}
RACE_CATEGORY = {"Human Heroes": "human", "Orc Heroes": "orc", "Undead Heroes": "undead",
                 "Night Elf Heroes": "nightelf", "Neutral Heroes": "neutral"}
RACE_LETTER = {"h": "human", "o": "orc", "u": "undead", "e": "nightelf", "w": "orc"}
TIER_BUILDINGS = {"hkee": 2, "ostr": 2, "unp1": 2, "etoa": 2, "hcas": 3, "ofrt": 3, "unp2": 3, "etoe": 3}
# Building morphs (upgrade in place). Liquipedia infoboxes do not link them; the chain is game structure,
# costs are still taken from Liquipedia (which lists the cumulative cost of the upgraded building).
MORPHS = {"htow": ["hkee"], "hkee": ["hcas"], "ogre": ["ostr"], "ostr": ["ofrt"], "unpl": ["unp1"],
          "unp1": ["unp2"], "etol": ["etoa"], "etoa": ["etoe"], "hwtw": ["hgtw", "hctw", "hatw"],
          "uzig": ["uzg1", "uzg2"]}
TIER_NAMES = {"keep": 2, "stronghold": 2, "hallsofthedead": 2, "treeofages": 2,
              "castle": 3, "fortress": 3, "blackcitadel": 3, "treeofeternity": 3}

# Patches whose balance notes are checked against page revision dates (staleness check).
CHECKED_PATCHES = ["2.0.2", "2.0.2.22796", "2.0.3", "2.0.3.23101", "2.0.4", "3.0.0", "3.0.1"]

# Spot checks: values stated in Liquipedia patch notes (page in `src`) must equal the infobox/card value.
PATCH_CHECKS = [
    ("units", "ebal", "buildTimeSec", 42, "Patch 2.0.4/night elf", "Glaive Thrower build time from 48 to 42 seconds."),
    ("units", "ufro", "buildTimeSec", 60, "Patch 2.0.4/undead", "Frost Wyrm build time from 65 to 60 seconds."),
    ("units", "otau", "goldCost", 300, "Patch 2.0.2/orc", "Tauren cost increased from 280/80 to 300/100."),
    ("units", "otau", "lumberCost", 100, "Patch 2.0.2/orc", "Tauren cost increased from 280/80 to 300/100."),
    ("buildings", "ogre", "buildTimeSec", 135, "Patch 2.0.2/orc", "Great Hall build time from 140 to 135."),
    ("upgrades", "Rhde", "levels.0.goldCost", 125, "Patch 2.0.2/human", "Defend cost reduced from 150/100 to 125/75"),
    ("upgrades", "Rhde", "levels.0.lumberCost", 75, "Patch 2.0.2/human", "Defend cost reduced from 150/100 to 125/75"),
    ("upgrades", "Rhde", "levels.0.researchTimeSec", 40, "Patch 2.0.2/human", "research time reduced from 45 to 40"),
    ("upgrades", "Remg", "levels.0.goldCost", 125, "Patch 2.0.2/night elf", "Moon Glaive cost increased from 100/150 to 125/175"),
    ("upgrades", "Remg", "levels.0.lumberCost", 175, "Patch 2.0.2/night elf", "Moon Glaive cost increased from 100/150 to 125/175"),
    ("upgrades", "Remg", "levels.0.researchTimeSec", 50, "Patch 2.0.3/night elf", "Moon Glaives upgrade time decreased from 60 to 50"),
    ("upgrades", "Reuv", "levels.0.researchTimeSec", 45, "Patch 2.0.2.22796/night elf", "Ultravision research time increased from 30 to 45 seconds."),
    ("upgrades", "Rubu", "levels.0.researchTimeSec", 30, "Patch 2.0.2/undead", "Burrow research time reduced from 45 to 30 seconds."),
    ("upgrades", "Rhpt", "levels.1.researchTimeSec", 60, "Patch 2.0.4/human", "Priest Master Training research time reduced from 75 to 60 seconds."),
    ("upgrades", "Rhst", "levels.1.researchTimeSec", 60, "Patch 2.0.4/human", "Sorceress Master Training research time reduced from 75 to 60 seconds."),
    ("upgrades", "Redt", "levels.1.researchTimeSec", 60, "Patch 2.0.4/night elf", "Druid of the Talon Master Training research time from 75 to 60 seconds."),
    ("upgrades", "Rost", "levels.1.researchTimeSec", 60, "Patch 2.0.4/orc", "Shaman Master Training research time from 75 to 60 seconds."),
    ("upgrades", "Rowd", "levels.1.researchTimeSec", 60, "Patch 2.0.4/orc", "Witch Doctor Master Training research time from 75 to 60 seconds."),
    ("upgrades", "Rowt", "levels.1.researchTimeSec", 60, "Patch 2.0.4/orc", "Spirit Walker Master Training research time from 65 to 60 seconds."),
    ("upgrades", "Ruba", "levels.1.researchTimeSec", 60, "Patch 2.0.4/undead", "Banshee Master Training research time from 75 to 60 seconds."),
    ("items", "rde2", "goldCost", 125, "Patch 2.0.2/item", "Ring of Protection +3 gold cost reduced from 150 to 125"),
    ("items", "name:Wand of Negation", "goldCost", 120, "Patch 2.0.4/undead", "Wand of Negation cost from 150 to 120."),
    ("items", "name:Ritual Dagger", "goldCost", 75, "Patch 2.0.2/undead", "Ritual Dagger gold cost reduced from 100 to 75"),
]


# ---------------------------------------------------------------- inputs

def load_pages():
    with open(os.path.join(CACHE, "pages.json"), encoding="utf-8") as f:
        pages = json.load(f)
    fetched = []
    for path in glob.glob(os.path.join(CACHE, "query-*.json")):
        with open(path, encoding="utf-8") as f:
            fetched.append(json.load(f).get("fetchedAt", ""))
    return pages, max(fetched) if fetched else None


def load_mappings():
    src = open(MAPPINGS, encoding="utf-8").read()

    def section(name):
        m = re.search(r"const " + name + r"\b[^{]*\{(.*?)\n\};", src, re.S)
        return dict(re.findall(r'^\s*(\w{4}):\s*"([^"]*)"', m.group(1), re.M))

    maps = {k: section(k) for k in ("items", "units", "buildings", "upgrades", "heroAbilities", "abilityToHero")}
    strip = lambda d: {k: re.sub(r"^[iupb]_", "", v) for k, v in d.items()}  # noqa: E731
    maps["items"] = strip(maps["items"])
    maps["units"] = strip(maps["units"])
    maps["buildings"] = strip(maps["buildings"])
    maps["upgrades"] = strip(maps["upgrades"])
    heroes = {}
    for abil, hero in maps["abilityToHero"].items():
        label = maps["heroAbilities"].get(abil, "")
        if ":" in label:
            heroes.setdefault(hero, re.sub(r"^a_", "", label).split(":")[0])
    maps["heroes"] = heroes
    return maps


# ---------------------------------------------------------------- helpers

def page_url(title):
    return WIKI + urllib.parse.quote(title.replace(" ", "_"), safe="/:()'+,")


class Sources:
    """Tracks every page used, for meta.json and per-entity provenance."""

    def __init__(self, pages):
        self.pages = pages
        self.used = {}

    def ref(self, title):
        p = self.pages[title]
        self.used[title] = {"title": title, "pageid": p["pageid"], "revid": p["revid"],
                            "timestamp": p["timestamp"], "url": page_url(title),
                            "permalink": f"{WIKI}index.php?oldid={p['revid']}"}
        return {"page": title, "revid": p["revid"], "url": page_url(title)}


def first(templates):
    return templates[0] if templates else None


def race_of(value):
    if value is None:
        return None
    v = strip_comments(value).strip().lower()
    return RACES.get(v, "neutral" if v else None)


def null_note(entity, field, why="not on the Liquipedia page"):
    entity.setdefault("nullFields", {})[field] = why


# ---------------------------------------------------------------- parse

def parse_units_buildings(pages, src):
    units, buildings = [], []
    for title in sorted(pages):
        p = pages[title]
        if p["ns"] != 0:
            continue
        text = strip_comments(p["content"])
        for kind, out in (("Infobox unit", units), ("Infobox building", buildings)):
            ib = first(find_templates(text, kind))
            if ib is None:
                continue
            e = {
                "id": (ib.get("id") or "").strip() or None,
                "idSource": "liquipedia" if (ib.get("id") or "").strip() else None,
                "name": (ib.get("name") or title).strip(),
                "race": race_of(ib.get("race")),
                "type": "unit" if kind == "Infobox unit" else "building",
                "goldCost": num(ib.get("gold")),
                "lumberCost": num(ib.get("lumber")),
                "foodCost": num(ib.get("food")),
                "foodProvided": num(ib.get("foodproduced")),
                "buildTimeSec": num(ib.get("build_time")),
                "hp": num(ib.get("hp")),
                "armor": num(ib.get("armor")),
                "armorType": (ib.get("armortype") or "").strip() or None,
                "level": num(ib.get("level")),
                "classes": bullet_values(ib.get("class")),
                "_requires": links(ib.get("requires")),
                "_builtfrom": links(ib.get("builtfrom")),
                "_builds": links(ib.get("builds")),
                "_upgrades": links(ib.get("upgrades")),
                "_unlocks": links(ib.get("unlocks")),
                "_title": title,
                "source": src.ref(title),
            }
            raw_race = (ib.get("race") or "").strip()
            if raw_race and e["race"] == "neutral" and raw_race.lower() != "neutral":
                e["raceLiquipedia"] = raw_race
            for f in ("goldCost", "lumberCost", "foodCost", "foodProvided", "buildTimeSec", "hp", "armor"):
                if e[f] is None:
                    null_note(e, f)
            if e["type"] == "unit":
                e["isWorker"] = any(c.lower() == "peon" for c in e["classes"])
            out.append(e)
    return units, buildings


def parse_hero_costs(pages, src):
    """Hero costs from the 'Hero' page table (altar vs tavern, first vs later heroes)."""
    text = pages["Hero"]["content"]
    rows = {}
    for label in ("First Hero", "Second Hero", "Third Hero"):
        m = re.search(r"!\s*" + label + r"\s*\n\|([^\n]*)\n\|([^\n]*)", text)
        if not m:
            continue

        def cost(cell):
            g = re.search(r"\{\{gold\}\}\s*(\d+)", cell)
            lu = re.search(r"\{\{lumber\}\}\s*(\d+)", cell)
            fo = re.search(r"\{\{food\}\}\s*(\d+)", cell)
            t = re.search(r"Training\s*(\d+)s", cell)
            return {"goldCost": int(g.group(1)) if g else 0, "lumberCost": int(lu.group(1)) if lu else 0,
                    "foodCost": int(fo.group(1)) if fo else None,
                    "buildTimeSec": int(t.group(1)) if t else None}

        rows[label] = {"altar": cost(m.group(1)), "tavern": cost(m.group(2))}
    return rows, src.ref("Hero")


def parse_spellcards(pages):
    cards = {}
    for title, p in pages.items():
        if not title.startswith("Template:SpellCard/"):
            continue
        text = strip_comments(p["content"])
        sc = first(find_templates(text, "SpellCard"))
        if sc is None:
            # Wrapper card such as {{SpellCard/UnitInventory|...}}: expand the wrapper's parameters.
            m = re.match(r"\s*\{\{\s*SpellCard/(\w+)", text)
            wrapper = f"Template:SpellCard/{m.group(1)}" if m else None
            if wrapper in pages:
                args = first(find_templates(text, f"SpellCard/{m.group(1)}")) or {}
                body = re.sub(r"\{\{\{(\w+)\|?([^}]*)\}\}\}",
                              lambda x: args.get(x.group(1), x.group(2)), pages[wrapper]["content"])
                sc = first(find_templates(strip_comments(body), "SpellCard"))
                if sc is not None:
                    sc["_wrapper"] = wrapper
        if sc:
            cards[title.split("/", 1)[1]] = (sc, title)
    return cards


def parse_heroes(pages, src, maps, spellcards, costs):
    heroes = []
    for title in sorted(pages):
        p = pages[title]
        if p["ns"] != 0:
            continue
        text = strip_comments(p["content"])
        ib = first(find_templates(text, "Infobox heroauto"))
        if ib is None:
            continue
        ability_ids = []
        for m in re.finditer(r"\{\{\s*SpellCard/(\w+)\s*\}\}", text):
            if m.group(1) not in ability_ids:
                ability_ids.append(m.group(1))
        abilities = []
        for a in ability_ids:
            # Hero abilities only (AH*, AO*, AU*, AE*, AN*); unit cards such as Shadowmeld are skipped.
            if not re.fullmatch(r"A[HOUEN]\w\w", a):
                continue
            card = spellcards.get(a)
            abilities.append({"id": a, "name": card[0].get("name") if card else None,
                              "inW3gjs": a in maps["abilityToHero"],
                              "source": src.ref(card[1]) if card else None})
        votes = [maps["abilityToHero"][a] for a in ability_ids if a in maps["abilityToHero"]]
        hero_id, id_source = None, None
        if votes:
            hero_id = max(set(votes), key=votes.count)
            id_source = "w3gjs:abilityToHero"
        else:
            for hid, hname in maps["heroes"].items():
                if norm_name(hname) == norm_name(title):
                    hero_id, id_source = hid, "w3gjs:name"
        race = race_of(ib.get("race"))
        if race is None:
            race = next((RACE_CATEGORY[c.split(":", 1)[1]] for c in p["categories"]
                         if c.split(":", 1)[1] in RACE_CATEGORY), None)
        trained = (ib.get("trainedat") or "").strip()
        if race is None and trained == "Tavern":
            race = "neutral"
        where = "tavern" if trained == "Tavern" else "altar"
        later = costs[0].get("Second Hero", {}).get(where, {})
        first_cost = costs[0].get("First Hero", {}).get(where, {})
        e = {
            "id": hero_id, "idSource": id_source, "name": title, "race": race, "type": "hero",
            "goldCost": later.get("goldCost"), "lumberCost": later.get("lumberCost"),
            "foodCost": later.get("foodCost"), "foodProvided": 0 if later else None,
            "buildTimeSec": later.get("buildTimeSec"),
            "firstHeroCost": {"goldCost": first_cost.get("goldCost"), "lumberCost": first_cost.get("lumberCost")}
            if first_cost else None,
            "hp": None, "armor": num(ib.get("basearmor")),
            "primaryAttribute": (ib.get("mainattribute") or "").strip() or None,
            "attributes": {"str": num(ib.get("basestr")), "strGain": num(ib.get("strgain")),
                           "agi": num(ib.get("baseagi")), "agiGain": num(ib.get("agigain")),
                           "int": num(ib.get("baseint")), "intGain": num(ib.get("intgain"))},
            "_trainedat": trained, "abilities": abilities,
            "source": src.ref(title), "costSource": costs[1],
        }
        null_note(e, "hp", "Liquipedia computes hero HP in a Lua module from attributes; not stored in wikitext")
        e.setdefault("notes", []).append("armor = base armor before agility bonus (Infobox heroauto basearmor)")
        if where == "tavern":
            null_note(e, "buildTimeSec", "tavern heroes are bought instantly ('Buyable at 2:15' on page Hero)")
        heroes.append(e)
    return heroes


def parse_upgrades(pages, src, spellcards):
    ups = []
    for title in sorted(pages):
        if not title.startswith("Template:UpgradeCard/") or title.endswith("/doc"):
            continue
        uc = first(find_templates(strip_comments(pages[title]["content"]), "UpgradeCard"))
        if uc is None:
            continue
        n = num(uc.get("levels")) or 1
        g, gu = num(uc.get("gold")), num(uc.get("goldupgrade")) or 0
        lu, luu = num(uc.get("lumber")), num(uc.get("lumberupgrade")) or 0
        t, tu = num(uc.get("researchtime")), num(uc.get("researchtimeupgrade")) or 0
        levels = []
        for i in range(1, n + 1):
            req = uc.get(f"requiredA{i}")
            levels.append({
                "level": i, "name": (uc.get(f"name{i}") or "").strip() or None,
                "goldCost": None if g is None else g + (i - 1) * gu,
                "lumberCost": None if lu is None else lu + (i - 1) * luu,
                "researchTimeSec": None if t is None else t + (i - 1) * tu,
                "_requires": links(req) if req else [],
            })
        ups.append({
            "id": title.split("/", 1)[1], "idSource": "liquipedia:template", "name": levels[0]["name"],
            "race": None, "type": "upgrade", "_researched": links(uc.get("researched")),
            "levels": levels,
            "costFormula": {"gold": [g, gu], "lumber": [lu, luu], "researchTimeSec": [t, tu],
                            "note": "level N = base + (N-1) * increment (UpgradeCard gold/goldupgrade etc.)"},
            "source": src.ref(title),
        })
    # Researches that Liquipedia describes in the ability card (SpellCard with gold/researched).
    # Several ability cards can describe one research (e.g. Burrow); keep the first per (building, name).
    card_ids = {u["id"] for u in ups}
    card_names = {norm_name(lv["name"]) for u in ups for lv in u["levels"] if lv["name"]}
    seen = set()
    for aid, (sc, title) in sorted(spellcards.items()):
        researched = (sc.get("researched") or "").strip()
        if not researched or "{{{" in researched or aid in card_ids:
            continue
        name = (sc.get("upgradename") or sc.get("name") or "").strip()
        key = (norm_name(researched), norm_name(name))
        if key in seen or norm_name(name) in card_names:
            continue
        seen.add(key)
        reqs = []
        for k in sorted(sc):
            if re.fullmatch(r"required\d*", k) and sc[k]:
                reqs += links(sc[k])
        ups.append({
            "id": None, "idSource": None, "abilityId": aid, "name": name, "race": None, "type": "upgrade",
            "_researched": links(researched),
            "levels": [{"level": 1, "name": name, "goldCost": num(sc.get("gold")),
                        "lumberCost": num(sc.get("lumber")), "researchTimeSec": num(sc.get("researchtime")),
                        "_requires": reqs}],
            "source": src.ref(title),
        })
        if sc.get("_wrapper"):
            ups[-1]["costSource"] = src.ref(sc["_wrapper"])
    return ups


def parse_items(pages, src):
    items = []
    for title in sorted(pages):
        p = pages[title]
        if p["ns"] != 0:
            continue
        ib = first(find_templates(strip_comments(p["content"]), "Infobox item"))
        if ib is None:
            continue
        sold = []
        js = first(find_templates(ib.get("soldfrom") or "", "Json"))
        if js:
            sold = [v.strip() for k, v in sorted(js.items()) if re.fullmatch(r"name\d+", k) and v.strip()]
        elif (ib.get("soldfrom") or "").strip():
            sold = links(ib.get("soldfrom"))
        e = {
            "id": None, "idSource": None, "name": title, "race": None, "type": "item",
            "goldCost": num(ib.get("gold")), "lumberCost": num(ib.get("lumber")),
            "level": num(ib.get("level")), "itemClass": (ib.get("class") or "").strip() or None,
            "charges": num(ib.get("charges")), "stock": num(ib.get("stock")),
            "stockStartSec": num(ib.get("stockstart")), "stockReplenishSec": num(ib.get("stockreplenish")),
            "_soldfrom": sold, "_requires": links(ib.get("requires")),
            "source": src.ref(title),
        }
        for f in ("goldCost", "lumberCost"):
            if e[f] is None:
                null_note(e, f, "field is empty on the Liquipedia page")
        if not sold:
            null_note(e, "soldFrom", "Infobox item has no 'soldfrom' value")
        items.append(e)
    return items


# ---------------------------------------------------------------- linking

class NameIndex:
    def __init__(self, entities):
        self.by = {}
        for e in entities:
            for key in {norm_name(e["name"]), norm_name(e.get("_title", e["name"]))}:
                self.by.setdefault(key, []).append(e)

    def resolve(self, name, race=None):
        cands = self.by.get(norm_name(name), [])
        if len(cands) > 1 and race:
            same = [c for c in cands if c["race"] == race]
            cands = same or cands
        exact = [c for c in cands if c.get("_title") == name]
        pick = exact[0] if exact else (cands[0] if cands else None)
        return {"name": name, "id": pick["id"] if pick else None}


def tier_for(required, producers, by_id, seen, own=1):
    """Tech tier: 1 by default, 2/3 when a Keep/Castle-level building is needed (transitively)."""
    def ref_tier(r):
        m = re.fullmatch(r"Tier (\d)", r["name"])
        t = int(m.group(1)) if m else TIER_BUILDINGS.get(r["id"]) or TIER_NAMES.get(norm_name(r["name"]))
        if t:
            return t
        e = by_id.get(r["id"])
        if e is None or r["id"] in seen:
            return 1
        return tier_for(e["requirements"] + ([e["upgradesFrom"]] if e.get("upgradesFrom") else []),
                        e["producedBy"], by_id, seen | {r["id"]})
    tier = max([own] + [ref_tier(r) for r in required])
    prod = [ref_tier(p) for p in producers]
    return max(tier, min(prod)) if prod else tier


def link_all(units, buildings, heroes, upgrades, items, maps):
    ub = units + buildings
    idx = NameIndex(ub + heroes)

    # Ids for unit/building pages without an id in the infobox: name match with w3gjs.
    for e in ub:
        if e["id"] is None:
            table = maps["units"] if e["type"] == "unit" else maps["buildings"]
            hit = [k for k, v in table.items() if norm_name(v) == norm_name(e["name"])]
            if hit:
                e["id"], e["idSource"] = hit[0], "w3gjs:name"

    for e in ub:
        e["requirements"] = [idx.resolve(n, e["race"]) for n in e.pop("_requires")]
        e["producedBy"] = [idx.resolve(n, e["race"]) for n in e.pop("_builtfrom")]
    for b in buildings:
        b["trains"] = [idx.resolve(n, b["race"]) for n in b.pop("_builds")]
        ups = [idx.resolve(n, b["race"]) for n in b.pop("_upgrades")]
        b["upgradesTo"] = [u for u in ups if u["id"] and any(x["id"] == u["id"] for x in buildings)]
        b["_researchNames"] = [u["name"] for u in ups if u not in b["upgradesTo"]]
        b["unlocks"] = [idx.resolve(n, b["race"]) for n in b.pop("_unlocks")]
    by_id = {e["id"]: e for e in ub if e["id"]}
    for src_id, targets in MORPHS.items():
        b = by_id.get(src_id)
        if b:
            for t in targets:
                if t in by_id and not any(u["id"] == t for u in b["upgradesTo"]):
                    b["upgradesTo"].append({"name": by_id[t]["_title"], "id": t})
    # producedBy for units from the building side (Infobox building `builds`).
    for b in buildings:
        for t in b["trains"]:
            u = by_id.get(t["id"])
            if u and not any(x["id"] == b["id"] for x in u["producedBy"]):
                u["producedBy"].append({"name": b["_title"], "id": b["id"]})
    # Building upgrades (Keep, Castle, towers...): Liquipedia lists the cumulative cost.
    for b in buildings:
        for t in b["upgradesTo"]:
            tgt = by_id.get(t["id"])
            if not tgt:
                continue
            tgt["upgradesFrom"] = {"name": b["_title"], "id": b["id"]}
            if None not in (tgt["goldCost"], b["goldCost"], tgt["lumberCost"], b["lumberCost"]):
                tgt["upgradeCost"] = {"goldCost": tgt["goldCost"] - b["goldCost"],
                                      "lumberCost": tgt["lumberCost"] - b["lumberCost"], "derived": True}
    # Tier (derived): max over requirements and the morph source; min over producers (any one suffices).
    for e in ub:
        e["tier"] = tier_for(e["requirements"] + ([e["upgradesFrom"]] if e.get("upgradesFrom") else []),
                             e["producedBy"], by_id, {e["id"]}, own=TIER_BUILDINGS.get(e["id"], 1))

    # Heroes: trainedAt.
    for h in heroes:
        h["producedBy"] = [idx.resolve(h.pop("_trainedat"))] if h.get("_trainedat") else []
        h["requirements"] = []
        h["tier"] = 1

    # Upgrades: researched building, race, ids for SpellCard researches.
    up_ids = {u["id"] for u in upgrades if u["id"]}
    for u in upgrades:
        u["researchedAt"] = [idx.resolve(n) for n in u.pop("_researched")]
        site = by_id.get(u["researchedAt"][0]["id"]) if u["researchedAt"] else None
        u["race"] = site["race"] if site else (RACE_LETTER.get(u["id"][1]) if u["id"] else None)
        for lv in u["levels"]:
            lv["requirements"] = [idx.resolve(n, u["race"]) for n in lv.pop("_requires")]
            lv["tier"] = tier_for(lv["requirements"], u["researchedAt"], by_id, set())
        if u["id"] is None:
            name = norm_name(u["name"])
            names = {k: {norm_name(v), norm_name(re.sub(r"^Upgrade ", "", v))}
                     for k, v in maps["upgrades"].items()}
            hits = [k for k, ns in names.items()
                    if name in ns and RACE_LETTER.get(k[1]) == u["race"] and k not in up_ids]
            if hits:
                u["id"], u["idSource"] = hits[0], "w3gjs:name"
                up_ids.add(hits[0])
    for u in upgrades:
        for f in ("goldCost", "lumberCost", "researchTimeSec"):
            if any(lv[f] is None for lv in u["levels"]):
                null_note(u, f, "not on the Liquipedia card for at least one level")

    for b in buildings:
        b["researches"] = [{"name": u["name"], "id": u["id"]} for u in upgrades
                           if any(r["id"] == b["id"] for r in u["researchedAt"])]

    # Items: ids by name.
    item_names = {}
    for k, v in maps["items"].items():
        item_names.setdefault(norm_name(v), []).append(k)
    expanded = []
    for it in items:
        it["soldFrom"] = [idx.resolve(n) for n in it.pop("_soldfrom")]
        it["requirements"] = [idx.resolve(n) for n in it.pop("_requires")]
        it["tier"] = tier_for(it["requirements"], [], by_id, set()) if it["requirements"] else None
        ids = item_names.get(norm_name(it["name"]), [])
        if not ids:
            expanded.append(it)
            continue
        for i, k in enumerate(ids):
            copy = dict(it, id=k, idSource="w3gjs:name")
            if len(ids) > 1:
                copy["sharedNameIds"] = ids
            expanded.append(copy)
    items[:] = expanded


# ---------------------------------------------------------------- staleness & checks

def patch_objects(pages):
    release = {}
    for title, p in pages.items():
        m = re.fullmatch(r"Patch ([0-9.]+)", title)
        if m:
            ip = first(find_templates(p["content"], "Infobox patch"))
            if ip and re.match(r"\d{4}-\d{2}-\d{2}", ip.get("release", "")):
                release[m.group(1)] = ip["release"][:10]
    objs = []
    for title, p in pages.items():
        if not title.startswith("Patch ") or "/" not in title:
            continue
        for po in find_templates(p["content"], "Patch object"):
            ver = (po.get("patch") or "").strip()
            if ver not in CHECKED_PATCHES:
                continue
            pages_ref = [v.strip() for k, v in po.items() if re.fullmatch(r"page\d+", k) and v.strip()]
            objs.append({"patch": ver, "release": release.get(ver), "pages": pages_ref,
                         "text": po.get("text", "").strip(), "notesPage": title})
    return objs, release


def mark_stale(all_entities, objs):
    for e in all_entities:
        title = e["source"]["page"]
        names = {title, e["name"], re.sub(r"^Template:\w+/", "", title)}
        ts = e["_ts"]
        hits = sorted({o["patch"] for o in objs if o["release"] and names & set(o["pages"])
                       and ts[:10] < o["release"]})
        if hits:
            e["possiblyStale"] = {"patches": hits,
                                  "reason": "source revision is older than the release of a patch whose "
                                            "balance notes mention this page"}


def get_path(obj, path):
    for part in path.split("."):
        obj = obj[int(part)] if part.isdigit() else obj.get(part)
        if obj is None:
            return None
    return obj


def run_checks(tables):
    results = []
    for table, key, path, expected, page, quote in PATCH_CHECKS:
        rows = tables[table]
        if key.startswith("name:"):
            ent = next((r for r in rows if r["name"] == key[5:]), None)
        else:
            ent = next((r for r in rows if r["id"] == key), None)
        actual = get_path(ent, path) if ent else None
        results.append({"table": table, "entity": key, "field": path, "expected": expected,
                        "actual": actual, "ok": actual == expected, "patchNotes": page, "quote": quote})
    return results


# ---------------------------------------------------------------- output

def clean(e):
    return {k: v for k, v in e.items() if not k.startswith("_")}


def expected_fourcc():
    """fourcc ids used in reference build orders and hero lists (testdata/expected)."""
    used = {}
    for path in sorted(glob.glob(os.path.join(EXPECTED, "*.json"))):
        with open(path, encoding="utf-8") as f:
            d = json.load(f)
        for p in d.get("players", []):
            for b in p.get("buildOrderFirst15", []):
                used.setdefault((b["type"], b["id"]), set()).add(os.path.basename(path))
            for h in p.get("heroes", []):
                used.setdefault(("hero", h["id"]), set()).add(os.path.basename(path))
    return used


def write_json(name, payload):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main():
    pages, fetched_at = load_pages()
    maps = load_mappings()
    src = Sources(pages)
    spellcards = parse_spellcards(pages)
    units, buildings = parse_units_buildings(pages, src)
    costs = parse_hero_costs(pages, src)
    heroes = parse_heroes(pages, src, maps, spellcards, costs)
    upgrades = parse_upgrades(pages, src, spellcards)
    items = parse_items(pages, src)
    link_all(units, buildings, heroes, upgrades, items, maps)

    tables = {"units": units, "buildings": buildings, "heroes": heroes, "upgrades": upgrades, "items": items}
    for rows in tables.values():
        for e in rows:
            e["_ts"] = pages[e["source"]["page"]]["timestamp"]
    objs, release = patch_objects(pages)
    for t in ("Patch 3.0.0", "Patch 3.0.1", "Patch 2.0.4"):
        src.ref(t)
    mark_stale([e for rows in tables.values() for e in rows], objs)
    checks = run_checks(tables)

    # Duplicate ids inside a table (first occurrence wins).
    dups = []
    for name, rows in tables.items():
        seen, kept = {}, []
        for e in rows:
            if e["id"] and e["id"] in seen:
                dups.append((name, e["id"], seen[e["id"]], e["source"]["page"]))
                continue
            if e["id"]:
                seen[e["id"]] = e["source"]["page"]
            kept.append(e)
        rows[:] = sorted(kept, key=lambda e: (e["race"] or "~", e["id"] or "~", e["name"]))

    os.makedirs(OUT, exist_ok=True)
    common_meta = {"schemaVersion": SCHEMA_VERSION, "patch": PATCH,
                   "source": "Liquipedia Warcraft Wiki, https://liquipedia.net/warcraft/",
                   "license": "CC BY-SA 3.0, https://creativecommons.org/licenses/by-sa/3.0/"}
    for name, rows in tables.items():
        write_json(f"{name}.json", {**common_meta, "count": len(rows), "entities": [clean(e) for e in rows]})

    fourcc = expected_fourcc()
    all_ids = {e["id"] for rows in tables.values() for e in rows if e["id"]}
    hero_abil = {a["id"] for h in heroes for a in h["abilities"]}
    coverage = []
    for (kind, fid), files in sorted(fourcc.items()):
        covered = fid in (hero_abil if kind == "skill" else all_ids)
        coverage.append({"kind": kind, "id": fid, "covered": covered, "replays": len(files)})

    write_json("meta.json", {
        **common_meta,
        "patchNote": ("Liquipedia has no race balance changes for 3.0.0/3.0.1 (only the new tavern hero "
                      "Forsaken Paladin and its 3.0.1 tuning). Unit/building/upgrade/item numbers are the "
                      "2.0.4 balance, which is unchanged in 3.0.x."),
        "patchReleases": release,
        "fetchedAt": fetched_at,
        "generator": "tools/gamedata/fetch.py + tools/gamedata/build.py",
        "attribution": ("Data derived from Liquipedia Warcraft Wiki pages listed in `pages` "
                        "(contributors: see each page history), CC BY-SA 3.0. Modified: extracted into JSON. "
                        "fourcc ids partly from w3gjs mappings.ts (MIT)."),
        "counts": {k: len(v) for k, v in tables.items()},
        "patchChecks": checks,
        "duplicateIds": [{"table": t, "id": i, "kept": a, "dropped": b} for t, i, a, b in dups],
        "buildOrderCoverage": coverage,
        "pages": sorted(src.used.values(), key=lambda x: x["title"]),
    })
    write_unmatched(tables, maps, coverage, checks, dups)
    print(json.dumps({k: len(v) for k, v in tables.items()}), file=sys.stderr)
    print("checks failed:", [c for c in checks if not c["ok"]], file=sys.stderr)


def write_unmatched(tables, maps, coverage, checks, dups):
    lines = ["# Сопоставление справочника с fourcc ID", "",
             "Сгенерировано `tools/gamedata/build.py`. Не править руками.", ""]

    def mapping_section(title, table, mapping):
        ids = {e["id"] for e in tables[table] if e["id"]}
        missing = sorted((k, v) for k, v in mapping.items() if k not in ids)
        total = len(mapping)
        lines.append(f"### {title}: {total - len(missing)} из {total} ID w3gjs есть в справочнике")
        lines.append("")
        if missing:
            lines.extend(f"- `{k}` {v}" for k, v in missing)
        else:
            lines.append("- всё сопоставлено")
        lines.append("")
        return total, total - len(missing)

    lines += ["## 1. ID из w3gjs mappings.ts, которых нет в справочнике", ""]
    tot = got = tot_ni = got_ni = 0
    for title, table, key in (("Юниты", "units", "units"), ("Здания", "buildings", "buildings"),
                              ("Герои", "heroes", "heroes"), ("Апгрейды", "upgrades", "upgrades"),
                              ("Предметы", "items", "items")):
        a, b = mapping_section(title, table, maps[key])
        tot, got = tot + a, got + b
        if table != "items":
            tot_ni, got_ni = tot_ni + a, got_ni + b
    lines.insert(4, f"Итого сопоставлено {got} из {tot} ID w3gjs ({100 * got / tot:.1f}%). "
                    f"Без предметов: {got_ni} из {tot_ni} ({100 * got_ni / tot_ni:.1f}%). "
                    "Большая часть несопоставленных предметов — кампанейские и дроп с крипов, "
                    "страниц на Liquipedia для них нет (проверено запросом по точному названию).")
    lines.insert(5, "")

    lines += ["Пояснения по апгрейдам: `Reht` Hippogryph Taming убран в патче 1.30.0 "
              "(Patch 1.30.0/night elf: «researched by default»). `Roch` Chaos — карточки на Liquipedia нет.", ""]
    lines += ["## 2. Сущности Liquipedia без fourcc ID", "",
              "Нет ID в инфобоксе и не нашлось совпадения по имени в w3gjs. В JSON у них `id: null`.", ""]
    for table in ("units", "buildings", "heroes", "upgrades", "items"):
        rows = [e for e in tables[table] if not e["id"]]
        lines.append(f"### {table}: {len(rows)}")
        lines.append("")
        lines += [f"- {e['name']} — [{e['source']['page']}]({e['source']['url']})" for e in rows] or ["- нет"]
        lines.append("")

    lines += ["## 3. ID с Liquipedia, которых нет в w3gjs", "",
              "ID взят из инфобокса Liquipedia. В основном крипы, нейтральные и вызываемые юниты.", ""]
    for table, key in (("units", "units"), ("buildings", "buildings")):
        extra = sorted(e["id"] for e in tables[table] if e["id"] and e["id"] not in maps[key])
        lines.append(f"- {table} ({len(extra)}): " + ", ".join(f"`{x}`" for x in extra))
    lines.append("")

    lines += ["## 4. Forsaken Paladin (`Npal`, герой таверны патча 3.0)", ""]
    npal = next((h for h in tables["heroes"] if h["name"] == "Forsaken Paladin"), None)
    in_map = "Npal" in maps["heroes"]
    lines.append(f"- В w3gjs mappings.ts: {'есть (через abilityToHero: AHcr, AHpa, AHcl)' if in_map else 'нет'}.")
    if npal:
        abil = ", ".join(f"`{a['id']}` {a['name']}" for a in npal["abilities"])
        lines.append(f"- На Liquipedia: страница есть, id в справочнике `{npal['id']}` ({npal['idSource']}). "
                     f"Способности: {abil}.")
        missing = [a["id"] for a in npal["abilities"] if a["id"] not in maps["abilityToHero"]]
        if missing:
            lines.append(f"- Способностей нет в w3gjs abilityToHero: {', '.join('`'+m+'`' for m in missing)}.")
    lines.append("")

    lines += ["## 5. Покрытие билд-ордеров из testdata/expected", ""]
    miss = [c for c in coverage if not c["covered"]]
    lines.append(f"Уникальных fourcc в `buildOrderFirst15` и `heroes`: {len(coverage)}, "
                 f"покрыто {len(coverage) - len(miss)}.")
    lines.append("")
    lines += [f"- не покрыт: `{c['id']}` ({c['kind']}, реплеев: {c['replays']})" for c in miss] or ["- всё покрыто"]
    lines.append("")

    lines += ["## 6. Сверка с патчноутами Liquipedia", "",
              "Значение после изменения из текста патчноута сравнивается со значением в инфобоксе/карточке.", ""]
    ok = sum(c["ok"] for c in checks)
    lines.append(f"Совпало {ok} из {len(checks)}.")
    lines.append("")
    lines += [f"- расхождение: {c['table']} `{c['entity']}` {c['field']}: ожидается {c['expected']} "
              f"({c['patchNotes']}), в данных {c['actual']}" for c in checks if not c["ok"]]
    stale = [e for rows in tables.values() for e in rows if e.get("possiblyStale")]
    lines += ["", "### Возможно устаревшие страницы", "",
              "Ревизия страницы старше релиза патча, в патчноутах которого она упомянута. "
              "Поле `possiblyStale` в JSON.", ""]
    lines += [f"- {e['type']} `{e['id']}` {e['name']}: патчи {', '.join(e['possiblyStale']['patches'])}, "
              f"ревизия {e['source']['revid']}" for e in stale] or ["- нет"]
    if dups:
        lines += ["", "### Повторяющиеся ID (оставлена первая страница)", ""]
        lines += [f"- {t} `{i}`: {a} / {b}" for t, i, a, b in dups]
    lines.append("")
    with open(os.path.join(OUT, "UNMATCHED.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


if __name__ == "__main__":
    main()
