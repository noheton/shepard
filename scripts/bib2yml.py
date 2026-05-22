#!/usr/bin/env python3
"""Convert ``docs/_data/references.bib`` to ``docs/_data/references.yml``.

This is the SSOT bridge between the canonical BibTeX bibliography (good for
Zotero / Mendeley import, downstream LaTeX tooling, etc.) and the YAML form
Jekyll renders on the public bibliography page.

The script is intentionally stdlib-only --- it runs on a stock GitHub Actions
runner with no extra ``pip install`` step. It implements a strict subset of
BibTeX parsing tailored to our bibliography (the entries are hand-curated,
clean, and use a small set of fields).

Invocation::

    python3 scripts/bib2yml.py

Behaviour:

* Reads ``docs/_data/references.bib`` (path relative to repo root).
* Writes ``docs/_data/references.yml``.
* Output is a dictionary keyed by citation key --- so future docs can
  reference an entry inline via ``{{ site.data.references.<key> }}``.
* Per-entry fields: ``type``, plus every captured BibTeX field (``title``,
  ``author``, ``year``, ``url``, ``doi``, ``note``, ``category`` ...).
* Bails loudly (non-zero exit) on any entry that cannot be parsed --- silent
  drops would make the bibliography rot invisibly.

The YAML emitted is a small, conservative subset (strings, ints, dicts) ---
no library required to read it; Jekyll's built-in YAML loader handles it.
"""

from __future__ import annotations

import re
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BIB_PATH = REPO_ROOT / "docs" / "_data" / "references.bib"
YML_PATH = REPO_ROOT / "docs" / "_data" / "references.yml"

ENTRY_RE = re.compile(r"@(\w+)\s*\{\s*([^,\s]+)\s*,", re.MULTILINE)


def _strip_outer_braces(value: str) -> str:
    """Remove one layer of matched ``{...}`` or ``"..."`` wrapping."""
    value = value.strip()
    if len(value) >= 2 and value[0] == "{" and value[-1] == "}":
        return value[1:-1].strip()
    if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
        return value[1:-1].strip()
    return value


def _bibtex_to_text(value: str) -> str:
    """Render a few common BibTeX escapes into plain Unicode.

    Order matters --- accent macros run BEFORE the brace-stripping pass so
    e.g. ``\\c{c}`` resolves to ``ç`` before the braces would be eaten.
    """
    # Accent / special-character macros — pragmatic, not exhaustive.
    # Each entry: (raw-string regex, replacement). Run BEFORE brace removal.
    accent_pairs = [
        (r"\\textsuperscript\{2\}", "²"),
        (r"\\textsuperscript\{(\w)\}", r"\1"),
        (r"\\&", "&"),
        (r"\\#", "#"),
        (r"\\%", "%"),
        (r"\\_", "_"),
        (r"\\textendash", "–"),
        (r"\\textemdash", "—"),
        (r"\\textbf\{([^}]*)\}", r"\1"),
        (r"\\textit\{([^}]*)\}", r"\1"),
        (r"\\texttt\{([^}]*)\}", r"\1"),
        # Cedilla / caron / other braced accent macros.
        (r"\\c\{c\}", "ç"),
        (r"\\c\{C\}", "Ç"),
        (r"\\ss\b", "ß"),
        # Umlauts (double-quote accents) — both braced and bare forms.
        (r'\\"\{a\}|\\"a\b', "ä"),
        (r'\\"\{o\}|\\"o\b', "ö"),
        (r'\\"\{u\}|\\"u\b', "ü"),
        (r'\\"\{A\}|\\"A\b', "Ä"),
        (r'\\"\{O\}|\\"O\b', "Ö"),
        (r'\\"\{U\}|\\"U\b', "Ü"),
        # Acute accents.
        (r"\\'\{a\}|\\'a\b", "á"),
        (r"\\'\{e\}|\\'e\b", "é"),
        (r"\\'\{i\}|\\'i\b", "í"),
        (r"\\'\{o\}|\\'o\b", "ó"),
        (r"\\'\{u\}|\\'u\b", "ú"),
        (r"\\'\{A\}|\\'A\b", "Á"),
        (r"\\'\{E\}|\\'E\b", "É"),
        # Grave + circumflex (rare, but cheap).
        (r"\\`\{a\}|\\`a\b", "à"),
        (r"\\`\{e\}|\\`e\b", "è"),
        (r"\\\^\{a\}|\\\^a\b", "â"),
        (r"\\\^\{e\}|\\\^e\b", "ê"),
        (r"\\\^\{i\}|\\\^i\b", "î"),
        (r"\\\^\{o\}|\\\^o\b", "ô"),
    ]
    for pattern, repl in accent_pairs:
        value = re.sub(pattern, repl, value)
    # Drop remaining inner braces (capitalisation locks etc).
    value = value.replace("{", "").replace("}", "")
    # Collapse internal whitespace.
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def _parse_fields(body: str) -> "OrderedDict[str, str]":
    """Parse the comma-separated ``key = value`` fields inside an entry body.

    Handles nested braces and quoted strings; aborts on malformed input.
    """
    fields: "OrderedDict[str, str]" = OrderedDict()
    i = 0
    n = len(body)
    while i < n:
        # Skip whitespace and commas between fields.
        while i < n and body[i] in " \t\r\n,":
            i += 1
        if i >= n:
            break
        # Read key.
        key_start = i
        while i < n and (body[i].isalnum() or body[i] == "_" or body[i] == "-"):
            i += 1
        key = body[key_start:i].strip().lower()
        if not key:
            break
        # Expect '='.
        while i < n and body[i] in " \t\r\n":
            i += 1
        if i >= n or body[i] != "=":
            raise ValueError(f"expected '=' after field key '{key}', got: {body[i:i+30]!r}")
        i += 1
        while i < n and body[i] in " \t\r\n":
            i += 1
        if i >= n:
            raise ValueError(f"unexpected end of entry after field '{key}'")
        # Read value: brace-delimited, quote-delimited, or bare.
        if body[i] == "{":
            depth = 0
            val_start = i
            while i < n:
                ch = body[i]
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                i += 1
            value = body[val_start:i]
        elif body[i] == '"':
            val_start = i
            i += 1
            while i < n and body[i] != '"':
                if body[i] == "\\" and i + 1 < n:
                    i += 2
                else:
                    i += 1
            if i >= n:
                raise ValueError(f"unterminated quoted value for field '{key}'")
            i += 1  # past closing quote
            value = body[val_start:i]
        else:
            # Bare value — runs until next comma at top level.
            val_start = i
            while i < n and body[i] not in ",":
                i += 1
            value = body[val_start:i]
        value = _bibtex_to_text(_strip_outer_braces(value))
        fields[key] = value
    return fields


def parse_bib(text: str) -> "OrderedDict[str, dict]":
    """Parse a BibTeX file into ``{key: {type, ...fields}}``."""
    entries: "OrderedDict[str, dict]" = OrderedDict()
    # Strip line comments (any line that begins with %).
    text = "\n".join(
        line for line in text.splitlines() if not line.lstrip().startswith("%")
    )
    # Walk entries via the @type{key, pattern.
    pos = 0
    while True:
        m = ENTRY_RE.search(text, pos)
        if not m:
            break
        entry_type = m.group(1).lower()
        entry_key = m.group(2).strip()
        # Find matching closing brace for the entry body.
        body_start = m.end()
        depth = 1
        i = body_start
        while i < len(text) and depth > 0:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        if depth != 0:
            raise ValueError(f"unterminated entry '{entry_key}'")
        body = text[body_start : i - 1]
        try:
            fields = _parse_fields(body)
        except Exception as exc:
            raise ValueError(f"failed to parse entry '{entry_key}': {exc}") from exc
        if entry_key in entries:
            raise ValueError(f"duplicate citation key: {entry_key!r}")
        # ``entry_type`` is the BibTeX entry kind (techreport / misc / article / ...).
        # We use the YAML field ``entry_type`` to avoid colliding with the
        # optional ``type`` field on @techreport (which carries the
        # human-readable type string like "W3C Recommendation").
        entry: dict = {"entry_type": entry_type}
        entry.update(fields)
        entries[entry_key] = entry
        pos = i
    return entries


def _yaml_quote(value: str) -> str:
    """Emit a YAML-safe double-quoted scalar."""
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def emit_yaml(entries: "OrderedDict[str, dict]") -> str:
    """Render the entries dict as a small, conservative YAML document.

    Output shape::

        welzmueller2024_pluto:
          type: misc
          title: "Research Data Management..."
          ...
    """
    lines: list[str] = []
    lines.append("# Auto-generated from docs/_data/references.bib by scripts/bib2yml.py.")
    lines.append("# DO NOT edit this file by hand --- edit the .bib and re-run the script.")
    lines.append("# CI re-generates this file before every Jekyll build (see pages.yml).")
    lines.append("")
    for key, entry in entries.items():
        lines.append(f"{key}:")
        for field, value in entry.items():
            if isinstance(value, int):
                lines.append(f"  {field}: {value}")
            else:
                lines.append(f"  {field}: {_yaml_quote(str(value))}")
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    if not BIB_PATH.exists():
        print(f"error: {BIB_PATH} not found", file=sys.stderr)
        return 2
    text = BIB_PATH.read_text(encoding="utf-8")
    try:
        entries = parse_bib(text)
    except ValueError as exc:
        print(f"error parsing {BIB_PATH.name}: {exc}", file=sys.stderr)
        return 1
    if not entries:
        print(f"error: no entries parsed from {BIB_PATH.name}", file=sys.stderr)
        return 1
    yaml_text = emit_yaml(entries)
    YML_PATH.write_text(yaml_text, encoding="utf-8")
    print(f"bib2yml: wrote {len(entries)} entries to {YML_PATH.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
