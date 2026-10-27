"""Minimal Liquipedia MediaWiki API client (stdlib only).

Follows the Liquipedia API Terms of Use:
- custom User-Agent, gzip encoding;
- at most 1 request per 2 s (action=parse is never used here);
- every raw response is cached on disk and never fetched twice.
"""
import gzip
import hashlib
import json
import os
import time
import urllib.parse
import urllib.request

API_URL = "https://liquipedia.net/warcraft/api.php"
USER_AGENT = "wc3-viewer-gamedata/0.1 (non-commercial replay analyzer)"
MIN_INTERVAL_SEC = 2.5
CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")

_last_request = 0.0


class ApiError(RuntimeError):
    pass


def _cache_path(params):
    key = urllib.parse.urlencode(sorted(params.items()))
    digest = hashlib.sha1(key.encode("utf-8")).hexdigest()[:16]
    return os.path.join(CACHE_DIR, f"{params.get('action', 'x')}-{digest}.json")


def query(params, offline=False):
    """Run an API request; return parsed JSON. Uses the disk cache first."""
    params = dict(params)
    params.setdefault("format", "json")
    params.setdefault("formatversion", "2")
    if params.get("action") == "parse":
        raise ApiError("action=parse is not used by this tool (rate limit 1/30 s)")
    path = _cache_path(params)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)["response"]
    if offline:
        raise ApiError(f"not in cache (offline mode): {params}")

    global _last_request
    wait = MIN_INTERVAL_SEC - (time.monotonic() - _last_request)
    if wait > 0:
        time.sleep(wait)
    url = API_URL + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Encoding": "gzip",
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            if resp.headers.get("Content-Encoding") == "gzip":
                raw = gzip.decompress(raw)
    except urllib.error.HTTPError as e:
        raise ApiError(f"HTTP {e.code} for {url}: {e.read()[:500]!r}") from e
    finally:
        _last_request = time.monotonic()
    data = json.loads(raw.decode("utf-8"))
    if "error" in data:
        raise ApiError(f"API error for {url}: {data['error']}")
    os.makedirs(CACHE_DIR, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"request": params, "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                   "response": data}, f, ensure_ascii=False)
    return data
