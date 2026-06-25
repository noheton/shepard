import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";

/**
 * SIDEBAR-LAZY-TREE (2026-06-25): the treeview is appId-native AND
 * lazy-loaded.
 *
 * `id` is the DataObject's appId (UUID v7) — it is the value the v-treeview
 * keys on, the value sidebar links route on, and the value compared against
 * the `[dataObjectId]` route param. The numeric Neo4j id is retained as
 * `numericId` ONLY for the named v1-only dialog inputs (create-dialog
 * ParentInput/PredecessorInput — backlog SIDEBAR-V2-CREATE in aidocs/16);
 * it never appears in a route, link, or emit.
 *
 * Lazy contract: a node is expandable iff it has at least one child. Vuetify's
 * `load-children` prop fires the first time a user expands a node whose
 * `children` is an EMPTY ARRAY (`[]`). So:
 *   - expandable node → `children: []` (placeholder; chevron shows, lazy-fetch
 *     replaces it on first expand)
 *   - leaf node → `children: undefined` (no `children` key → no chevron)
 * The whole tree is NEVER materialised client-side; each level is fetched on
 * demand. This is the MFFD-scale fix (8 483 DataObjects → ~43 eager fetches
 * before, now one fetch of ~33 roots, children on expand).
 */
export interface TreeviewItem {
  /** appId (UUID v7) — v2 identity; used for routes, treeview value, opened state. */
  id: string;
  /**
   * Numeric Neo4j id resolved from the loaded v2 list row. Internal-only:
   * fed to the v1-only create/edit dialog plumbing (documented exception,
   * SIDEBAR-V2-CREATE). Never placed on a route or link.
   */
  numericId: number;
  title: string;
  /**
   * Lazy children:
   *   - `[]`        → expandable, not yet loaded (Vuetify fires load-children)
   *   - non-empty   → loaded children
   *   - `undefined` → leaf (no chevron)
   */
  children: TreeviewItem[] | undefined;
  /** appIds of direct children (empty array = leaf; drives the chevron/dot). */
  childrenIds: string[] | undefined;
  /** Parent appId, if any. */
  parentId: string | undefined;
}

/**
 * The v2 list row, augmented with the appId-native parent/children linkage
 * (`parentAppId`/`childrenAppIds`) added to `DataObjectListItemV2IO` for the
 * sidebar tree. The generated client type predates these fields (and the new
 * `parentAppId`/`topLevel` request params), so we widen locally; the backend
 * always sends them on the list endpoint.
 */
export type ListRow = DataObjectListItemV2 & {
  parentAppId?: string | null;
  childrenAppIds?: string[] | null;
};

/**
 * Map a single v2 list row to a {@link TreeviewItem}, deciding leaf vs.
 * expandable from `childrenAppIds`.
 *
 * Rows without an appId cannot be addressed by the v2-only UI and yield
 * `undefined` (callers filter them out). Post-reset data always carries one.
 *
 * @param parentAppId the appId of the parent the row was fetched under
 *   (for roots: undefined). Used to populate `parentId` without a second
 *   lookup, since the lazy fetch already knows the parent.
 */
export function mapRowToItem(
  row: ListRow,
  parentAppId?: string,
): TreeviewItem | undefined {
  if (!row.appId) return undefined;
  const childAppIds = (row.childrenAppIds ?? []).filter(
    (c): c is string => !!c,
  );
  const hasChildren = childAppIds.length > 0;
  return {
    id: row.appId,
    // numericId is best-effort: the v2 list suppresses the numeric id, so it
    // is usually -1. It is only consumed by the v1-only create-dialog
    // plumbing (SIDEBAR-V2-CREATE) and never appears on a route or link.
    numericId: row.id ?? -1,
    title: row.name,
    // Expandable → empty-array placeholder (Vuetify lazy trigger); leaf →
    // undefined (no chevron).
    children: hasChildren ? [] : undefined,
    childrenIds: childAppIds,
    parentId: parentAppId,
  };
}

/**
 * Map a page of v2 list rows (one hierarchy level) to {@link TreeviewItem}s,
 * dropping rows without an appId. Used for both the initial root load
 * (`?topLevel=true`) and each lazy child fetch (`?parentAppId=…`).
 *
 * Creation order: appId is UUID v7 (time-ordered), so a lexicographic appId
 * sort reproduces creation order.
 */
export function buildTreeviewItems(
  rows: DataObjectListItemV2[],
  parentAppId?: string,
): TreeviewItem[] {
  const items: TreeviewItem[] = [];
  for (const row of rows as ListRow[]) {
    const item = mapRowToItem(row, parentAppId);
    if (item) items.push(item);
  }
  items.sort((a, b) => a.id.localeCompare(b.id));
  return items;
}
