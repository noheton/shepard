import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";

/**
 * V2-SWEEP Wave 1 (2026-06-10): the treeview is appId-native.
 *
 * `id` is the DataObject's appId (UUID v7) — it is the value the v-treeview
 * keys on, the value sidebar links route on, and the value compared against
 * the `[dataObjectId]` route param. The numeric Neo4j id is retained as
 * `numericId` ONLY for the named v1-only dialog inputs (create-dialog
 * ParentInput/PredecessorInput — backlog SIDEBAR-V2-CREATE in aidocs/16);
 * it never appears in a route, link, or emit.
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
  children: TreeviewItem[] | undefined;
  /** appIds of direct children (empty array = leaf; drives the chevron/dot). */
  childrenIds: string[] | undefined;
  /** Parent appId, if any. */
  parentId: string | undefined;
}

/**
 * The v2 list row, augmented with the appId-native parent/children linkage
 * (`parentAppId`/`childrenAppIds`) added to `DataObjectListItemV2IO` for the
 * sidebar tree. The generated client type predates these fields, so we widen
 * it locally; the backend always sends them on the list endpoint.
 *
 * SIDEBAR-V2-APPID-LINK (2026-06-25): the numeric `id`/`parentId`/`childrenIds`
 * are suppressed on the v2 wire (APISIMP-DO-IO-NUMERIC-ID-LEAK), so the tree
 * is assembled purely from appIds. The old numeric-keyed assembly collapsed
 * the whole tree to one node once `id` became null (operator-surfaced: only
 * one PlyGroup visible).
 */
type ListRow = DataObjectListItemV2 & {
  parentAppId?: string | null;
  childrenAppIds?: string[] | null;
};

/**
 * Build the fully-materialised tree from the exhaustive v2 list
 * (`GET /v2/collections/{collectionAppId}/data-objects`). Linkage is by
 * `appId` only (`parentAppId`); the numeric id is no longer on the wire.
 *
 * Rows without an appId cannot be addressed by the v2-only UI and are
 * skipped (post-reset data always carries one).
 */
export function buildTreeviewItems(
  rows: DataObjectListItemV2[],
): TreeviewItem[] {
  const byAppId = new Map<string, TreeviewItem>();
  const parentAppIdOf = new Map<string, string>();

  for (const row of rows as ListRow[]) {
    if (!row.appId) continue;
    byAppId.set(row.appId, {
      id: row.appId,
      // numericId is best-effort: the v2 list suppresses the numeric id, so it
      // is usually -1. It is only consumed by the v1-only create-dialog
      // plumbing (SIDEBAR-V2-CREATE) and never appears on a route or link.
      numericId: row.id ?? -1,
      title: row.name,
      children: undefined,
      childrenIds: [],
      parentId: undefined,
    });
    if (row.parentAppId) parentAppIdOf.set(row.appId, row.parentAppId);
  }

  const roots: TreeviewItem[] = [];
  for (const item of byAppId.values()) {
    const parentAppId = parentAppIdOf.get(item.id);
    const parent =
      parentAppId !== undefined ? byAppId.get(parentAppId) : undefined;
    if (parent) {
      item.parentId = parent.id;
      parent.children = parent.children ?? [];
      parent.children.push(item);
      parent.childrenIds = parent.childrenIds ?? [];
      parent.childrenIds.push(item.id);
    } else {
      roots.push(item);
    }
  }

  // Creation order: appId is UUID v7 (time-ordered), so a lexicographic appId
  // sort reproduces creation order — the replacement for the old numeric-id
  // sort now that the numeric id is off the wire.
  const sortRec = (items: TreeviewItem[]) => {
    items.sort((a, b) => a.id.localeCompare(b.id));
    for (const item of items) {
      if (item.children) sortRec(item.children);
    }
  };
  sortRec(roots);
  return roots;
}
