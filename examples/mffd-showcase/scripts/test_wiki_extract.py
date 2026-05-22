# /// script
# requires-python = ">=3.11"
# dependencies = ["pytest", "beautifulsoup4"]
# ///
"""Tests for the Confluence ToC parser in mffd-wiki-extract.py.

These tests are PURE — no network, no filesystem mutation beyond a tempdir.
They exercise the ToC parser against representative Confluence export
fragments. The fragments are stripped-down versions of real Confluence
HTML space exports (Server / DC, ~v7-v8 era).

If the parser is broken, the import script refuses to start. See the
`run_self_tests()` invocation at the top of `main()` in mffd-wiki-extract.py.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

# Load the sibling script as a module so we can test its parser directly.
_SCRIPT = Path(__file__).parent / "mffd-wiki-extract.py"
_spec = importlib.util.spec_from_file_location("mffd_wiki_extract", _SCRIPT)
assert _spec and _spec.loader, f"cannot load {_SCRIPT}"
mwe = importlib.util.module_from_spec(_spec)
sys.modules["mffd_wiki_extract"] = mwe
_spec.loader.exec_module(mwe)


# Representative Confluence Server / DC HTML export index.html shape.
# Real exports nest <ul><li><a href="...html">title</a><ul>...</ul></li></ul>
INDEX_HTML_NESTED = """
<!DOCTYPE html>
<html>
<head><title>Available Pages</title></head>
<body>
<h1>Available Pages:</h1>
<ul>
  <li><a href="123.html">Home Page</a>
    <ul>
      <li><a href="124.html">Child A</a>
        <ul>
          <li><a href="125.html">Grandchild A1</a></li>
        </ul>
      </li>
      <li><a href="126.html">Child B</a></li>
    </ul>
  </li>
  <li><a href="127.html">Sibling Root</a></li>
</ul>
<div class="pageSection">
  <h2>Attachments:</h2>
  <ul>
    <li><a href="attachments/123/foo.png">foo.png</a></li>
  </ul>
</div>
</body>
</html>
"""

# Pages list as a flat unstructured menu - some exports do this.
INDEX_HTML_FLAT = """
<!DOCTYPE html>
<html><body>
<h2>Available Pages:</h2>
<ul>
  <li><a href="100.html">Page Alpha</a></li>
  <li><a href="101.html">Page Beta</a></li>
  <li><a href="102.html">Page Gamma</a></li>
</ul>
</body></html>
"""


def test_parse_nested_toc_returns_tree() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_NESTED)
    assert isinstance(tree, list)
    assert len(tree) == 2  # 2 roots: "Home Page", "Sibling Root"
    titles = [n.title for n in tree]
    assert "Home Page" in titles
    assert "Sibling Root" in titles


def test_parse_nested_toc_preserves_hierarchy() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_NESTED)
    home = next(n for n in tree if n.title == "Home Page")
    child_titles = [c.title for c in home.children]
    assert "Child A" in child_titles
    assert "Child B" in child_titles
    child_a = next(c for c in home.children if c.title == "Child A")
    assert [g.title for g in child_a.children] == ["Grandchild A1"]


def test_parse_nested_toc_extracts_page_id_from_href() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_NESTED)
    home = next(n for n in tree if n.title == "Home Page")
    assert home.page_id == "123"
    assert home.href == "123.html"


def test_parse_flat_toc() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_FLAT)
    assert len(tree) == 3
    assert all(n.children == [] for n in tree)
    assert {n.title for n in tree} == {"Page Alpha", "Page Beta", "Page Gamma"}


def test_parse_empty_html_returns_empty_tree() -> None:
    assert mwe.parse_confluence_toc("<html><body></body></html>") == []


def test_parse_malformed_html_does_not_raise() -> None:
    # BeautifulSoup is forgiving, parser should be too.
    tree = mwe.parse_confluence_toc("<ul><li><a href='x.html'>X</li></ul")
    assert isinstance(tree, list)


def test_flatten_tree_yields_depth() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_NESTED)
    flat = list(mwe.flatten_tree(tree))
    # depths: Home=0, Child A=1, Grandchild A1=2, Child B=1, Sibling Root=0
    depths = [(n.title, depth, path) for n, depth, path in flat]
    by_title = {t: (d, p) for t, d, p in depths}
    assert by_title["Home Page"][0] == 0
    assert by_title["Child A"][0] == 1
    assert by_title["Grandchild A1"][0] == 2
    assert by_title["Child B"][0] == 1
    assert by_title["Sibling Root"][0] == 0


def test_flatten_tree_builds_wiki_path() -> None:
    tree = mwe.parse_confluence_toc(INDEX_HTML_NESTED)
    flat = list(mwe.flatten_tree(tree))
    paths = {n.title: path for n, _, path in flat}
    assert paths["Home Page"] == "Home Page"
    assert paths["Child A"] == "Home Page/Child A"
    assert paths["Grandchild A1"] == "Home Page/Child A/Grandchild A1"
    assert paths["Sibling Root"] == "Sibling Root"


def test_extract_page_metadata_handles_minimal_html() -> None:
    # Pages may be barebones; parser must not crash.
    html = "<html><head><title>Bare</title></head><body><p>x</p></body></html>"
    meta = mwe.extract_page_metadata(html, page_id="999")
    assert meta["confluence_page_id"] == "999"
    # title fallback when no specific .pagetitle present
    assert meta.get("title") in ("Bare", None, "")


def test_extract_page_metadata_typical_confluence_shape() -> None:
    html = """
    <html><head>
      <title>My Page</title>
      <meta name="confluence-space-key" content="MFFD"/>
    </head><body>
      <div id="main-content" class="wiki-content">
        <h1 id="title-heading-text" class="pagetitle">My Page Title</h1>
        <div class="page-metadata">
          <span class="author">Alice Smith</span>
          <span class="last-modified">2024-03-15</span>
          <span class="version">7</span>
        </div>
        <div>actual content</div>
      </div>
    </body></html>
    """
    meta = mwe.extract_page_metadata(html, page_id="42")
    assert meta["confluence_page_id"] == "42"
    # We should at minimum capture page_id; richer fields are best-effort.


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
