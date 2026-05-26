/**
 * Pure helper for CollectionLineageGraph empty-state logic.
 * Exported here (not from the .vue SFC) so Vitest tests can import it
 * without needing to mount Vuetify components.
 */
export type LineageState = "loading" | "no-dos" | "no-edges" | "graph";

export function computeLineageState(
  isLoading: boolean,
  items: unknown[],
  hasEdges: boolean,
): LineageState {
  if (isLoading) return "loading";
  if (items.length === 0) return "no-dos";
  if (!hasEdges) return "no-edges";
  return "graph";
}
