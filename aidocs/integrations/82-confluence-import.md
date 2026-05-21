# 82 — Confluence Data Center Space Export → Shepard Import

**Status:** design
**Audience:** contributors, operators, researchers migrating from Confluence wikis
**Depends on:** IMP1 (import plan-seal), POST /v2/import/jobs (IMP1 execute, pending)

---

## Motivation

Teams that maintained lab notebooks, process logs, and data collection notes in
Confluence before adopting Shepard have years of institutional knowledge locked in
wiki pages. A Confluence → Shepard import brings that content into the provenance
graph as LabJournalEntries and FileReferences — making it searchable, linkable to
DataObjects, and part of the traceable research record.

This is the primary migration path for institutes that already have Confluences
running for documentation and want Shepard as the data+journal layer going forward.

---

## Confluence DC export format

Confluence Data Center (and Server) produces two export types from Space Settings →
Export Space:

| Type | Format | Contents |
|---|---|---|
| **HTML** | ZIP → `index.html` + per-page HTML files + `attachments/` | Human-readable; easiest to parse with html2text |
| **XML (Full)** | ZIP → `entities.xml` + `attachments/` | Machine-readable; preserves hierarchy + metadata; harder to parse |

Recommendation: **HTML export** is the pragmatic choice for the importer. It is
unambiguous, works on any Confluence version ≥ 6.x, and `html2text` (or BeautifulSoup)
converts it to clean markdown for LabJournalEntry content.

XML export is richer (preserves page history, inline comments, macro metadata) but
the Confluence storage format (XHTML + custom macro XML) requires a purpose-built
parser and is version-sensitive. Defer XML parsing to a later iteration.

---

## Content mapping

| Confluence entity | Shepard target | Notes |
|---|---|---|
| Space | Collection | One Collection per space; name from space title |
| Top-level page | DataObject (root) | parent_id = None |
| Nested page | DataObject (child) | parent_id = parent page DataObject |
| Page content (HTML) | LabJournalEntry | HTML → markdown via html2text; title = page title |
| Attachment (PDF, XLSX, image, …) | FileReference | Attached to the page's DataObject |
| Page labels | Annotations | key = "confluence_label", value = label text |
| Page metadata (author, created, modified) | DataObject attributes | `confluence_author`, `confluence_created`, `confluence_modified` |
| Blog post | DataObject (root, tagged `confluence_type=blogpost`) + LabJournalEntry | Blogs have no hierarchy — land as flat root DOs |
| Confluence inline tables | StructuredDataReference (JSON) | Only if `--import-tables` flag is set; otherwise stays in markdown |

---

## What cannot be imported (flagged, not silently dropped)

| Confluence content | Why not importable | Shepard action |
|---|---|---|
| Jira issue macro (`{jira:}`) | Jira is an external system; issue data not in export | URIReference: the Jira URL |
| Embedded external images | Not in export ZIP | Markdown `![alt](original_url)` preserved; URL flagged in report |
| Confluence chart macros | Generated client-side from data; no raw data in export | Annotation: `confluence_macro=chart` on the DataObject |
| Draw.io / Gliffy diagrams | Binary; exportable only as PNG/SVG from Confluence | The PNG/SVG attachment is importable as a FileReference |
| `{include:}` macros | Transclusion of other pages | Replaced with a link to the target page's DataObject |
| User profile links | `@mention` → user emails; may not match Shepard users | Annotation: `confluence_mention=<username>` |
| Page history / versions | Only current revision in HTML export | Historical versions: defer to XML export path |
| Space permissions | Not in export | Import applies default Collection permissions |
| Confluence comments | Inline comments not in HTML export | Defer to XML export path |

All non-importable content is logged in `import-report.md` alongside the import
so the operator can decide what to do. **Nothing is silently dropped.**

---

## Data sources linked in wiki pages

This is the high-value case: a wiki page says "see measurement run 2024-06-02 in
the lab NAS at `\\nas01\hot-fire\2024-06\run-002\`" or embeds a chart from a
LabVIEW export.

The importer extracts all links and file references from page content and classifies them:

| Link type | Detection | Shepard action |
|---|---|---|
| File server path (`\\nas`, `/mnt/`, `Z:\`) | Regex: UNC + Unix absolute paths | URIReference with scheme `file://` + annotation `unarchived_datasource=true` |
| HTTP/HTTPS links | HTML `<a href>` | URIReference; annotated `confluence_link=true` |
| Confluence attachment | HTML `<a href="attachments/…">` | FileReference (included in import) |
| Confluence page link | HTML `<a href="…spaceKey/pageTitle">` | Internal link → resolved to DataObject appId after import |
| Data table in page body | `<table>` with numeric cells | Optionally imported as StructuredDataReference |

The `unarchived_datasource` annotation marks data that exists somewhere but is not
yet in Shepard — exactly the gap the importer should surface, not hide.

---

## Import pipeline

```
confluence-export.zip
  └── unzip
        ├── spaceManifest.xml  (page ID → filename map)
        ├── Page-Title-1.html
        ├── Page-Title-2.html
        ├── ...
        └── attachments/
              ├── <pageId>/1/attachment-name.pdf
              └── ...

Step 1: parse-space.py  →  intermediate JSON manifest
          (DataObject tree + LabJournalEntry HTML + attachment list)

Step 2: convert-content.py  →  HTML → markdown (html2text)
          + extract links → URIReferences

Step 3: generate-import-manifest.py  →  ImportManifestIO JSON
          (the IMP1 format: DataObjects + containers + references)

Step 4: POST /v2/import/validate  →  check for conflicts
Step 5: POST /v2/import/jobs      →  execute (pending endpoint IMP-1)
```

---

## CLI interface (`examples/_tools/import-confluence.py`)

```
python import-confluence.py \
    --zip    confluence-space-export.zip \
    --host   https://shepard.nuclide.systems \
    --apikey YOUR-KEY \
    [--collection-id  <existing-id>   # add to existing; omit to create new]
    [--dry-run]                        # validate only, no writes
    [--import-tables]                  # convert HTML tables to StructuredDataRefs
    [--max-attachment-mb 50]           # skip attachments larger than N MB
    [--report import-report.md]        # full report of what was/wasn't imported
```

### Phase 1: parse + dry-run (`analyze-confluence-export.py`)

Before running the full import, a separate discovery script prints what's in the
export without making any changes:

```
python analyze-confluence-export.py --zip confluence-space-export.zip
```

Output:
- Page count + depth distribution
- Attachment count + extension catalog
- External link count + domain breakdown (flag `\\nas` paths separately)
- Confluence macro catalog (count of `{jira:}`, `{chart}`, `{include:}` etc.)
- Estimated import size (DataObjects × LabJournalEntries × FileReferences)
- Import feasibility verdict per content type

---

## Implementation plan

| Step | Artefact | Effort | Notes |
|---|---|---|---|
| CF1a | `analyze-confluence-export.py` — discovery script | S | Stdlib only; HTML parsing with html.parser |
| CF1b | `import-confluence.py` — dry-run mode (`--validate` only) | M | Generates ImportManifestIO; calls `POST /v2/import/validate` |
| CF1c | `import-confluence.py` — execute mode | S | Calls `POST /v2/import/jobs` (gated on IMP-1 execute endpoint) |
| CF1d | URIReference extraction for linked data sources | S | Part of CF1b |
| CF2a | XML export path (page history, inline comments) | L | Complex parser; defer until demand |
| CF2b | Confluence → Shepard user mapping file | S | Optional CSV: `confluence_user,shepard_orcid` |
| CF2c | Re-link Confluence internal page links after import | M | Post-import pass: find `confluence_link` URIRefs → update to DataObject appId |

**Minimum viable import** = CF1a + CF1b + CF1c — discovers what's there, validates,
then executes. The rest is refinement.

---

## Security and data hygiene

- Export ZIPs may contain PII (author names, email addresses in page metadata).
  The importer stores these as DataObject attributes; inform users that Confluence
  author names will appear in Shepard.
- Attachments may contain confidential files. The importer respects `--max-attachment-mb`
  to limit accidental ingestion of large binaries.
- `\\nas` file paths and `http://internal.*` links should be flagged in the report
  but never followed — the importer is offline-only relative to external data sources.

---

## Related

- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI
- `aidocs/platform/30-mcp-plugin-design.md` — MCP import tools
- IMP1 (`aidocs/platform/importer`) — the import plan-seal pattern
- `examples/_tools/analyze-confluence-export.py` — discovery script (CF1a)
- `examples/_tools/import-confluence.py` — import script (CF1b+c)
