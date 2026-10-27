"""Small wikitext helpers: template extraction and value parsing (stdlib only)."""
import re

COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
LINK_RE = re.compile(r"\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]")


def strip_comments(text):
    return COMMENT_RE.sub("", text)


def find_templates(text, name):
    """Return a list of param dicts for every {{name ...}} call in text.

    `name` is matched case-insensitively and exactly (so 'SpellCard' does not
    match 'SpellCard/AHbz'). Nested templates and links are kept verbatim
    inside values.
    """
    result = []
    pattern = re.compile(r"\{\{\s*" + re.escape(name) + r"\s*(?=[|}\n])", re.I)
    for m in pattern.finditer(text):
        body, _ = _scan_template(text, m.end())
        if body is not None:
            result.append(_split_params(body))
    return result


def _scan_template(text, pos):
    """Scan from pos (just after the template name) to the matching '}}'."""
    depth_t, depth_l, i, start = 1, 0, pos, pos
    while i < len(text):
        two = text[i:i + 2]
        if two == "{{":
            depth_t += 1
            i += 2
        elif two == "}}":
            depth_t -= 1
            if depth_t == 0:
                return text[start:i], i + 2
            i += 2
        elif two == "[[":
            depth_l += 1
            i += 2
        elif two == "]]":
            depth_l = max(0, depth_l - 1)
            i += 2
        else:
            i += 1
    return None, len(text)


def _split_params(body):
    parts, depth_t, depth_l, cur = [], 0, 0, []
    i = 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "}}", "[[", "]]"):
            if two == "{{":
                depth_t += 1
            elif two == "}}":
                depth_t -= 1
            elif two == "[[":
                depth_l += 1
            else:
                depth_l -= 1
            cur.append(two)
            i += 2
            continue
        ch = body[i]
        if ch == "|" and depth_t == 0 and depth_l == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
        i += 1
    parts.append("".join(cur))
    params = {}
    for p in parts[1:]:  # parts[0] is whatever follows the name before the first '|'
        if "=" in p:
            k, v = p.split("=", 1)
            params[k.strip()] = v.strip()
        elif p.strip():
            params.setdefault(p.strip(), "")
    return params


def num(value):
    """Parse a plain number; None for empty or non-numeric values."""
    if value is None:
        return None
    v = strip_comments(str(value)).strip().replace(",", "")
    if not re.fullmatch(r"-?\d+(\.\d+)?", v):
        return None
    f = float(v)
    return int(f) if f.is_integer() else f


def links(value):
    """Return link targets from a value; plain text if it has no links."""
    if not value:
        return []
    value = strip_comments(value)
    found = []
    for m in LINK_RE.finditer(value):
        target = m.group(1).strip()
        label = re.search(r"\|([^\]]*)\]\]$", m.group(0))
        # [[Tier|Tier 2]] carries the meaning in the label.
        found.append(label.group(1).strip() if target == "Tier" and label else target)
    if found:
        return found
    items = [x.strip(" *\t") for x in re.split(r"\n|,", value)]
    return [x for x in items if x and not x.startswith("{{")]


def bullet_values(value):
    if not value:
        return []
    return [x.strip(" *\t") for x in strip_comments(value).split("\n") if x.strip(" *\t")]


def norm_name(name):
    """Normalized key for name matching."""
    n = name.lower().replace("’", "'")
    n = re.sub(r"\s*\((human|orc|undead|night elf|nightelf|level \d)\)\s*$", "", n)
    return re.sub(r"[^a-z0-9+]", "", n)
