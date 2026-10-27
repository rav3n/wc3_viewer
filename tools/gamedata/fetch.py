"""Download raw wikitext from Liquipedia Warcraft into tools/gamedata/cache/.

Only action=query + prop=revisions is used (cheap for the API).
Output: cache/pages.json -- {title: {pageid, revid, timestamp, content, categories}}.
Run: python3 -I tools/gamedata/fetch.py [--offline]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lpapi  # noqa: E402

CATEGORIES = [
    "Category:Units",
    "Category:Buildings",
    "Category:Heroes",
    "Category:Items",
    "Category:Upgrades",
    "Category:Human Balance Patches",
    "Category:Orc Balance Patches",
    "Category:Night Elf Balance Patches",
    "Category:Undead Balance Patches",
    "Category:Neutral Balance Patches",
    "Category:Item Balance Patches",
]
TEMPLATE_PREFIXES = ["UpgradeCard/", "SpellCard/"]
EXTRA_PAGES = ["Hero", "Patch 3.0.0", "Patch 3.0.1", "Patch 2.0.4", "Patch 2.0.3", "Patch 2.0.3.23101",
               "Patch 2.0.2", "Patch 2.0.2.22796"]

REV_PARAMS = {
    "action": "query",
    "prop": "revisions|categories",
    "rvprop": "ids|timestamp|content",
    "rvslots": "main",
    "cllimit": "max",
}


def _collect(params, out, offline):
    cont = {}
    while True:
        data = lpapi.query({**params, **cont}, offline=offline)
        for p in data.get("query", {}).get("pages", []):
            if p.get("missing") or "revisions" not in p:
                continue
            title = p["title"]
            rev = p["revisions"][0]
            entry = out.setdefault(title, {
                "pageid": p["pageid"], "ns": p["ns"], "revid": rev["revid"],
                "timestamp": rev["timestamp"], "content": rev["slots"]["main"]["content"],
                "categories": [],
            })
            for c in p.get("categories", []):
                if c["title"] not in entry["categories"]:
                    entry["categories"].append(c["title"])
        if "continue" not in data:
            return
        cont = data["continue"]


def _subcategories(cat, offline):
    data = lpapi.query({"action": "query", "list": "categorymembers", "cmtitle": cat,
                        "cmtype": "subcat", "cmlimit": "max"}, offline=offline)
    return [m["title"] for m in data["query"]["categorymembers"]]


def main():
    offline = "--offline" in sys.argv
    pages = {}
    cats = list(CATEGORIES)
    for top in ("Category:Units", "Category:Buildings", "Category:Heroes", "Category:Items"):
        for sub in _subcategories(top, offline):
            if sub not in cats:
                cats.append(sub)
    for cat in cats:
        print("category:", cat, file=sys.stderr)
        _collect({**REV_PARAMS, "generator": "categorymembers", "gcmtitle": cat,
                  "gcmlimit": "50", "gcmnamespace": "0|10"}, pages, offline)
    for prefix in TEMPLATE_PREFIXES:
        print("templates:", prefix, file=sys.stderr)
        _collect({**REV_PARAMS, "generator": "allpages", "gapprefix": prefix,
                  "gapnamespace": "10", "gaplimit": "50"}, pages, offline)
    _collect({**REV_PARAMS, "titles": "|".join(EXTRA_PAGES)}, pages, offline)
    # Probe w3gjs names that are not among the fetched titles (other categories, redirects).
    import build  # noqa: E402  (shares the mappings parser)
    from wikitext import norm_name  # noqa: E402
    maps = build.load_mappings()
    known = {norm_name(t) for t in pages}
    wanted = sorted({v.strip() for k in ("units", "buildings", "items", "upgrades")
                     for v in maps[k].values() if norm_name(v) not in known and "/" not in v})
    for i in range(0, len(wanted), 50):
        _collect({**REV_PARAMS, "titles": "|".join(wanted[i:i + 50]), "redirects": "1"}, pages, offline)
    out = os.path.join(lpapi.CACHE_DIR, "pages.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(pages, f, ensure_ascii=False, indent=1, sort_keys=True)
    print(f"{len(pages)} pages -> {out}", file=sys.stderr)


if __name__ == "__main__":
    main()
