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
    endpoint: "/v2/admin/sql-timeseries/config",
    backlogRow: "P10c",
    designDoc: "aidocs/platform/29-p10-implementation-design.md",
    backend: "shipped",
  },
  {
    slug: "notifications-admin",
    surface: "admin",
    title: "Notification transports",
    subtitle:
      "Configure SMTP, Matrix, and in-app notification delivery, and send a smoke-test from each transport. Full pane coming with NTF1-UI.",
    endpoint: null,
    backlogRow: "NTF1",
    designDoc: "aidocs/integrations/40-notification-system.md",
    backend: "partial",
  },
  {
    slug: "instance-admins",
    surface: "admin",
    title: "Instance administrators",
    subtitle:
      "Grant or revoke the instance-admin role for other users. Today the API is callable; the UI lands with ADM-MANAGE.",
    endpoint: "/v2/admin/instance-admins",
    backlogRow: "ADM-MANAGE",
    designDoc: "aidocs/16-dispatcher-backlog.md",
    backend: "shipped",
  },
  {
    slug: "users-orcid",
    surface: "admin",
    title: "User ORCID overrides",
    subtitle:
      "Set or clear a user's ORCID iD when they cannot themselves (deactivated account, audit hand-off). Admin override of /v2/users/me/orcid.",
    endpoint: null,
    backlogRow: "ADM-USR-ORCID",
    designDoc: "aidocs/16-dispatcher-backlog.md",
    backend: "shipped",
  },
  {
    slug: "users-git",
    surface: "admin",
    title: "User git credentials",
    subtitle:
      "Issue or rotate git-host credentials for other users on their behalf (importer / wiki-writer plugin support).",
    endpoint: null,
    backlogRow: "ADM-USR-GIT",
    designDoc: "aidocs/integrations/47-dev-experience-and-plugin-system.md",
    backend: "shipped",
  },
  {
    slug: "ai-config",
    surface: "admin",
    title: "AI configuration",
    subtitle:
      "Per-instance LLM provider fallback (base URL, model, API key). Resolution rule: user-key → admin-fallback → hidden. Designed; not yet shipped.",
    endpoint: null,
    backlogRow: "AI1a",
    designDoc: "aidocs/integrations/97-shepard-plugin-ai-design.md",
    backend: "designed",
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
      "Browse HDF5 datasets stored in the HSDS sidecar. Currently a placeholder — download via /v2/hdf-containers/{id}/file works today.",
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
];

export const EXPECTED_PLACEHOLDER_COUNT = PLACEHOLDER_ENTRIES.length;

export function findPlaceholder(slug: string): PlaceholderEntry | undefined {
  return PLACEHOLDER_ENTRIES.find((e) => e.slug === slug);
}

export function placeholdersBySurface(
  surface: PlaceholderEntry["surface"],
): PlaceholderEntry[] {
  return PLACEHOLDER_ENTRIES.filter((e) => e.surface === surface);
}
