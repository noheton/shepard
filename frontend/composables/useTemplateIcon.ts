/**
 * TEMPLATE-ICONS-2-FE — composable resolving the MDI icon for a
 * DataObject (or any entity rendered like one). Falls back to a
 * per-kind default when the template carries no `iconKey`.
 *
 * Design: aidocs/integrations/122 §6.1.
 *
 * Usage:
 *
 *   const icon = useTemplateIcon(row.primaryTemplate, "DataObject");
 *   // -> "mdi-layers" when the template's iconKey is set, otherwise
 *   //    "mdi-circle-medium" (the per-kind default for DataObject)
 *
 * Render sites pass the parent template (when known) plus a `kindHint`
 * naming the entity kind ("DataObject" / "Collection" / "Project" /
 * a specific reference kind). The composable is a pure function — no
 * reactive state — so it is trivially memoisable when render volume
 * is high.
 */

import type { ShepardTemplate } from "@dlr-shepard/backend-client";

/**
 * Per-kind defaults table — used when no template is supplied or the
 * template carries no iconKey. The table is intentionally narrow
 * (~9 entries); the source of truth for "which icon should this
 * entity show?" is the template's iconKey, not this table.
 *
 * Stays aligned with aidocs/integrations/122 §3.
 */
const PER_KIND_DEFAULT_ICON: Readonly<Record<string, string>> = Object.freeze({
  Collection: "mdi-folder-multiple",
  Project: "mdi-flag",
  DataObject: "mdi-circle-medium",
  FileReference: "mdi-file-outline",
  FileBundleReference: "mdi-folder-zip-outline",
  TimeseriesReference: "mdi-chart-line",
  SpatialDataReference: "mdi-cube-outline",
  SceneGraph: "mdi-graph-outline",
  LabJournalEntry: "mdi-notebook-outline",
});

/**
 * Catch-all default for unknown kinds. The DataObject default doubles
 * as the universal fallback so a typo in `kindHint` never produces a
 * missing-icon UI glitch.
 */
const GENERIC_DEFAULT_ICON = "mdi-circle-medium";

/**
 * Lookup the per-kind default icon for a given kind string. Exported
 * for unit tests + render sites that need just the fallback (e.g.
 * placeholder rows before the template is loaded).
 */
export function defaultIconForKind(kindHint: string | null | undefined): string {
  if (!kindHint) return GENERIC_DEFAULT_ICON;
  return PER_KIND_DEFAULT_ICON[kindHint] ?? GENERIC_DEFAULT_ICON;
}

/**
 * Resolve the icon for an entity. The template's `iconKey` wins when
 * present; otherwise the per-kind default applies.
 *
 * Pure function — safe to call in computed properties, render
 * functions, table cells, etc.
 *
 * @param template The parent ShepardTemplate, or null/undefined when
 *                 the entity has no template attached or it hasn't
 *                 loaded yet.
 * @param kindHint The entity kind name ("DataObject" / "Collection" /
 *                 "Project" / a reference-kind name). When omitted,
 *                 falls back to the generic default.
 */
export function useTemplateIcon(
  template: ShepardTemplate | null | undefined,
  kindHint?: string | null,
): string {
  if (template?.iconKey) return template.iconKey;
  return defaultIconForKind(kindHint);
}
