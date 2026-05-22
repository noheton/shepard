---
stage: deployed
last-stage-change: 2026-05-23
---

# 100 — Shepard UI annoyances (live captured)

**Status.** Living document — append-only. Twin to `aidocs/99-api-annoyances.md`.
**Snapshot date.** 2026-05-21.
**Audience.** Frontend / UX maintainers — paper-cuts to fix; downstream
users — workarounds to know about.
**Originating prompt.** User 2026-05-21: *"in this case when using ui.
note ui-annoyance document to improve user experience."*

Captured during Playwright-style validations of Shepard-from-the-frontend.
Each entry: what I tried, what bit, what UX principle is violated, the fix.

## Inclusion bar (per user 2026-05-21)

> *"Only note things human users would consider annoying or inconvenient
> from best-practice research."*

Each entry MUST satisfy all three:

1. **A real human would notice.** A researcher / operator / scientist
   would actually be slowed down or frustrated. Not "an automated client
   would prefer X" (that's the API-annoyances doc). Not "this is slightly
   less elegant than it could be."
2. **A documented principle is violated.** Cite the specific authority:
   one of Nielsen's 10 Usability Heuristics, a WCAG 2.2 success criterion,
   a Don Norman pattern from *Design of Everyday Things*, a Material 3 or
   Apple HIG guideline. If you can't name the principle, the entry
   doesn't belong.
3. **A real user could surface it in a 5-minute usability test.** If a
   stranger sitting at the screen would shrug and complete the task
   without comment, it doesn't go in this doc.

Stylistic preference without grounding → out. Theoretical accessibility
risk that no user has hit → out. "Could be better" without naming what
"better" looks like → out.

---

*(First validation pass — 2026-05-21, Claude Opus 4.7 via Playwright on
shepard.nuclide.systems. Screenshots in
`aidocs/agent-findings/validation-screenshots/`.)*

---

## U-01 — Collection sidebar overlays the main content on every desktop width

**Severity.** ★★★★ (4 / 5 — the Name column of the FileReference file
table is completely invisible at 1440 × 900 and at 1920 × 1080; still
partially clipped at 2560 × 1440. The user cannot match a row to a
filename without hovering somewhere off-screen or scrolling.)

**Page / element.** Every `/collections/{id}/...` page that uses
`layouts/collection.vue` — i.e. the collection detail page, every
DataObject detail page, every FileReference / TimeseriesReference /
StructuredDataReference detail page. The bug surfaces hardest on
detail pages whose primary content is a data table (the FileReference
page is the worst offender — see
`validation-screenshots/filebundle-list.png` and
`validation-screenshots/extra-fileref-1920.png`).

**What the user tried.** "I wanted to see which file in the bundle
was which — I clicked the FileBundle row and got a table of nine
oids and creation dates, but the filenames were gone."

**What bit.** `frontend/components/context/sidebar/CollectionSidebar.vue:178`
sets the sidebar to `position: fixed; width: 100%; max-width: inherit`
on desktop. That removes the sidebar from the layout flow. The
parent grid in `frontend/layouts/collection.vue:23-25` reserves a
`v-col cols="3"` (empty placeholder) and gives the main content
`v-col cols="9"` (75 % of viewport) starting at viewport-x ≈ 25 %.
But the fixed-position sidebar paints from viewport-x = 0 to
viewport-x ≈ 33 % (its `width: 100%` resolves against the placeholder
column, which on a 2560 viewport is 640 px wide). So the sidebar
overlays the leftmost ≈ 160 px (at 1920) to 0 px (at 2560+) of the
*content area*, swallowing the leading column of any data table on
the page. At 1440 × 900 (default desktop) the FileReference table's
Name column is 100 % occluded; the entire `File Reference "Session
Deliverables (9 markdown docs)"` page title is also clipped to
`...rables (9` because it starts inside the sidebar's z-index zone.

**Principle violated.** *Nielsen heuristic #1 — Visibility of system
status:* the user cannot see the names of files they uploaded a
minute ago, which is the single most diagnostic piece of information
on the page. *Also WCAG 2.2 SC 1.4.10 Reflow:* the content does not
reflow without loss of information at default desktop widths. *Also
Norman's mapping principle (Design of Everyday Things, ch. 4):* the
visual position of the table column → its meaning is broken; a
column header "Name" is shown but its cells are hidden behind a
panel.

**Fix proposal.** In `CollectionSidebar.vue:178`, drop the
`position: fixed` (let the sidebar live in normal flow inside its
`v-col cols="3"`). If a sticky scrollbar behaviour is desired,
switch to `position: sticky; top: 0` which keeps the sidebar in
flow. Verify on the four affected page types (collection detail,
DataObject detail, FileReference detail, TimeseriesReference
detail) that the main content's left edge no longer slides under
the sidebar. Bonus: add a collapse toggle on desktop too — the
sidebar is currently `d-md-none`'d only in mobile (`layouts/
collection.vue:10-18`), so a user on a 1440-wide laptop has no
way to reclaim the 25 % of horizontal space.

---

## U-02 — Markdown files preview as raw source, not rendered

**Severity.** ★★ (2 / 5 — the dialog is honestly labelled "Text
File Content" so it's not deceptive, but it's a missed expectation.
Researchers who upload `.md` in 2026 expect GitHub-style rendered
output, not literal `## Heading` text in a monospace box.)

**Page / element.** `frontend/components/context/display-components/
file-references/TextViewerDialog.vue`, invoked from the eye-icon
action on any `.md` row in a FileReference file table. Verified live
on the SHOWCASE.md preview in the AI Exchange / Session Deliverables
bundle — see
`validation-screenshots/file-preview-dialog-only.png`.

**What the user tried.** "I uploaded nine markdown docs, then
clicked the eye icon on SHOWCASE.md expecting to read the report
inline before deciding whether to download."

**What bit.** The dialog opens with title *Text File Content* and
shows the literal markdown source — `# Heading`, `**bold**`,
pipe-tables — in a `RichTextEditor` with `codeType: "markdown"`,
which gives syntax highlighting but no rendering. The mapping in
`frontend/components/context/display-components/file-references/
shepardFileMappingUtil.ts:35-56` lumps `.md` with `.txt / .yml /
.json / .toml` as `fileType: "text"`, so it gets the same code-view
treatment as a YAML file. A `.md` extension is the canonical signal
for "render me as a document".

**Principle violated.** *Nielsen heuristic #2 — Match between system
and the real world:* outside Shepard, every system a researcher
touches (GitHub, GitLab, VS Code preview, JupyterLab, Obsidian,
Notion) renders `.md` to HTML by default and offers "view source" as
a secondary action. Shepard inverts the convention.

**Fix proposal.** Add a `MarkdownViewerDialog.vue` sibling to
`TextViewerDialog.vue` (or a render-mode toggle inside the existing
dialog). When `fileType === "text"` *and* the extension is `.md`,
default to the rendered view; expose a "View source" toggle for the
syntax-highlighted source. Use an existing library — `marked` +
`DOMPurify`, or `markdown-it`, both already common in the Nuxt /
Vuetify ecosystem and safer than rolling sanitisation by hand
(per `feedback_reuse_trusted_code.md`). The dialog title should
change from "Text File Content" to the filename when rendering.

---

## U-03 — Search overlay floats above the sidebar tree without dismissing it

**Severity.** ★★ (2 / 5 — visual clutter, not a blocker. A first-
time user is briefly confused; the workaround "click outside" is
discovered immediately.)

**Page / element.** Collection detail page — the global "Search
shepard..." input in the top bar, when activated, opens a result
overlay that paints on top of the left sidebar tree. The "Filter…"
input inside the sidebar's CONTENTS panel stays visible and active
underneath it. See `validation-screenshots/collection-list.png` for
the resulting overlap (two search inputs visibly stacked, ADD /
New DataObject tooltips bleeding through, Edit tooltip floating
above a sidebar row).

**What the user tried.** "I clicked the search box at the top to
find a dataset by name. The result panel popped up but I could
still see and click into the sidebar's filter underneath it, so I
wasn't sure which search was 'active'."

**What bit.** Two text inputs of similar visual weight are
simultaneously interactive in the same viewport region without a
clear focus indicator or modal scrim differentiating them. The
sidebar tooltips also escape their parent (the `Edit`, `ADD`, and
`New DataObject` tooltips appear at unrelated coordinates).

**Principle violated.** *Nielsen heuristic #4 — Consistency and
standards:* modal overlays are conventionally accompanied by a
scrim that dims and disables the content underneath. *Material 3
guidance on modal vs. non-modal:* if the global search results
panel is meant to be the focus, it should be modal (scrim +
focus trap). If it's meant to coexist with sidebar filtering, the
two should be visually distinct so the user knows which input
they're typing into. *Norman, signifiers:* without a scrim, the
sidebar appears active when it should be dimmed.

**Fix proposal.** When the global search dropdown is open, render
a `v-overlay` scrim behind it that dims (but does not hide) the
sidebar, and prevent pointer events on the sidebar until the
overlay closes. Audit the sidebar's tooltip activator anchors —
the ADD / Edit / New DataObject tooltips should be positioned
relative to their trigger buttons, not floating mid-page.

---

## Format

```
## U-NN — short headline

**Severity.** ★ to ★★★★★ (1 = annoyance, 5 = blocks the task)

**Page / element.** `/collections/{id}/dataobjects/{id}` + which widget

**What the user tried.** A specific task in the user's voice — e.g.
"I wanted to download a file so I clicked the file name."

**What bit.** What actually happened that broke the user's expectation.

**Principle violated.** Citation — e.g. *"Nielsen heuristic #1: Visibility
of system status — the file-download click triggered no visible state
change for 2 seconds, and no user feedback indicated whether the click
had registered."*

**Fix proposal.** Concrete + bounded.
```
