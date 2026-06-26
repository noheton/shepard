// placeholderRegistry — single source of truth for the no-UI-gap
// placeholder routes shipped 2026-05-24. See
// aidocs/agent-findings/no-ui-gap-survey-2026-05-24.md.
//
// Each entry pairs a slug with the metadata the placeholder components
// render. The registry is testable as a plain TS module (no Vue mounting
// required) — see tests/unit/placeholderRegistry.test.ts.

export type BackendStatus =
  | "shipped"
  | "partial"
  | "designed"
  | "queued"
  | "stub";

export interface PlaceholderEntry {
  /** Stable slug — admin fragment or route-segment name */
  slug: string;
  /** Where in the UI this placeholder is mounted */
  surface: "admin" | "profile" | "route";
  /** Human-readable title shown by PlaceholderPageHeader */
  title: string;
  /** One-line description shown beneath the title */
  subtitle: string;
  /** REST endpoint to dump in advanced mode (null = designed-not-shipped) */
  endpoint: string | null;
  /** Backlog row ID in aidocs/16 (preferred) or design doc shortname */
  backlogRow: string;
  /** Path to the design doc (relative to repo root) */
  designDoc: string;
  /** Backend implementation state */
  backend: BackendStatus;
}

export const PLACEHOLDER_ENTRIES: PlaceholderEntry[] = [
  // ---------- admin tiles (fragment-routed inside /admin) ----------
  {
    slug: "file-migration",
    surface: "admin",
    title: "File migration",
    subtitle:
      "Move file payloads between storage backends (e.g. legacy MinIO → Garage). Status surfaces here once the UI lands.",
    endpoint: "/v2/admin/files/migrate/status",
    backlogRow: "FS1e",
    designDoc: "aidocs/data/35-hdf5-hsds-implementation-design.md",
    backend: "shipped",
  },
  {
    slug: "sql-timeseries",
    surface: "admin",
    title: "SQL-over-HTTP for timeseries",
    subtitle:
      "Runtime caps for the bulk-read SQL endpoint (POST /v2/sql/timeseries). Flip max-rows and max-duration without a restart.",
    endpoint: "/v2/admin/config/sql-timeseries",
    backlogRow: "P10c",
    designDoc: "aidocs/platform/29-p10-implementation-design.md",
    backend: "shipped",
  },
  {
    // PLACEHOLDER-REPLACE-NTF1 (2026-05-31): real pane shipped as
    // AdminNotificationsPane.vue; registry entry retained so the
    // EXPECTED_PLACEHOLDER_COUNT contract holds and so partial-backend
    // tracking stays surfaced (SMTP + Matrix transport CRUD pending).
    slug: "notifications-admin",
    surface: "admin",
    title: "Notification transports",
    subtitle:
      "Configure SMTP, Matrix, and in-app notification delivery, and send a smoke-test from each transport. In-app pane shipped 2026-05-31; SMTP/Matrix transport CRUD pending (NTF1-BACKEND-*).",
    endpoint: "/v2/admin/notifications/test",
    backlogRow: "NTF1",
    designDoc: "aidocs/integrations/40-notification-system.md",
    backend: "partial",
  },
  {
    // PLACEHOLDER-REPLACE-ADM-MANAGE (2026-05-31): real pane shipped as
    // AdminInstanceAdminsPane.vue. Registry entry retained for
    // EXPECTED_PLACEHOLDER_COUNT stability and surface tracking.
    slug: "instance-admins",
    surface: "admin",
    title: "Instance administrators",
    subtitle:
      "Grant or revoke the instance-admin role for other users. Real pane shipped 2026-05-31 — calls GET/POST/DELETE /v2/admin/instance-admins.",
    endpoint: "/v2/admin/instance-admins",
    backlogRow: "ADM-MANAGE",
    designDoc: "aidocs/16-dispatcher-backlog.md",
    backend: "shipped",
  },
  {
    // PLACEHOLDER-REPLACE-ADM-USR-ORCID (2026-05-31): real pane shipped
    // as AdminUserOrcidPane.vue.
    slug: "users-orcid",
    surface: "admin",
    title: "User ORCID overrides",
    subtitle:
      "Set or clear a user's ORCID iD when they cannot themselves (deactivated account, audit hand-off). Real pane shipped 2026-05-31 — PATCH /v2/admin/users/{username}/orcid.",
    endpoint: "/v2/admin/users/{username}/orcid",
    backlogRow: "ADM-USR-ORCID",
    designDoc: "aidocs/16-dispatcher-backlog.md",
    backend: "shipped",
  },
  {
    // PLACEHOLDER-REPLACE-ADM-USR-GIT (2026-05-31): partial pane shipped
    // as AdminUserGitPane.vue (set/replace credential per host). Backend
    // gap (GET-for-other-users, /rotate, lastRotatedAt) tracked in
    // ADM-USR-GIT-BACKEND-1.
    slug: "users-git",
    surface: "admin",
    title: "User git credentials",
    subtitle:
      "Issue or rotate git-host credentials for other users on their behalf (importer / wiki-writer plugin support). Partial pane shipped 2026-05-31; list/rotate endpoints pending (ADM-USR-GIT-BACKEND-1).",
    endpoint: "/v2/admin/users/{username}/git-credentials",
    backlogRow: "ADM-USR-GIT",
    designDoc: "aidocs/integrations/47-dev-experience-and-plugin-system.md",
    backend: "partial",
  },
  {
    // PLACEHOLDER-REPLACE-AI-CONFIG (2026-06-26): real pane shipped as
    // AdminAiConfigPane.vue; registry entry retained so the
    // EXPECTED_PLACEHOLDER_COUNT contract holds.
    slug: "ai-config",
    surface: "admin",
    title: "AI configuration",
    subtitle:
      "Per-instance LLM capability slot configs (TEXT, FAST_TEXT, IMAGE_GEN, VISION, EMBEDDING, STRUCTURED, TRANSCRIPTION, MODERATION). PATCH body keyed by capability name. Real pane shipped 2026-06-26 — calls GET/PATCH /v2/admin/config/ai.",
    endpoint: "/v2/admin/config/ai",
    backlogRow: "APISIMP-AI-ADMIN-REST",
    designDoc: "aidocs/integrations/97-shepard-plugin-ai-design.md",
    backend: "shipped",
  },
  {
    slug: "backup",
    surface: "admin",
    title: "Backup configuration",
    subtitle:
      "Nightly pg_dump + Wal-G archiving to Garage, plus Neo4j and Mongo snapshot policy. Designed; ships with PG-COLLAPSE-002.",
    endpoint: null,
    backlogRow: "PG-COLLAPSE-002",
    designDoc: "aidocs/strategy/105-postgres-multitenant-decision.md",
    backend: "designed",
  },
  // ---------- profile tiles (fragment-routed inside /me) ----------
  {
    slug: "ai-settings",
    surface: "profile",
    title: "AI settings",
    subtitle:
      "Per-user LLM provider config (base URL, model, API key). Designed; will resolve against the admin fallback when absent.",
    endpoint: null,
    backlogRow: "AI1a",
    designDoc: "aidocs/integrations/97-shepard-plugin-ai-design.md",
    backend: "designed",
  },
  // ---------- top-level browsable routes ----------
  {
    slug: "shapes-validate",
    surface: "route",
    title: "SHACL validation playground",
    subtitle:
      "Paste a Turtle data graph + a SHACL shape graph and get a validation report back. Backed by POST /v2/shapes/validate (Jena SHACL).",
    endpoint: null,
    backlogRow: "SHAPES-V",
    designDoc: "aidocs/semantics/100-consistent-semantic-annotation-design.md",
    backend: "shipped",
  },
  {
    slug: "snapshots-diff",
    surface: "route",
    title: "Snapshot diff viewer",
    subtitle:
      "Compare two Collection snapshots — what DataObjects changed, what references added/removed. Backed by GET /v2/snapshots/{a}/diff/{b}.",
    endpoint: null,
    backlogRow: "SNAP-DIFF",
    designDoc: "aidocs/platform/25-neo4j-id-migration-design.md",
    backend: "shipped",
  },
  {
    slug: "hdf-container",
    surface: "route",
    title: "HDF container browser",
    subtitle:
      "Browse HDF5 datasets stored in the HSDS sidecar. Currently a placeholder — download via /v2/containers/{appId}/file works today.",
    endpoint: null,
    backlogRow: "A5",
    designDoc: "aidocs/data/35-hdf5-hsds-implementation-design.md",
    backend: "partial",
  },
  {
    slug: "semantic-sparql",
    surface: "route",
    title: "SPARQL query interface",
    subtitle:
      "Run read-only SPARQL SELECT/ASK queries against the internal n10s-managed graph. Backed by GET/POST /v2/semantic/{repoAppId}/sparql.",
    endpoint: null,
    backlogRow: "N1f",
    designDoc: "aidocs/semantics/100-consistent-semantic-annotation-design.md",
    backend: "shipped",
  },
  {
    slug: "semantic-vocabularies",
    surface: "route",
    title: "Vocabularies",
    subtitle:
      "Browse the ontology bundles seeded into this instance — what predicates are available for semantic annotation.",
    endpoint: "/v2/admin/semantic/ontologies",
    backlogRow: "SEMA-V6",
    designDoc: "aidocs/semantics/100-consistent-semantic-annotation-design.md",
    backend: "partial",
  },
  {
    slug: "shapes-render",
    surface: "route",
    title: "Shape render playground",
    subtitle:
      "Project a VIEW_RECIPE template's channel bindings onto a focus DataObject — the backend hook that drives Trace3D and other shape-driven renderers.",
    endpoint: null,
    backlogRow: "TPL2b",
    designDoc: "aidocs/semantics/98-shapes-views-and-process-model.md",
    backend: "shipped",
  },
  // PLACEHOLDER-REPLACE-TPL3a-lite shipped 2026-05-31 → AdminOntologyAlignmentPane
  // PLACEHOLDER-REPLACE-FE-PROV-INSTANCE-REGISTRY shipped 2026-05-31 → AdminInstanceRegistryPane
  // TS-SEMANTIC-REST
  {
    slug: "ts-channel-annotations",
    surface: "route",
    title: "Channel Annotations",
    subtitle:
      "Semantic annotations on individual timeseries channels. Channels receive identity annotations automatically via the TS-SEMANTIC-01 dual-write. Additional annotations can be added via POST /v2/containers/{containerAppId}/channels/{channelShepardId}/annotations.",
    endpoint: "/v2/containers/{containerAppId}/channels/{channelShepardId}/annotations",
    backlogRow: "TS-SEMANTIC-REST",
    designDoc: "aidocs/16-dispatcher-backlog.md",
    backend: "shipped",
  },
];

// TS-SEMANTIC-REST: +1 for ts-channel-annotations (2026-05-27)
export const EXPECTED_PLACEHOLDER_COUNT = PLACEHOLDER_ENTRIES.length;

export function findPlaceholder(slug: string): PlaceholderEntry | undefined {
  return PLACEHOLDER_ENTRIES.find((e) => e.slug === slug);
}

export function placeholdersBySurface(
  surface: PlaceholderEntry["surface"],
): PlaceholderEntry[] {
  return PLACEHOLDER_ENTRIES.filter((e) => e.surface === surface);
}
