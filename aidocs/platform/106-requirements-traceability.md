# 106 — Requirements traceability — research direction

**Status:** Research direction. No implementation commitment.
**Snapshot date:** 2026-05-22.
**Audience:** Contributors and reviewers who care about *which code satisfies which requirement* and the inverse — *for any requirement, where is it implemented + verified?*
**Originating item:** user-asked 2026-05-22 — *"is there a way to map specific requirements to specific parts of implementation, for code transparency?"*

---

## 0. Why this matters for shepard specifically

Shepard's regulatory framing already names the audiences that demand traceability:

- **DIN EN 9100 §8.7** — non-conformance traceability obligation for aerospace QMS
- **EASA AI Concept Paper Issue 02** — the W-shape requires Learning Assurance code-to-requirements links
- **EU AI Act 2024/1689 Article 50** — AI-generated content marking + verifiable trail
- **EU Machinery Regulation 2023/1230 Article 17** — 10-year technical file retention with audit-evidence backing
- **DFG / Horizon Europe / Clean Aviation JU** — funding-body reporting on what was built vs what was promised
- **FAIR / F(AI)²R principle** — every Claim must `prov:wasGeneratedBy` an Activity (aidocs/95 §14e)

The common ask: *prove that line N of file F exists to satisfy requirement R, and that test T verifies it.* Today shepard answers this **structurally** (commit scopes, aidocs/34 ledger rows, feature IDs) but **not queryably** — a human walking the trace assembles it by hand from prose docs.

This doc surveys the design space and proposes three shepard-flavoured shapes (lightest to heaviest), ranked by current fit.

---

## 1. What shepard has today (de facto traceability)

The traceability is already happening — it's just not indexed.

| Artefact | Carries requirement IDs? | Bidirectional? | Machine-readable? |
|---|---|---|---|
| **Conventional commit scopes** — `feat(FS1b):`, `fix(#148):`, `feat(TPL2a):` | Yes (the scope IS the requirement ID) | One-way (commit → req) | Yes (grep `git log`) |
| **Aidocs numbered docs** — `aidocs/platform/87-timeseries-appid-migration.md` | Yes (~190 docs, each numbered) | One-way | Filename only |
| **`aidocs/34` upgrade ledger rows** — feature ID + commit hash citation | Yes, explicit | Bidirectional but manual | Markdown table — semi-structured |
| **`aidocs/44` feature matrix rows** — per-ID status with hash cite | Yes | One-way (matrix → commit) | Markdown table |
| **Tasks (`#N`)** — scope-ID references in descriptions | Yes | Manual | TaskList JSON |
| **JAX-RS `@Tag(name="…")`** on resources | Sometimes (mostly user-facing category, not requirement ID) | One-way | Yes (annotation processor) |
| **Aidocs/16 dispatcher backlog IDs** — `FS1a`, `KIP1d`, `A0`, `EXP1a`, … | Yes (~200 backlog items with stable IDs) | Manual cross-link to commits | Markdown |
| **F(AI)²R `:Activity`** (PROV1a shipped; TPL9 designed) | Yes — every AI write is a typed Activity | Yes (by design) | Yes (graph queryable) |

**Structural conclusion:** the convention is already in place. Every commit naming `feat(FS1b):` is a one-way trace from code to requirement. Every aidocs/34 row citing a hash is a one-way trace from requirement to code. The gap is **automated bidirectional indexing**.

---

## 2. Industry approaches (ranked by fit for shepard)

Six families, ranked by fit for shepard's scale (~200 feature IDs, single-repo monorepo, Quarkus + Vue, regulatory-aware):

### 2.1 `git log` post-hoc index (lightweight)

Walk `git log` extracting conventional-commit scopes; build a JSON index from scope ID to {commits, files touched, tests}. Cross-reference with aidocs numbered docs.

**Fit: HIGH.** Works on what's already there. One script. No retrofit. Limitation: trace is to *files* not *lines*.

### 2.2 In-source `@Requirement("FS1b")` annotations (precise)

Decorate Java classes/methods with `@Requirement` annotations. Extract via `apt` annotation processor at build time (no runtime overhead). Combined with 2.1, get line-level precision.

**Fit: MEDIUM.** Requires retrofit (~700 Java files in `backend/src/main/`). High precision once done. Frontend equivalent: TypeScript decorators (`@requirement('TPL2a')`) — same pattern.

Examples in the wild: Spring's `@Validated`, JUnit's `@DisplayName`, OWASP CycloneDX's traceability annotations, NASA cFS coding standard.

### 2.3 OSLC Configuration Management / ReqIF (heavyweight)

Open Services for Lifecycle Collaboration (W3C/OMG) — bidirectional REST links between requirement records (in DOORS/Polarion/Jama) and code (in git/SCM). ReqIF is the OMG-standard XML interchange.

**Fit: LOW for shepard's scale.** Designed for IBM ELM / Jama / DOORS users with thousands of req records + dedicated requirement-management tooling. Heavy ceremony for a fork with one core dev team.

Worth knowing because: if shepard is ever adopted by a DLR institute already using DOORS, OSLC bridges them. Out of scope for v1.

### 2.4 BDD / Cucumber tag traceability (test-centric)

Gherkin `Feature:` files as requirements; `@FS1b` tags on scenarios; step definitions link to backend methods.

**Fit: MEDIUM.** Adds a Cucumber dependency. Strong for acceptance-style requirements ("when an operator uploads a file, then it lands in the active storage adapter"); weak for non-functional requirements ("the FileStorage SPI must be plugin-extensible").

Pairs naturally with: the existing Playwright e2e tests in `e2e/tests/` — those are already requirement-shaped.

### 2.5 SHACL shapes as requirements (substrate-aligned)

Express requirements as SHACL shapes. A code module declares it satisfies a shape via a `shacl:satisfies <requirement-iri>` annotation. CI validates that the implementation matches.

**Fit: HIGH long-term.** Fits the substrate-split chain: shapes are already authoritative for data; making them authoritative for code-against-requirements is one more axis. But requires the substrate-split (#127) to mature.

```turtle
easa-cp2:EXP-09  a easa-cp2:LearningAssuranceRequirement ;
    rdfs:label "AI activity capture" ;
    rdfs:comment "Every :Activity involving an AI agent MUST carry a fair2r:AuthoringPass typing." ;
    shacl:shape <fair2r:AuthoringPassShape> .

shepard:code-prov1a-filter
    shacl:satisfies easa-cp2:EXP-09 ;
    shepard:implementedBy "backend/.../ProvenanceCaptureFilter.java" .
```

A SPARQL query then answers: *"show me all code claiming to satisfy EASA-CP2 requirements + the shape it must validate."*

### 2.6 Formal verification (rigorous tail)

Coq / Isabelle / Dafny / SMT-backed Java verifiers. Code proofs that the implementation matches a formal spec.

**Fit: ZERO for shepard.** Worth knowing exists; not a fit for a Quarkus + Vue stack that ships rapidly. Mention only so reviewers don't ask.

---

## 3. Three shepard-flavoured options

### Option A — `git-log` trace index (lightest)

**What:** A script that reads:
- `git log --format='%H %s'` — for scope IDs in commit messages
- `aidocs/16-dispatcher-backlog.md` — for the backlog catalogue of feature IDs (~200)
- `aidocs/34-upstream-upgrade-path.md` — for explicit hash citations
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — for status per ID

Builds JSON:

```json
{
  "FS1b": {
    "title": "shepard-plugin-file-s3 — S3 adapter for the file payload kind",
    "status": "shipped",
    "aidocs": ["aidocs/data/45-gridfs-to-s3-evaluation.md", "aidocs/34#row-FS1b"],
    "commits": ["abc123 feat(FS1b): ...", "def456 fix(FS1b): locator format ..."],
    "files_touched": ["plugins/file-s3/.../S3FileStorage.java", "..."],
    "tests": ["plugins/file-s3/src/test/.../S3FileStorageTest.java"],
    "depends_on": ["FS1a"],
    "depended_by": ["FS1c", "FS1d", "FS1e1", "FS1e2", "FS1f"]
  }
}
```

Surface options: `docs/reference/traceability.md` auto-regenerated, `GET /v2/admin/traceability/{id}` REST surface (later).

**Effort:** 1 day. One Python script + Make target + CI step.
**Coverage:** every shipped feature; file-level trace.
**Limitation:** file-level, not line-level.

### Option B — `@Requirement` annotation + extractor (precise)

```java
@Retention(RetentionPolicy.SOURCE)
@Target({TYPE, METHOD, FIELD, CONSTRUCTOR})
public @interface Requirement {
  String[] value();
  String note() default "";
}
```

Decorated code:

```java
@Requirement({"FS1b", "FS1d"})
public class S3FileStorage implements FileStorage {
  @Requirement("FS1c")
  public Optional<PresignedPut> presignedUploadUrl(...) { ... }
}
```

Build-time annotation processor extracts to `target/traceability/requirements.json`. CI step diffs against Option A's canonical index; fails if a `@Requirement("FS1b")` references an unknown ID.

**Effort:** 2-3 days for annotation + processor + integration. Retrofit spreads PR-by-PR (~700 backend files).

**Frontend equivalent:** TypeScript decorators (`@requirement('TPL2a')` on Vue composables).

### Option C — SHACL shapes as requirements (substrate-aligned)

Express requirements as named SHACL shapes in `backend/src/main/resources/requirements/*.ttl`. A class's `@Requirement("EASA-CP2-EXP-09")` carries a shape claim CI can validate against the runtime graph.

**Effort:** 1-2 weeks. Gated on substrate-split #127 maturity.
**Coverage:** regulatory + design requirements; particularly strong for EU AI Act / EASA / EN 9100 obligations where the requirement reduces to a shape.

---

## 4. Recommended sequencing

```
NOW       (1 day)    Option A — git-log trace index + Markdown report
                      Output: docs/reference/traceability.md auto-generated

LATER     (1 sprint) Option B — @Requirement annotation + apt extractor
                      Pairs with Option A; precision boost without retrofit blocker

2026-Q4   (gated)    Option C — SHACL as requirements layer
                      Waits on substrate-split #127 maturity
                      Lights up EASA Learning Assurance W-shape
```

A → B → C compose. The JSON index from A becomes the input to B's extractor and to C's SPARQL queries.

---

## 5. Open questions

| Q | Notes |
|---|---|
| Should requirement IDs be stable forever, or rev-able? | Likely stable; shepard's IDs (`FS1b`, `KIP1d`, `EXP1a`) already are. |
| Where do regulatory IDs live? (e.g. `EASA-CP2-EXP-09`) | Separate namespace from shepard's design-feature IDs. `kind` field disambiguates. |
| How do negative requirements ("MUST NOT") map? | Same annotation; the shape declares the negation. SHACL `sh:not`. |
| What about commit messages that miss the scope? | The `git-log` script flags these in a CI lint step. |
| Should the index be a runtime endpoint or build artefact? | Build artefact first (markdown); runtime endpoint when there's a consumer. |
| Should non-shipped requirements (designed but not implemented) appear? | Yes — `status: designed` in the JSON. |
| How does F(AI)²R plug in? | Every `fair2r:AuthoringPass` Activity carries `prov:used <requirement-iri>` linking AI-generated code to the requirement it claimed to satisfy. |

---

## 6. Implementation hooks (when A ships)

- `docs/reference/traceability.md` (auto-generated, regenerated on every merge to main)
- `.github/workflows/*.yml` — new step `build-traceability-index` before `build-images`
- `scripts/build-traceability-index.py` — the generator
- `.github/pull_request_template.md` — checklist item: "Commit message uses conventional scope matching the requirement ID"
- `shepard-admin traceability <id>` — CLI verb (future)

---

## 7. Companion docs

- `aidocs/16-dispatcher-backlog.md` — canonical catalogue of ~200 feature IDs
- `aidocs/34-upstream-upgrade-path.md` — operator-facing change ledger; closest to a current "requirements list"
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — status per ID
- `aidocs/semantics/95-shacl-templates-and-individuals.md §14e` — F(AI)²R provenance
- `aidocs/agent-findings/easa-ai-regulatory-positioning.md` — EASA AI Concept Paper Issue 02 framing
- `aidocs/agent-findings/easa-data-management-learning-assurance.md` — EASA Learning Assurance W-shape
- `aidocs/agent-findings/eu-machinery-regulation-2023-1230.md` — 10-year retention obligation

---

## 8. Decision

**This is a research-direction doc, not a build commitment.** When the team decides to invest:
- Option A as a task; ships in a sprint slot
- Option B per-PR retrofit afterward
- Option C alongside substrate-split #127 maturity (2026-Q4 earliest)

The principle worth committing to NOW (before any code ships): **every new feature PR uses a conventional commit scope that exists in `aidocs/16-dispatcher-backlog.md` or `aidocs/34`**. That's the cheapest possible enforcement of the convention, and makes Option A's index possible without any new infrastructure.

User picked Option A prototype as the next concrete step (2026-05-22). The prototype lives at `scripts/build-traceability-index.py` (added in the same commit batch).
