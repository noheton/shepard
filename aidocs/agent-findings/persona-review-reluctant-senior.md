# Reluctant Senior Researcher — review of the MFFD shapes / views / SPI proposals

**Persona.** 28 years at DLR. 40 TB on NFS. 600-row Excel master sheet.
Track 1 Run 22192 is at `/mffd2024/track1/run22192/` and I can find it
in under ten seconds with `cd` and tab-completion. My attributes are
literal strings — `material_roll_change=true`, `run_number=22192`,
`track_number=1`. My colleagues know what they mean.

**Status.** The three docs the task brief points at — `aidocs/semantics/98`,
`aidocs/platform/100`, `aidocs/platform/101` — do not exist on disk.
Working from the nearest live design that says the same things:
`aidocs/semantics/95-shacl-templates-and-individuals.md` (shapes as
templates, shapes as views, shapes as agent contracts, writable
individuals, upper-ontology alignment). That doc carries the load
the missing three were supposed to carry. Where the brief asks me
about "7 view tabs" or "view-as-shape", I read it as the §4 templates
+ §5 views (sh:group → detail-page tabs) story in `95`.

---

## 1. The 90-second test

I open the proposed UI cold. The new Collection page for a MFFD
campaign has — what — a header, a row of tabs ("Plies", "Weld Params",
"NDT Gate", "Calibration", "Provenance", "Activities", "Files"), and
inside the first tab a form with seven required fields, half of which
have IRIs next to them (`dcterms:title`, `prov:wasGeneratedBy`).

What loses me first: **the IRIs.** I am looking for Run 22192. I do
not want to know that "Track Number" is `mffd:trackNumber` with
`xsd:integer` datatype and `sh:minCount 1`. I want to type "22192"
in a search box and have my run come up.

Second thing that loses me: **the tab row.** Seven tabs means
seven places my data could be. In my folder tree the data is
where I put it. Here it's wherever the shape author decided
`sh:group` should split.

Third — and this one is the actual killer — **the form has no
"upload my existing folder" button.** It assumes I'm creating
something new. I'm not. I have 40 TB.

---

## 2. Conversion killers — provably worse than my current setup

### CK1. Shape-defined attribute keys vs my literal keys

`95 §4` shows the EU-Project shape requires `dcterms:title`,
`:gaNumber` matching `^[0-9]{6}$`. My import has
`material_roll_change`, `run_number`, `track_number`. None of those
are in any shape anywhere. Three options the doc gives me:

- (a) coin new vocab terms (gated by usage count — `95 §9` tier 3)
- (b) ask an instance-admin to add them to `mffd:` ontology
- (c) shove them in some "extra attributes" bag

(a) means I wait for a usage threshold. (b) means I file a ticket
with somebody. (c) means the shape engine doesn't understand my
data and the "shapes-drive-views" promise silently degrades to
"shapes drive views for the data the shape author thought of."

My folder tree has none of these problems. `material_roll_change=true`
is right there in the directory name.

### CK2. Tab grouping is the shape author's opinion, not mine

`95 §5` "Detail-page tabs. A shape property tagged with `sh:group`
groups into a tabbed section." So whoever wrote `mffd:TapeLayupShape`
decided which fields go on which tab. I disagree with their grouping
on day one. In my Excel sheet I have my columns in the order I want.
Here I get the shape author's order, with `sh:order` numbers.

Override path: not documented. I assume "fork the shape." I am not
forking a shape to reorder a tab.

### CK3. "Writable layer" is a power-user permission I don't have

`95 §9` — three tiers: Vendor, Instance-admin, Power-user. To coin
new vocab I need power-user. To author shapes I need instance-admin.
On day one I have neither. So I am locked out of the very mechanism
the doc says makes Shepard adaptable.

Compare: on my NFS share I am root in my own subdirectory.

### CK4. Migration story for 40 TB is missing

`95 §10` "Backward compatibility — TPL4 lossy long-tail acknowledged
honestly; institute-side review needed." That sentence is doing a
lot of work. "Lossy long-tail" means **my data doesn't fit.** No
import script in the design that takes `/mffd2024/track1/run22192/`
+ a sidecar YAML and produces a DataObject conformant to
`mffd:TapeLayupShape`. There is `examples/mffd-showcase/seed.py`
for the synthetic demo, not for me.

### CK5. The system can be down

My NFS share has been up since 2007 with two outages I remember.
Shepard is Neo4j + Postgres + Influx + Mongo + S3 + Quarkus + a
proxy. That's six things that can break. The design doesn't tell
me what my fallback is when one of them does.

---

## 3. The one demo moment that would make me try it for real

Pull up Run 22192. Show me the **provenance graph** with TR-001
(material batch) → AFP layup → ultrasonic weld → NDT scan → next
run, with the NDT report linked, the calibration cert of the
ultrasound probe linked, **and the operator's shift log linked**,
all visible in one screen with two clicks from the top.

Then click TR-004 (the anomaly). Show me the investigation
sub-tree branching off, the repair entry, the re-test. Tell me
"this took 2 hours to reconstruct from the folder tree last month
because run notes were in three different Excel files."

That's the demo. **One screen, one click, the audit trail that
EN 9100 wants and I currently assemble by hand.** If that screen
works, I will sit down for a longer demo.

The shape-driven form, the SHACL templates, the upper-ontology
alignment — none of that is the demo. **The provenance graph is
the demo.** Everything else is plumbing the user shouldn't see.

---

## 4. Arguments for different paths

### A. Default view: Tree vs Table vs Graph

| Path | Argument for |
|---|---|
| Tree (folders) | Maps to my existing mental model. Zero training. Adoption-friendly. Lets me migrate gradually — Shepard *is* my folder tree, just indexed. |
| Table (Excel-like) | Maps to the 600-row master sheet I actually work with. I want columns, sort, filter, export-to-Excel. The reluctant-senior killer view. |
| Graph (provenance) | Maps to the one thing folders genuinely can't do (multi-parent, lateral lineage). The funder demo. |

**Lean: Table is default for the persona, Graph is the "wow"
view one click away, Tree is the migration on-ramp.** The doc
defaults to a per-shape detail page with tabs (`95 §5`). Wrong
default for me. The list-of-things-with-columns view should be
the landing page. I should be able to add my own columns from
any attribute that exists on the rows, not just the ones the
shape author tagged with `sh:order`.

### B. SHACL: hidden vs exposed in advanced mode

| Path | Argument for |
|---|---|
| Hidden (basic) | I never see IRIs. The form just has "Project title" and "GA number." Internally those are `dcterms:title` and `:gaNumber`, but I never type a colon. Shape becomes invisible scaffolding. |
| Exposed in advanced | The PI / data steward needs to see the shape to know what's enforced. Tooltips show the IRI. Hover reveals `xsd:integer` constraint. |

**Lean: hidden in basic, revealed in advanced, never required.**
Per the project memory rule (`feedback_basic_advanced_superset.md`)
advanced is a strict superset of basic. So basic shows labels;
advanced shows labels **and** IRIs. The doc currently reads like
SHACL is the user-facing surface (`95 §2` "ontology layer is the
source of truth") — which is true for the system but should be
false for the user. The user sees forms. The shape is invisible.

### C. Attribute keys: preserve mine vs force-map to shape

| Path | Argument for |
|---|---|
| Preserve | `material_roll_change` stays a literal string attribute on the DataObject, unmapped. I keep my vocabulary; Shepard indexes it as freetext. Adoption-friendly. |
| Force-map | Every attribute must resolve to a SHACL property path. `material_roll_change` becomes `mffd:materialRollChange xsd:boolean`. The shape engine works. I have to migrate my Excel. |
| Hybrid | Two buckets per DataObject: "typed" (resolves to shape) and "literal" (freetext bag). Shape-driven UI shows typed; advanced mode shows both. New work uses typed; legacy stays literal. |

**Lean: Hybrid.** Force-map is what the doc currently implies and
it is the migration killer for 40 TB of legacy data. Pure preserve
loses the "shapes drive views" claim. Hybrid keeps both —
**legacy data continues to work via the literal bag, new data
gets the typed shape benefits.** Per `95 §10` this is exactly the
"lossy long-tail" the design admits to but doesn't resolve. The
hybrid bucket is the resolution.

### D. Shape authoring: who and when

| Path | Argument for |
|---|---|
| Instance-admin only | Quality control. No proliferation. One ontology per institute. |
| Power-user (gated by usage count) | The doc's path (`95 §9`). Self-service for trusted users. |
| Anyone can draft, admin promotes | I draft a shape with my literal keys; the admin reviews and promotes. I'm not blocked, the ontology stays clean. |

**Lean: Anyone-drafts-admin-promotes.** Closest match to a real
research-group workflow where the senior researcher prototypes
and the data steward formalises.

---

## 5. Clarifications I need before I'd commit

### `[NEEDS-CLARIFICATION-1]` Can I keep `material_roll_change` as a literal attribute?

Options:
- (a) Yes — literal-string attributes persist forever as freetext,
      no shape mapping required, indexed for search.
- (b) No — every attribute MUST resolve to a SHACL property path
      after a deprecation window; literal keys are imported but
      flagged as "to be typed."
- (c) Hybrid — literal bucket and typed bucket coexist permanently;
      shape-driven UI surfaces typed only; advanced mode surfaces both.

**Lean: (c).** (a) is what I want; (b) is what the design currently
implies; (c) is the compromise that lets me migrate without
breaking the architectural claim.

### `[NEEDS-CLARIFICATION-2]` Where is the tab grouping decided?

Options:
- (a) Shape author via `sh:group`. End user has no override.
- (b) Shape author defines defaults; end user can re-order and re-group per session.
- (c) End user defines tabs entirely; shape only provides the field set.

**Lean: (b).** (a) is the doc's default and I will reorder constantly. (c) loses the
auto-generated benefit.

### `[NEEDS-CLARIFICATION-3]` Default landing view for a Collection

Options:
- (a) Per-shape detail page with tabs (`95 §5` default).
- (b) Table view with shape-driven default columns.
- (c) Tree view of the Collection hierarchy.
- (d) Configurable per-user.

**Lean: (b) for the table-driven persona, (d) in v2.** (a) is the
doc's current implicit answer and it's wrong for the table-first user.

### `[NEEDS-CLARIFICATION-4]` Bulk import path for legacy folders

Options:
- (a) Manual creation through the form per DataObject. (40 TB ≈ never.)
- (b) Sidecar YAML + folder walk, generates DataObjects with literal attrs.
- (c) Sidecar YAML + shape mapping, generates DataObjects conformant to shape.
- (d) Both (b) and (c), with (b) as the migration on-ramp.

**Lean: (d).** Per the importer plugin memory note this is exactly
the gap `shepard-plugin-importer` is supposed to fill.

### `[NEEDS-CLARIFICATION-5]` Downtime fallback

Options:
- (a) Read-only mirror on NFS as a snapshot, refreshed nightly.
- (b) Export-to-folder-tree on demand, so I can always recover a known shape.
- (c) Live HA Neo4j + read replicas. (Operationally heavy.)
- (d) Documented "your data is in MinIO/S3 at path X" so I can grab it bare.

**Lean: (b) + (d).** I need to know I can always get my bits back
without the application running.

---

## 6. Honest verdict

**Would I adopt after one session? No.**

Would I run it in parallel for six months? Maybe. The provenance
graph is genuinely the thing my folder tree can't do, and I see
the EN 9100 angle. But:

- The shape vocabulary is the wrong level of abstraction for me on
  day one. I don't want to learn SHACL, IRIs, or `sh:group`.
- My 40 TB has no migration path described.
- I lose control over how my data is grouped, ordered, and labeled.
- The system has too many moving parts to trust as a system of record.

If the team showed up with the **provenance graph demo of CK6 §3**
running on **my actual data** (not the synthetic seed), with my
attribute keys preserved as a literal bucket alongside the typed
shape — I would clear my afternoon. As of right now the design
reads as "you will learn our vocabulary." I won't.

---

## 7. Top 3 changes before I'd migrate any real data

1. **Hybrid attribute bucket — literal + typed.** Ship per
   `[NEEDS-CLARIFICATION-1]` option (c). My `material_roll_change`
   stays a literal string attribute on the DataObject; the shape
   engine ignores it; the search index finds it. New work uses
   typed shape properties. The "lossy long-tail" admission in
   `95 §10` becomes a designed feature, not an apology.

2. **Folder-walk importer with sidecar YAML, no shape required.**
   Per `[NEEDS-CLARIFICATION-4]` option (d). I point it at
   `/mffd2024/track1/run22192/`, it produces a Collection with one
   DataObject per subdirectory, attributes from the directory name
   or sidecar YAML. The shape mapping is *optional* and runs
   *after* the import. This is `shepard-plugin-importer` and it is
   the migration on-ramp the design is missing.

3. **Table view as the default landing page for a Collection.**
   Per `[NEEDS-CLARIFICATION-3]` option (b). Columns are sortable,
   filterable, exportable to CSV. The shape suggests default
   columns. I can add any attribute (typed or literal) as a column.
   Per-user column preferences persist. This replaces my Excel
   master sheet in the one place where it matters — the at-a-glance
   list-of-runs.

If those three land, **I run it in parallel for six months.** If
all three are still missing in six months, I haven't migrated and
nobody in my group has either, and the project quietly dies in
our corner of the institute. The fix isn't more ontology — it's
making the ontology invisible to the people who don't want it,
and giving the people who do want it the tools to drive it without
locking everyone else out.

---

## Note on source docs

The brief cited `aidocs/semantics/98`, `aidocs/platform/100`,
`aidocs/platform/101`. None of those exist on disk as of
2026-05-22. The nearest live design with the same load-bearing
claims is `aidocs/semantics/95-shacl-templates-and-individuals.md`
(2026-05-21). Findings here are written against `95`. If `98 /
100 / 101` ship later with different shapes, this review needs
to be re-run against them — the gut reactions about IRIs, tabs,
migration, and shape authority will likely survive, but the
specific section references will not.
