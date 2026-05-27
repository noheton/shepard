/**
 * RDM-005 / FAIR8 — Metadata Completeness Score helper.
 *
 * Pure (no I/O) scoring utility consumed by `MetadataCompletenessCard.vue`.
 * The card supplies the Collection wire shape + a handful of cheap fetched
 * counts (semantic annotations, lab journal entries, creator ORCID,
 * keyword tags) and this helper produces a deterministic 0–100 score with
 * a per-check breakdown.
 *
 * Design — why this is pure:
 *   - keeps Vitest unit cases trivial (no API mocking)
 *   - lets the widget compose existing composables (
 *     `AnnotatedCollection.fetchAnnotations`,
 *     `useFetchCollectionLabJournalEntries`, `UserApi.getUser`)
 *     without intertwining their lifecycles with the scoring math
 *   - matches the `CiteThisCard` / `formatCitation` split — same shape
 *     as the 2026-05-24 RDM-001 card the spec calls out as the
 *     model component
 *
 * Score band thresholds (red/amber/green) follow the standard DMP
 * completeness widget pattern used by Coscine and openBIS:
 *   - `< 50`  → red (`error`) — collection is not publication-ready
 *   - `< 80`  → amber (`warning`) — missing key FAIR fields
 *   - `>= 80` → green (`success`) — DMP-grade
 *
 * Total ceiling is 100; today's check list sums to exactly 100. When a
 * new check lands (e.g. `contributors[]` array once that wire field
 * exists), its points must be added to the appropriate band or the
 * ceiling re-baselined; the unit test `total points sums to 100` will
 * fail to make this obvious.
 *
 * ── F-UJI alignment (FAIR8) ────────────────────────────────────────────
 * Scoring inspired by F-UJI FAIR maturity indicators (fuji.net / CESSDA
 * PANGAEA). Each check below is annotated with the F-UJI indicator code
 * it contributes to. Indicators not yet addressable from a single
 * Collection record (FsF-F1-01D/02D PID registration, FsF-F4-01M
 * catalogue harvest, FsF-I1-01M RDF serialisation, FsF-I3-01M related
 * entity links, FsF-R1.3-01M community standard, FsF-R1.3-02D file
 * format) are tracked in FAIR8 follow-up rows in aidocs/16.
 * ───────────────────────────────────────────────────────────────────────
 */
import type { Collection } from "@dlr-shepard/backend-client";

/** Identifier slug for each check — referenced by the e2e test + the
 *  per-row anchor in the card's deep-link `scrollIntoView()` jumps. */
export type MetadataCheckId =
  | "name"
  | "description"
  | "license"
  | "accessRights"
  | "creatorOrcid"
  | "semanticAnnotation"
  | "labJournal"
  | "keywords"
  | "dataObjects";

export interface MetadataCheck {
  /** Stable identifier — used as the `data-testid` suffix in the card. */
  id: MetadataCheckId;
  /** Short label rendered in the per-check list row. */
  label: string;
  /** True when the field/condition is present; false when missing. */
  passed: boolean;
  /** Contribution to the score on pass (always added when `passed`). */
  points: number;
  /**
   * One-line explanation of why this matters (FAIR / funder mapping).
   * Rendered in the v-tooltip on hover/focus.
   */
  why: string;
  /**
   * Optional `#hash` deep-link / DOM-id target on the Collection
   * landing page. The card scrolls smoothly to this anchor when the
   * row's action button is clicked. Null for checks where the only
   * fix lives off-page (e.g. ORCID lives on `/me`).
   */
  deepLink: string | null;
  /** Verb to render on the missing-state action button. */
  actionLabel: string;
}

export interface MetadataCompletenessResult {
  /** 0–100 inclusive. */
  score: number;
  /** Sum of all points the checks could yield — today 100. */
  maxScore: number;
  /** Per-check breakdown in the order they render. */
  checks: MetadataCheck[];
  /** Vuetify colour token for the score chip (`error|warning|success`). */
  band: "error" | "warning" | "success";
}

export interface MetadataCompletenessInputs {
  collection: Collection;
  /**
   * Total number of semantic annotations on the Collection. Fetched
   * by the widget via `AnnotatedCollection.fetchAnnotations`. `null`
   * means "still loading"; the check renders as a pass-through `false`
   * while loading so the score is conservative.
   */
  semanticAnnotationCount: number | null;
  /**
   * Total number of lab journal entries across all DOs in the
   * Collection. Fetched via `useFetchCollectionLabJournalEntries`.
   */
  labJournalCount: number | null;
  /**
   * ORCID string of the Collection creator (looked up via
   * `UserApi.getUser({username: collection.createdBy})`). Null
   * means "no ORCID set" OR "still loading"; the widget tracks the
   * loading state separately so the row can render a spinner.
   */
  creatorOrcid: string | null;
  /**
   * Number of keyword/subject-tag semantic annotations on the
   * Collection (predicate = schema:keywords or equivalent controlled
   * term). Fetched by the widget alongside the general annotation
   * count; a dedicated keyword-annotation endpoint will be added in
   * FAIR8 follow-up. `null` means "still loading" — treated
   * conservatively as 0 so the score never over-reports.
   * FsF-F2-01M (descriptive core metadata: keywords sub-field).
   */
  keywordCount: number | null;
}

/** Minimum description length to count as "rich" — 50 chars is the
 *  threshold used by DataCite's `Description` field guidance and by
 *  Zenodo's completeness gauge. Avoids one-line stubs. */
export const DESCRIPTION_MIN_CHARS = 50;

/** Re-exported so the widget can scrollIntoView the same DOM ids the
 *  Collection landing page declares. Keep in sync with the
 *  `id="metadata-..."` anchors planted on that page. */
export const DEEP_LINK_IDS = {
  description: "metadata-description-section",
  license: "metadata-license-edit",
  accessRights: "metadata-license-edit",
  semanticAnnotation: "metadata-annotations-section",
  labJournal: "metadata-labjournal-section",
  keywords: "metadata-annotations-section",
  dataObjects: "metadata-dataobjects-section",
  // name + creatorOrcid intentionally absent — name is on the title
  // strip (always edited via the global edit dialog), creatorOrcid
  // lives off-page on `/me` so the action is a route push not a scroll.
} as const;

function clamp01(value: number): number {
  return Math.max(0, Math.min(value, 100));
}

/**
 * Compute the completeness score + per-check breakdown.
 *
 * Determinism: same inputs → same outputs. No `Date.now()`, no
 * random IDs, no I/O. Safe to call on every render — but the widget
 * memoises via `computed()` anyway.
 */
export function computeMetadataCompleteness(
  inputs: MetadataCompletenessInputs,
): MetadataCompletenessResult {
  const {
    collection,
    semanticAnnotationCount,
    labJournalCount,
    creatorOrcid,
    keywordCount,
  } = inputs;

  // Defensive reads: the wire shape might predate LIC1 on an older
  // backend image. Mirror the parent page's `(collection as unknown
  // as ...)` cast so the helper is forward-compatible with
  // regenerated clients.
  const license =
    (collection as unknown as { license?: string | null }).license ?? null;
  const accessRights =
    (collection as unknown as { accessRights?: string | null }).accessRights ??
    null;

  const description = collection.description ?? "";
  const name = collection.name ?? "";

  const checks: MetadataCheck[] = [
    // FsF-F2-01M — descriptive core metadata: Title sub-field.
    {
      id: "name",
      label: "Collection has a name",
      passed: name.trim().length > 0,
      points: 10,
      why:
        "DataCite §3 (Title) + F-UJI FsF-F2-01M — required for every " +
        "published dataset.",
      deepLink: null,
      actionLabel: "Set name",
    },
    // FsF-F2-01M — descriptive core metadata: Description sub-field.
    // FsF-R1-01MD — data content metadata (variable/format description).
    {
      id: "description",
      label: `Description ≥ ${DESCRIPTION_MIN_CHARS} characters`,
      passed: description.trim().length >= DESCRIPTION_MIN_CHARS,
      points: 15,
      why:
        `DataCite §17 (Description) + F-UJI FsF-F2-01M / FsF-R1-01MD — ` +
        `at least ${DESCRIPTION_MIN_CHARS} characters distinguishes a stub ` +
        `from a real abstract.`,
      deepLink: DEEP_LINK_IDS.description,
      actionLabel: "Add description",
    },
    // FsF-R1.1-01M — data usage license.
    {
      id: "license",
      label: "License (SPDX) set",
      passed: typeof license === "string" && license.trim().length > 0,
      points: 20,
      why:
        "DataCite §16 (Rights) + F-UJI FsF-R1.1-01M — the single biggest " +
        "blocker to publication. Without a license the dataset is legally " +
        "unusable.",
      deepLink: DEEP_LINK_IDS.license,
      actionLabel: "Add license",
    },
    // FsF-A1-01M — data access information (access level / conditions).
    {
      id: "accessRights",
      label: "Access rights set",
      passed:
        typeof accessRights === "string" && accessRights.trim().length > 0,
      points: 10,
      why:
        "DataCite §16 (Rights) + F-UJI FsF-A1-01M — declares open / " +
        "restricted / closed / embargoed. Required for Horizon Europe " +
        "embargoed deposits.",
      deepLink: DEEP_LINK_IDS.accessRights,
      actionLabel: "Set access rights",
    },
    // FsF-F2-01M — descriptive core metadata: Creator sub-field.
    {
      id: "creatorOrcid",
      label: "Creator has ORCID",
      passed:
        typeof creatorOrcid === "string" && creatorOrcid.trim().length > 0,
      points: 10,
      why:
        "DataCite §2 (Creator) + F-UJI FsF-F2-01M — researcher PID. " +
        "Without ORCID the citation falls back to a bare username with " +
        "no resolver.",
      deepLink: null,
      actionLabel: "Set ORCID on /me",
    },
    // FsF-I2-01M — metadata with semantic/vocabulary resources.
    {
      id: "semanticAnnotation",
      label: "At least one semantic annotation",
      passed: (semanticAnnotationCount ?? 0) > 0,
      points: 10,
      why:
        "F-UJI FsF-I2-01M — controlled-vocabulary annotation makes the " +
        "Collection findable via ontology-aware catalogues (FAIR I1 + I2).",
      deepLink: DEEP_LINK_IDS.semanticAnnotation,
      actionLabel: "Add annotation",
    },
    // FsF-R1.2-01M — data provenance.
    {
      id: "labJournal",
      label: "At least one lab journal entry",
      passed: (labJournalCount ?? 0) > 0,
      points: 5,
      why:
        "F-UJI FsF-R1.2-01M — narrative context that the machine-readable " +
        "provenance graph alone cannot provide (FAIR R1.2).",
      deepLink: DEEP_LINK_IDS.labJournal,
      actionLabel: "Open Lab Journal",
    },
    // FsF-F2-01M — descriptive core metadata: Keywords sub-field.
    // Note: `Collection` has no native keywords[] field today; this check
    // uses `keywordCount` supplied by the widget from keyword-predicate
    // semantic annotations. A dedicated keyword-annotation pass is tracked
    // in the FAIR8 follow-up row in aidocs/16.
    {
      id: "keywords",
      label: "At least one keyword annotation",
      passed: (keywordCount ?? 0) > 0,
      points: 5,
      why:
        "F-UJI FsF-F2-01M (Keywords sub-field) — subject tags make the " +
        "Collection discoverable in catalogue keyword searches (DataCite " +
        "§6 Subject).",
      deepLink: DEEP_LINK_IDS.keywords,
      actionLabel: "Add keyword annotation",
    },
    // FsF-F3-01M — metadata includes resource identifier / access info.
    {
      id: "dataObjects",
      label: "Has at least one DataObject",
      passed: (collection.dataObjectIds?.length ?? 0) > 0,
      points: 15,
      why:
        "F-UJI FsF-F3-01M — a Collection with zero DataObjects has nothing " +
        "for harvesters to enumerate (FAIR F2 / F3).",
      deepLink: DEEP_LINK_IDS.dataObjects,
      actionLabel: "Open Data Objects",
    },
  ];

  const maxScore = checks.reduce((acc, c) => acc + c.points, 0);
  const earned = checks
    .filter(c => c.passed)
    .reduce((acc, c) => acc + c.points, 0);
  const score = clamp01(earned);

  const band: "error" | "warning" | "success" =
    score < 50 ? "error" : score < 80 ? "warning" : "success";

  return { score, maxScore, checks, band };
}
