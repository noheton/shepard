/**
 * RDM-005 — Metadata Completeness Score helper.
 *
 * Pure (no I/O) scoring utility consumed by `MetadataCompletenessCard.vue`.
 * The card supplies the Collection wire shape + a handful of cheap fetched
 * counts (semantic annotations, lab journal entries, creator ORCID) and
 * this helper produces a deterministic 0–100 score with a per-check
 * breakdown.
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
  | "heroImage"
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
  heroImage: "metadata-heroimage-edit",
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
  const { collection, semanticAnnotationCount, labJournalCount, creatorOrcid } =
    inputs;

  // Defensive reads: the wire shape might predate LIC1 on an older
  // backend image. Mirror the parent page's `(collection as unknown
  // as ...)` cast so the helper is forward-compatible with
  // regenerated clients.
  const license =
    (collection as unknown as { license?: string | null }).license ?? null;
  const accessRights =
    (collection as unknown as { accessRights?: string | null }).accessRights ??
    null;
  const heroImageUrl =
    (collection as unknown as { heroImageUrl?: string | null }).heroImageUrl ??
    null;

  const description = collection.description ?? "";
  const name = collection.name ?? "";

  const checks: MetadataCheck[] = [
    {
      id: "name",
      label: "Collection has a name",
      passed: name.trim().length > 0,
      points: 10,
      why: "DataCite §3 (Title) — required for every published dataset.",
      deepLink: null,
      actionLabel: "Set name",
    },
    {
      id: "description",
      label: `Description ≥ ${DESCRIPTION_MIN_CHARS} characters`,
      passed: description.trim().length >= DESCRIPTION_MIN_CHARS,
      points: 15,
      why:
        `DataCite §17 (Description) + Zenodo completeness — at least ` +
        `${DESCRIPTION_MIN_CHARS} characters distinguishes a stub from a ` +
        `real abstract.`,
      deepLink: DEEP_LINK_IDS.description,
      actionLabel: "Add description",
    },
    {
      id: "license",
      label: "License (SPDX) set",
      passed: typeof license === "string" && license.trim().length > 0,
      points: 20,
      why:
        "DataCite §16 (Rights) + FAIR R1.1 — the single biggest blocker " +
        "to publication. Without a license the dataset is legally unusable.",
      deepLink: DEEP_LINK_IDS.license,
      actionLabel: "Add license",
    },
    {
      id: "accessRights",
      label: "Access rights set",
      passed:
        typeof accessRights === "string" && accessRights.trim().length > 0,
      points: 10,
      why:
        "DataCite §16 (Rights) + FAIR A1.2 — declares open / restricted / " +
        "closed / embargoed. Required for Horizon Europe embargoed deposits.",
      deepLink: DEEP_LINK_IDS.accessRights,
      actionLabel: "Set access rights",
    },
    {
      id: "creatorOrcid",
      label: "Creator has ORCID",
      passed:
        typeof creatorOrcid === "string" && creatorOrcid.trim().length > 0,
      points: 10,
      why:
        "DataCite §2 (Creator) — researcher PID. Without ORCID the " +
        "citation falls back to a bare username with no resolver.",
      deepLink: null,
      actionLabel: "Set ORCID on /me",
    },
    {
      id: "semanticAnnotation",
      label: "At least one semantic annotation",
      passed: (semanticAnnotationCount ?? 0) > 0,
      points: 10,
      why:
        "FAIR I1 + I2 — controlled-vocabulary annotation makes the " +
        "Collection findable via ontology-aware catalogues.",
      deepLink: DEEP_LINK_IDS.semanticAnnotation,
      actionLabel: "Add annotation",
    },
    {
      id: "labJournal",
      label: "At least one lab journal entry",
      passed: (labJournalCount ?? 0) > 0,
      points: 5,
      why:
        "FAIR R1.2 (provenance) — narrative context that the " +
        "machine-readable graph alone cannot provide.",
      deepLink: DEEP_LINK_IDS.labJournal,
      actionLabel: "Open Lab Journal",
    },
    {
      id: "heroImage",
      label: "Hero image set",
      passed:
        typeof heroImageUrl === "string" && heroImageUrl.trim().length > 0,
      points: 5,
      why:
        "Findability + discoverability — a banner image gives the " +
        "Collection landing a recognisable face in catalog listings.",
      deepLink: DEEP_LINK_IDS.heroImage,
      actionLabel: "Set hero image",
    },
    {
      id: "dataObjects",
      label: "Has at least one DataObject",
      passed: (collection.dataObjectIds?.length ?? 0) > 0,
      points: 15,
      why:
        "FAIR F2 — a Collection with zero DataObjects has nothing " +
        "for harvesters to enumerate.",
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
