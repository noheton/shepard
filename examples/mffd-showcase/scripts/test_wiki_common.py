#!/usr/bin/env python3
"""Unit tests for the pure parser functions in wiki_common.py.

These cover the load-bearing transforms (author extraction, dated-block
splitting, acronym mining, idempotency markers) without touching the live
Shepard instance. Run: python3 -m pytest test_wiki_common.py -q
"""
from __future__ import annotations

import wiki_common as wc
from wiki_common import WikiPage
from pathlib import Path


def _page(html: str, name: str = "x_123456.html", title: str = "X") -> WikiPage:
    raw, user, disp = wc.extract_author(html)
    return WikiPage(
        path=Path(name), page_id=wc.page_id_from_filename(name),
        title=title, author_raw=raw, author_username=user, author_display=disp,
        html=html, dated_count=0, klass="journal",
    )


# ── page_id_from_filename ────────────────────────────────────────────────────

def test_page_id_from_slug_filename():
    assert wc.page_id_from_filename("Legetagebuch_485279251.html") == "485279251"


def test_page_id_from_numeric_filename():
    assert wc.page_id_from_filename("266713006.html") == "266713006"


# ── extract_author ───────────────────────────────────────────────────────────

def test_extract_author_unknown_user():
    html = "Created by <span class='author'> Unknown User (dede_di)</span>, last updated"
    raw, user, disp = wc.extract_author(html)
    assert user == "dede_di"
    assert "dede_di" in raw


def test_extract_author_real_name():
    html = "Created by <span class='author'> Eckardt, Mona</span> on 1. Jan 2023"
    raw, user, disp = wc.extract_author(html)
    assert user == "eckardt-mona"
    assert disp == "Eckardt, Mona"


def test_extract_author_falls_back_to_anonymous():
    raw, user, disp = wc.extract_author("<html>no author here</html>")
    assert user == "wiki-import-anonymous"


def test_extract_author_editor_fallback():
    html = "last updated by <span class='editor'> Mayer, Monika</span> on 20. January 2023"
    raw, user, disp = wc.extract_author(html)
    assert user == "mayer-monika"


# ── html_to_text ─────────────────────────────────────────────────────────────

def test_html_to_text_preserves_list_items():
    txt = wc.html_to_text("<ul><li>one</li><li>two</li></ul>")
    assert "one" in txt and "two" in txt
    assert "- one" in txt


def test_html_to_text_drops_images():
    txt = wc.html_to_text('<p>before<img src="x.jpg"/>after</p>')
    assert "[image]" in txt
    assert "before" in txt and "after" in txt


def test_html_to_text_unescapes_entities():
    assert "&" in wc.html_to_text("<p>A &amp; B</p>")


# ── split_dated_blocks ───────────────────────────────────────────────────────

def test_split_paragraph_headed_diary():
    html = (
        '<div id="main-content">'
        '<p><strong>07.12.2022 - Protokoll DD:</strong></p>'
        '<ul><li>started layup</li><li>fehlschnitt</li></ul>'
        '<p><strong>08.12.2022 - Protokoll DH:</strong></p>'
        '<ul><li>repaired hose</li></ul>'
        '</div>'
    )
    page = _page(html)
    blocks = wc.split_dated_blocks(page)
    assert len(blocks) == 2
    assert blocks[0].date_iso == "2022-12-07"
    assert blocks[1].date_iso == "2022-12-08"
    assert "started layup" in blocks[0].text
    assert "repaired hose" in blocks[1].text
    # indices are 0-based and sequential
    assert [b.index for b in blocks] == [0, 1]


def test_split_table_row_dated_log():
    html = (
        '<div id="main-content"><table><tbody>'
        '<tr><td>shift A</td><td>07.12.2022</td><td>4 plies</td></tr>'
        '<tr><td>shift B</td><td>08.12.2022</td><td>6 plies</td></tr>'
        '</tbody></table></div>'
    )
    page = _page(html)
    blocks = wc.split_dated_blocks(page)
    assert len(blocks) == 2
    assert blocks[0].date_iso == "2022-12-07"
    assert blocks[1].date_iso == "2022-12-08"


def test_split_invalid_date_ignored():
    html = '<div id="main-content"><p><strong>99.99.2022 - bad</strong> body</p></div>'
    page = _page(html)
    blocks = wc.split_dated_blocks(page)
    assert blocks == []


def test_split_iso_date_zero_pads():
    html = '<div id="main-content"><p>2.1.2023: kickoff</p><p>body line</p></div>'
    page = _page(html)
    blocks = wc.split_dated_blocks(page)
    assert len(blocks) == 1
    assert blocks[0].date_iso == "2023-01-02"


# ── classify_pages ───────────────────────────────────────────────────────────

def test_classify_journal_by_force_name(tmp_path):
    (tmp_path / "Legetagebuch_1.html").write_text("<title>MFFD : Legetagebuch</title>")
    (tmp_path / "Project-Plan_2.html").write_text("<title>MFFD : Project Plan</title>")
    (tmp_path / "AFP-Kopf_3.html").write_text("<title>MFFD : AFP Kopf</title>")
    pages = {p.path.name: p.klass for p in wc.classify_pages(tmp_path)}
    assert pages["Legetagebuch_1.html"] == "journal"
    assert pages["Project-Plan_2.html"] == "plan"
    assert pages["AFP-Kopf_3.html"] == "reference"


def test_classify_journal_by_date_count(tmp_path):
    body = "".join(f"<p>0{i}.01.2023 - log</p>" for i in range(1, 7))
    (tmp_path / "diary_9.html").write_text(f"<title>MFFD : Diary</title>{body}")
    pages = {p.path.name: p.klass for p in wc.classify_pages(tmp_path)}
    assert pages["diary_9.html"] == "journal"


# ── mine_acronyms ────────────────────────────────────────────────────────────

def test_mine_acronyms_finds_known_and_assigns_expansion():
    html = '<div id="main-content"><p>The AFP head uses TPS and FSD modules.</p></div>'
    page = _page(html, title="ref")
    acc = wc.mine_acronyms([page], wc.load_stopwords(None))
    assert "AFP" in acc and acc["AFP"]["term"] == "Automated Fibre Placement"
    assert "TPS" in acc and "FSD" in acc
    assert acc["AFP"]["confidence"] == "high"


def test_mine_acronyms_respects_stopwords():
    html = '<div id="main-content"><p>See HTTP and URL and AFP.</p></div>'
    acc = wc.mine_acronyms([_page(html)], wc.load_stopwords(None))
    assert "HTTP" not in acc
    assert "URL" not in acc
    assert "AFP" in acc


def test_mine_acronyms_unknown_low_confidence():
    html = '<div id="main-content"><p>The XQZ module is custom.</p></div>'
    acc = wc.mine_acronyms([_page(html)], wc.load_stopwords(None))
    assert "XQZ" in acc
    assert acc["XQZ"]["term"] is None
    assert acc["XQZ"]["confidence"] == "low"


# ── idempotency markers ──────────────────────────────────────────────────────

def test_marker_roundtrip():
    content = "before " + wc.journal_marker_comment("12345", 7) + " after"
    assert wc.content_has_marker(content, "12345", 7)
    assert not wc.content_has_marker(content, "12345", 8)
    assert not wc.content_has_marker(content, "99999", 7)


def test_marker_is_sanitizer_safe_css_property():
    # The marker must be a hidden <span style="...--wjk:..."> so jsoup's
    # basicWithImages Safelist preserves it (comments + data-* are stripped).
    marker = wc.journal_marker_comment("485279251", 3)
    assert "<span" in marker and "display:none" in marker
    assert "--wjk:485279251-3" in marker
    assert "<!--" not in marker          # no HTML comment (stripped by jsoup)
    assert "data-" not in marker          # no data-* attr (stripped by jsoup)


def test_marker_empty_content():
    assert not wc.content_has_marker("", "1", 0)
    assert not wc.content_has_marker(None, "1", 0)


def test_journal_idempotency_token_format():
    tok = wc.journal_idempotency_token("485279251", 3)
    assert tok == "wiki-journal-key:485279251:3"


def test_journal_marker_css_format():
    assert wc.journal_marker_css("485279251", 3) == "--wjk:485279251-3"
