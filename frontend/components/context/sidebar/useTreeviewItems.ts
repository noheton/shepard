import {
  DataObjectsApi,
  type DataObjectListItemV2,
  type DataObjectDetail,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { buildTreeviewItems, type TreeviewItem } from "./treeviewItem";
import { useOpenedItems } from "./useOpenedItems";

/**
 * SIDEBAR-LAZY-TREE (2026-06-25) — the sidebar tree is v2-only, appId-native,
 * AND lazy-loaded.
 *
 * History: the V2-SWEEP Wave 1 rewrite (2026-06-10) loaded the WHOLE tree from
 * the paged v2 list (up to 100 pages × 200 = 20k DataObjects) and materialised
 * it client-side. At MFFD scale (8 483 DataObjects, ~43 sequential fetches)
 * this was slow and choked the browser.
 *
 * Fix: load only one hierarchy level at a time.
 *   - Initial load: `?topLevel=true` → the ~33 root DataObjects.
 *   - On expand: `?parentAppId=<node.appId>` → that node's direct children,
 *     wired through Vuetify's `load-children` Promise callback (fires once, the
 *     first time a node whose `children` is an empty array is expanded).
 *   - Cache: once a node's children are loaded they stay in the tree, so
 *     re-expand never refetches.
 *
 * Linkage stays appId-only (no numeric id on routes/links). The collection
 * route param goes straight on the v2 path; the backend's EntityIdResolver
 * accepts a UUID v7 or a legacy numeric string transparently.
 */

const PAGE_SIZE = 200;
// Safety valve per level: a single hierarchy level with >20k siblings is the
// wrong affordance anyway (see UX-auditor findings on virtualization); log and
// render what we have.
const MAX_PAGES = 100;

// The generated client predates the new `parentAppId` / `topLevel` request
// params (and the `parentAppId` / `childrenAppIds` response fields). Widen the
// request locally; the backend honours both.
type LazyListParams = {
  collectionAppId: string;
  fields?: string;
  page?: number;
  pageSize?: number;
  parentAppId?: string;
  topLevel?: boolean;
};

const TREE_FIELDS = "id,appId,name,childrenAppIds";

export const useTreeviewItems = (routeParams: Ref<CollectionRouteParams>) => {
  const dataObjectV2Api = useV2ShepardApi(DataObjectsApi);
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  // Distinct from `loading` so the template can render an explicit error
  // state instead of an infinite spinner when the v2 list call fails.
  const loadError = ref<boolean>(false);
  const { openedTreeviewItems, addOpen, collapseItem } = useOpenedItems();

  /** Fetch one hierarchy level (roots when topLevel; children of parentAppId otherwise). */
  async function fetchLevel(
    collectionId: string,
    opts: { topLevel?: boolean; parentAppId?: string },
  ): Promise<DataObjectListItemV2[]> {
    const rows: DataObjectListItemV2[] = [];
    for (let page = 0; page < MAX_PAGES; page++) {
      const params: LazyListParams = {
        collectionAppId: collectionId,
        fields: TREE_FIELDS,
        page,
        pageSize: PAGE_SIZE,
        ...(opts.topLevel ? { topLevel: true } : {}),
        ...(opts.parentAppId ? { parentAppId: opts.parentAppId } : {}),
      };
      const batch = await dataObjectV2Api.value.listDataObjects(
        params as unknown as Parameters<
          typeof dataObjectV2Api.value.listDataObjects
        >[0],
      );
      rows.push(...batch);
      if (batch.length < PAGE_SIZE) break;
    }
    return rows;
  }

  /** Initial root load (`?topLevel=true`). */
  async function fetchRoots(collectionId: string) {
    loadError.value = false;
    try {
      const rows = await fetchLevel(collectionId, { topLevel: true });
      treeviewItems.value = buildTreeviewItems(rows);
    } catch (error) {
      // Render an empty tree with an explicit error sentinel rather than
      // leaving treeviewItems undefined (which the template reads as
      // "still loading" and spins indefinitely).
      treeviewItems.value = [];
      loadError.value = true;
      handleError(error, "listDataObjects");
    }
  }

  /**
   * Vuetify `load-children` callback. Fired ONCE the first time a node whose
   * `children` is an empty array (`[]`) is expanded. Mutates `item.children`
   * in place with the fetched direct children. Errors are swallowed (logged)
   * so a single failed branch doesn't break the whole tree; the placeholder
   * stays `[]` and the user can retry by collapsing + re-expanding.
   */
  async function loadChildren(rawItem: unknown) {
    // Vuetify types `load-children` as `(item: unknown) => Promise<void>`;
    // our items are TreeviewItems keyed on `id` (the appId).
    const item = rawItem as TreeviewItem;
    // Guard: Vuetify only calls this when children is []; belt-and-braces so a
    // double-fire (re-expand) never refetches.
    if (item.children && item.children.length > 0) return;
    try {
      const rows = await fetchLevel(routeParams.value.collectionId, {
        parentAppId: item.id,
      });
      item.children = buildTreeviewItems(rows, item.id);
    } catch (error) {
      handleError(error, "listDataObjects");
      // Leave the placeholder as-is so the chevron remains and a re-expand
      // retries. (Vuetify caches on a non-empty result; an empty [] re-fires.)
    }
  }

  /**
   * Deep-link ancestor walk. With lazy loading we no longer hold the full
   * tree, so to reveal a routed `[dataObjectId]` we walk UP via the detail
   * endpoint's `parentSummary` (recursively) to build the ancestor appId chain
   * root→…→parent, then lazy-load each level down so the node becomes visible.
   *
   * Best-effort: any fetch failure falls back to expanding whatever is loaded.
   */
  async function expandUpToItem(itemId: string) {
    // 1. Already in the loaded tree? Expand its ancestors directly.
    const loaded = findLoadedPath(itemId);
    if (loaded) {
      addOpen(loaded);
      return;
    }
    // 2. Walk ancestors via parentSummary to build the root→parent chain.
    const collectionId = routeParams.value.collectionId;
    const chain: string[] = []; // ancestor appIds, target-first
    let cursor: string | undefined = itemId;
    const seen = new Set<string>();
    try {
      while (cursor && !seen.has(cursor)) {
        seen.add(cursor);
        const detail: DataObjectDetail =
          await dataObjectV2Api.value.getDataObjectV2({
            collectionAppId: collectionId,
            dataObjectAppId: cursor,
          });
        const parentAppId = detail.parentSummary?.appId ?? undefined;
        if (!parentAppId) break;
        chain.push(parentAppId);
        cursor = parentAppId;
      }
    } catch (error) {
      // Best-effort: expand what we already have (the loaded roots) and bail.
      handleError(error, "getDataObjectV2");
      return;
    }
    // chain is target-first (parent, grandparent, …, root). Reverse to
    // root→parent and lazy-load each level so the next is reachable.
    const rootToParent = chain.reverse();
    for (const ancestorAppId of rootToParent) {
      const node = findLoadedNode(ancestorAppId);
      if (node) await loadChildren(node);
    }
    // Now the path should be loaded; open the ancestor chain.
    addOpen(rootToParent);
  }

  /** Find a node by appId anywhere in the currently-loaded tree. */
  function findLoadedNode(appId: string): TreeviewItem | undefined {
    function walk(items: TreeviewItem[]): TreeviewItem | undefined {
      for (const item of items) {
        if (item.id === appId) return item;
        if (item.children && item.children.length > 0) {
          const found = walk(item.children);
          if (found) return found;
        }
      }
      return undefined;
    }
    if (!treeviewItems.value) return undefined;
    return walk(treeviewItems.value);
  }

  /**
   * If `itemId` is already in the loaded tree, return its ancestor appId path
   * (root→…→parent, excluding the item). Matches appId OR the legacy numeric
   * deep-link form (stringified `numericId`).
   */
  function findLoadedPath(itemId: string): string[] | undefined {
    function walk(items: TreeviewItem[]): string[] | undefined {
      for (const item of items) {
        if (item.id === itemId || String(item.numericId) === itemId) {
          return [];
        }
        if (item.children && item.children.length > 0) {
          const found = walk(item.children);
          if (found) return [item.id, ...found];
        }
      }
      return undefined;
    }
    if (!treeviewItems.value) return undefined;
    return walk(treeviewItems.value);
  }

  async function initialLoad() {
    loading.value = true;
    await fetchRoots(routeParams.value.collectionId);
    const did = routeParams.value.dataObjectId;
    if (did !== undefined) await expandUpToItem(did);
    loading.value = false;
  }

  initialLoad();

  watch(routeParams, async (_, oldParams) => {
    if (
      routeParams.value.collectionId &&
      oldParams.collectionId !== routeParams.value.collectionId
    ) {
      loading.value = true;
      await fetchRoots(routeParams.value.collectionId);
      loading.value = false;
    }
    const did = routeParams.value.dataObjectId;
    if (did !== undefined) await expandUpToItem(did);
  });

  async function refreshItems() {
    loading.value = true;
    await fetchRoots(routeParams.value.collectionId);
    loading.value = false;
    // appIds are stable across refreshes — the opened set carries over as-is.
    // Re-expand the routed node so a create/delete refresh keeps it revealed.
    const did = routeParams.value.dataObjectId;
    if (did !== undefined) await expandUpToItem(did);
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loading,
    loadError,
    refreshItems,
    collapseItem,
    loadChildren,
  };
};
