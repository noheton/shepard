/**
 * TOOLS-NAV-01 — pure data + helpers for the `/tools` landing.
 *
 * Kept as a plain module so the tile inventory is testable without mounting
 * the Vue component (mirrors `sectionLanding.ts` shape — but route-based,
 * not hash-fragment-based, because `/tools` aggregates standalone routes
 * rather than fragments of one big page).
 *
 * The Scene-graphs tile points to /scene-graphs (the landing/picker added by
 * UI-1920-SCENEGRAPH-NAV). The primary entry point remains in-context from a
 * URDF/RDK FileReference detail page (OpenIn3dViewButton) per the
 * "tool entry points are in-context first" CLAUDE.md rule; this tile is the
 * fallback for entry-less starts.
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
    to: "/shapes/render",
    title: "Shapes render playground",
    description: "Render URDF / mesh / spatial-shape previews.",
    icon: "mdi-cube-scan",
  },
  {
    to: "/tools/form-preview",
    title: "Form preview",
    description:
      "Compile a data-kind template's SHACL shape into its form descriptor (BTKVS-B2).",
    icon: "mdi-form-select",
  },
  {
    to: "/scene-graphs",
    title: "Scene-graph viewer",
    description:
      "Open a URDF robot model in 3D — frames, joints, and live channel bindings via vis-trace3d.",
    icon: "mdi-cube-scan",
  },
];

// UU2 (2026-05-31): `isPlausibleAppId` lives in `utils/idShape.ts` (which
// also exports `isNumericLegacyId` for the stale-URL hint). Explicit
// importers used to take this from here — they now import from `./idShape`
// directly. Nuxt auto-import sees only one source, no duplicate warning.
