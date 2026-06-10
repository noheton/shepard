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
 * Build the fully-materialised tree from the exhaustive v2 list
 * (`GET /v2/collections/{collectionAppId}/data-objects`). The v2 list rows
 * carry `appId` plus the numeric `id`/`parentId`/`childrenIds` linkage; the
 * numeric linkage is resolved to appIds here and never leaves this module
 * except as `numericId` (see above).
 *
 * Rows without an appId cannot be addressed by the v2-only UI and are
 * skipped (post-reset data always carries one).
 */
export function buildTreeviewItems(
  rows: DataObjectListItemV2[],
): TreeviewItem[] {
  const byNumericId = new Map<number, TreeviewItem>();
  const parentNumericIdOf = new Map<number, number>();

  for (const row of rows) {
    if (!row.appId) continue;
    byNumericId.set(row.id, {
      id: row.appId,
      numericId: row.id,
      title: row.name,
      children: undefined,
      childrenIds: [],
      parentId: undefined,
    });
    if (row.parentId != null) parentNumericIdOf.set(row.id, row.parentId);
  }

  const roots: TreeviewItem[] = [];
  for (const item of byNumericId.values()) {
    const parentNumericId = parentNumericIdOf.get(item.numericId);
    const parent =
      parentNumericId !== undefined
        ? byNumericId.get(parentNumericId)
        : undefined;
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

  // Stable creation order — numeric ids are creation-ordered (the pre-Wave-1
  // tree sorted by numeric id; appIds are UUID v7 so time-ordered too, but
  // numeric keeps byte-identical ordering with the previous behaviour).
  const sortRec = (items: TreeviewItem[]) => {
    items.sort((a, b) => a.numericId - b.numericId);
    for (const item of items) {
      if (item.children) sortRec(item.children);
    }
  };
  sortRec(roots);
  return roots;
}
