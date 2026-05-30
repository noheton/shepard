/**
 * TOOLS-NAV-01 — pure data + helpers for the `/tools` landing.
 *
 * Kept as a plain module so the tile inventory is testable without mounting
 * the Vue component (mirrors `sectionLanding.ts` shape — but route-based,
 * not hash-fragment-based, because `/tools` aggregates standalone routes
 * rather than fragments of one big page).
 *
 * Also exports a tiny appId-shape validator used by `/scene-graphs/index.vue`
 * (SCENEGRAPH-NAV-01) so a typo doesn't fire a backend request.
 */

export interface ToolTile {
  to: string;
  title: string;
  description: string;
  icon: string;
}

/**
 * Tiles for `/tools`. Order = display order. Sourced from
 * `SemanticPane.vue`'s research-tools grouping plus the new
 * scene-graphs landing (SCENEGRAPH-NAV-01) and the shapes/render
 * playground.
 */
export const TOOLS_TILES: ToolTile[] = [
  {
    to: "/semantic/vocabularies",
    title: "Vocabularies",
    description: "Browse the loaded ontologies + predicate inventory.",
    icon: "mdi-bookshelf",
  },
  {
    to: "/semantic/sparql",
    title: "SPARQL playground",
    description: "Query the semantic graph directly.",
    icon: "mdi-code-braces",
  },
  {
    to: "/shapes/validate",
    title: "Shape validator",
    description: "Run SHACL conformance against shape definitions.",
    icon: "mdi-check-decagram-outline",
  },
  {
    to: "/snapshots/diff",
    title: "Snapshot diff",
    description: "Compare collection snapshots across time.",
    icon: "mdi-vector-difference",
  },
  {
    to: "/scene-graphs",
    title: "Scene graphs",
    description: "Coordinate-frame trees + joints for digital twins.",
    icon: "mdi-graph-outline",
  },
  {
    to: "/shapes/render",
    title: "Shapes render playground",
    description: "Render URDF / mesh / spatial-shape previews.",
    icon: "mdi-cube-scan",
  },
];

/**
 * Loose appId shape check — UUID v7 is 36 chars, lowercase hex + hyphens.
 * Used by the scene-graphs "Open by appId" form to short-circuit obvious
 * typos before round-tripping to the backend.
 *
 * Intentionally loose (doesn't enforce v7-specific bits) — we just want
 * "is this plausibly an appId" not "is this a cryptographically valid v7".
 */
export function isPlausibleAppId(input: string | null | undefined): boolean {
  if (!input) return false;
  const trimmed = input.trim().toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
    trimmed,
  );
}
