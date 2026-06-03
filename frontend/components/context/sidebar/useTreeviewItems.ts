import {
  DataObjectApi,
  ResponseError,
  type DataObject,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { mapToTreeviewItem, type TreeviewItem } from "./treeviewItem";
import { useOpenedItems } from "./useOpenedItems";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): the per-treeview-item DataObject
 * lookup must route through the v2 appId-keyed endpoint
 * `GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`.
 * Pre-fix this composable hit the generated v1 `getDataObject({collectionId,
 * dataObjectId})` expecting numeric Neo4j longs — post-Neo4j-reset Collections
 * carry UUID v7 only and the v1 client 404'd on the stringified id, so the
 * sidebar tree silently broke when a deep-link landed mid-tree (the parent
 * walk in `getPathToItem` could not load any item by id).
 *
 * The list call `getAllDataObjects` stays on v1 — it's not in this BUG's
 * scope. NOTE: the v1 list endpoint
 * `DataObjectRest.getAllDataObjects(@PathParam Long collectionId, ...)`
 * declares a primitive-Long path param, so a UUID-only Collection will 400
 * at the JAX-RS binding before the service ever runs. That means this
 * composable's mid-tree single-item walk is fixed by -005 but the initial
 * tree load remains broken for UUID-only Collections until the v2 list
 * endpoint grows `parentId`/`predecessorId` support (filed as
 * **BUG-COLL-APPID-ROUTE-006** in aidocs/16).
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchDataObjectV2(
  collectionId: string,
  dataObjectId: number,
): Promise<DataObject> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(collectionId)}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(url, { headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  // DataObjectDetailV2IO extends DataObjectIO — treeview item mapping only
  // reads `id`, `name`, `parentId`, `childrenIds`, all of which are present.
  return (await resp.json()) as DataObject;
}

/**
 * BUG-COLL-APPID-ROUTE-006 (2026-06-03): the v1 list endpoint
 * (`DataObjectRest.getAllDataObjects(@PathParam Long collectionId, ...)`)
 * declares a primitive-Long path param, so passing the UUID v7 route param
 * as the v1 collectionId would 400 at the JAX-RS binding before the service
 * ever ran — producing the operator-surfaced infinite spinner on
 * `/collections/{appId}` (LUMEN, 2026-06-03).
 *
 * Fix: the caller resolves the NUMERIC id from the loaded v2 Collection's
 * `.id` (the canonical source) and passes it in as `collectionNumericId`.
 * The treeview's v1 list calls are gated on that ref being defined — until
 * the collection finishes loading the treeview shows the loading spinner,
 * and if the collection fetch errors out it shows an explicit
 * `loadError` instead of spinning forever.
 */
export const useTreeviewItems = (
  routeParams: Ref<CollectionRouteParams>,
  collectionNumericId?: MaybeRefOrGetter<number | undefined>,
) => {
  // BUG-COLL-APPID-ROUTE-001: route dataObjectId is a string (UUID v7 or
  // numeric long). Resolution to the numeric id used by the v1 list happens
  // via the treeview itself (each row carries its numeric `id`).
  const did = () => routeParams.value.dataObjectId as unknown as number | undefined;
  // Resolve the NUMERIC collection id used by the v1 `getAllDataObjects`
  // call. Three sources, in priority: explicit caller-supplied
  // `collectionNumericId` ref (the canonical post-006 path), legacy numeric
  // route param (covers /collections/123 deep links), undefined (UUID route
  // with no loaded collection yet → defer the fetch).
  function resolvedNumericCid(): number | undefined {
    if (collectionNumericId !== undefined) {
      const v = toValue(collectionNumericId);
      if (v != null) return v;
    }
    const n = Number(routeParams.value.collectionId);
    return Number.isInteger(n) && n > 0 ? n : undefined;
  }
  const dataObjectApi = useShepardApi(DataObjectApi);
  const treeviewItems = ref<TreeviewItem[] | undefined>(undefined);
  const loading = ref<boolean>(true);
  // Distinct from `loading` so the template can render an explicit error
  // state instead of an infinite spinner when the v1 list call fails.
  const loadError = ref<boolean>(false);
  const { openedTreeviewItems, addOpen, collapseItem } = useOpenedItems();

  async function fetchTreeviewItems(collectionId: number) {
    loadError.value = false;
    await dataObjectApi.value
      .getAllDataObjects({ collectionId, parentId: -1 })
      .then(response => {
        // initial load of dataobject from a collection - no parents possible
        treeviewItems.value = response
          .map(item => mapToTreeviewItem(item))
          .sort((itemA, itemB) => itemA.id - itemB.id);
        // instead of sorting by 'createdAt' we can sort the treeview items by ID
      })
      .catch(error => {
        // Render an empty tree with an explicit error sentinel rather than
        // leaving treeviewItems undefined (which the template reads as
        // "still loading" and spins indefinitely — the pre-006 shape).
        treeviewItems.value = [];
        loadError.value = true;
        handleError(error, "getAllDataObjects");
      });
  }

  /**
   * @param openLoadedItems will only load and not expand items if disabled, for example to preserve opened state after refreshing
   */
  async function loadAndExpandUpToItem(
    itemId: number,
    openLoadedItems: boolean = true,
  ) {
    const itemWithPath = getItemWithPathIfLoaded(itemId);
    if (itemWithPath) {
      if (openLoadedItems) addOpen(itemWithPath.pathFromRoot);
      return;
    }
    // getPathToItem uses the v2 single-item endpoint, which accepts either
    // a UUID v7 or a numeric string for the collection segment (resolved by
    // `EntityIdResolver` on the backend). Pass the raw route param string
    // so deep-links into a UUID-only Collection still resolve the parent
    // walk even before the numeric id is known.
    const routeCid = routeParams.value.collectionId;
    const parentIds = await getPathToItem(routeCid, itemId);
    for (const id of parentIds) {
      await loadChildrenOfItem(id);
    }
    if (openLoadedItems) addOpen(parentIds);
  }

  async function initialLoad() {
    const cid = resolvedNumericCid();
    if (cid === undefined) {
      // Collection numeric id not yet resolved (v2 fetch in flight). Keep
      // `loading=true`; the watcher below kicks the fetch as soon as the
      // id materialises.
      return;
    }
    loading.value = true;
    await fetchTreeviewItems(cid);
    const d = did();
    if (d !== undefined) {
      await loadAndExpandUpToItem(d);
    }
    loading.value = false;
  }

  initialLoad();

  // Re-run the initial load whenever the numeric collection id transitions
  // from undefined → a real value (i.e. the v2 Collection fetch resolves).
  // We watch the getter so callers passing a Ref or plain getter both work.
  watch(
    () => resolvedNumericCid(),
    async (newCid, oldCid) => {
      if (newCid === undefined || newCid === oldCid) return;
      loading.value = true;
      await fetchTreeviewItems(newCid);
      const d = did();
      if (d !== undefined) {
        await loadAndExpandUpToItem(d);
      }
      loading.value = false;
    },
  );

  watch(routeParams, async (_, oldParams) => {
    const cid = resolvedNumericCid();
    if (cid === undefined) return;
    loading.value = true;
    if (
      routeParams.value.collectionId &&
      oldParams.collectionId !== routeParams.value.collectionId
    ) {
      await fetchTreeviewItems(cid);
    }
    const d = did();
    if (d !== undefined) {
      await loadAndExpandUpToItem(d);
    }
    loading.value = false;
  });

  async function refreshItems() {
    const cid = resolvedNumericCid();
    if (cid === undefined) return;
    loading.value = true;
    await fetchTreeviewItems(cid);
    for (const openedItem of openedTreeviewItems.value) {
      await loadAndExpandUpToItem(openedItem, false);
      await loadChildrenOfItem(openedItem);
    }
    loading.value = false;
  }

  /**
   * Searches for item in the already loaded tree
   *
   * @param itemId Id of the item to be found
   * @returns The item and the path from the root to the item (excluding the id of the item itself). Undefined if the item can't be found in the tree.
   */
  function getItemWithPathIfLoaded(
    itemId: number,
  ): { item: TreeviewItem; pathFromRoot: number[] } | undefined {
    function getTreeviewItemInSubtree(
      dataObjectId: number,
      dataObjectItemList: TreeviewItem[],
    ): { item: TreeviewItem; pathFromRoot: number[] } | undefined {
      for (const item of dataObjectItemList) {
        if (item.id === dataObjectId) {
          return { item, pathFromRoot: [] };
        }
        if (item.children) {
          const found = getTreeviewItemInSubtree(dataObjectId, item.children);
          if (found) {
            return {
              item: found.item,
              pathFromRoot: [item.id, ...found.pathFromRoot],
            };
          }
        }
      }
      return undefined;
    }

    if (!treeviewItems.value) return undefined;
    return getTreeviewItemInSubtree(itemId, treeviewItems.value);
  }

  /**
   * Loads children of item and adds them to the tree.
   *
   * @param itemId Id of the item with children to expand (must be already loaded)
   */
  async function loadChildrenOfItem(itemId: number) {
    const item = getItemWithPathIfLoaded(itemId)?.item;

    if (!item) return;
    if (!item.childrenIds || item.childrenIds.length === 0) {
      item.children = undefined;
      return;
    }
    if (item.children?.length) return;

    const cid = resolvedNumericCid();
    if (cid === undefined) return;
    const children = await fetchChildrenOfItem(cid, item.id, item);

    if (!children) return;

    item.children = children;
    item.childrenIds = children.map(c => c.id);
  }

  /**
   * Walk parents from `itemId` up to the root, returning the list of
   * intermediate ids (excluding the item itself). The `collectionId`
   * parameter is the v2 collection identifier string (UUID v7 or numeric)
   * passed straight to the v2 single-item endpoint, which accepts both
   * shapes via `EntityIdResolver`.
   */
  async function getPathToItem(
    collectionId: string,
    itemId: number,
  ): Promise<number[]> {
    async function getPathFromRootTo(currentPath: number[]): Promise<number[]> {
      const currentRoot = currentPath[0];
      if (currentRoot === undefined) {
        return [];
      }
      const currentRootItemWithPath = getItemWithPathIfLoaded(currentRoot);
      if (currentRootItemWithPath) {
        return [...currentRootItemWithPath.pathFromRoot, ...currentPath];
      }

      const currentRootItem = await fetchTreeviewItem(
        collectionId,
        currentRoot,
        undefined,
      );

      if (!currentRootItem?.parentId) return currentPath;

      // Abort if there is a parent/child cycle
      if (currentPath.some(id => id === currentRootItem.parentId)) return [];

      return await getPathFromRootTo([
        currentRootItem.parentId,
        ...currentPath,
      ]);
    }

    const activeChild = await fetchTreeviewItem(
      collectionId,
      itemId,
      undefined,
    );

    if (!activeChild?.parentId) return [];
    return getPathFromRootTo([activeChild.parentId]);
  }

  return {
    treeviewItems,
    openedTreeviewItems,
    loading,
    loadError,
    loadChildrenOfItem,
    refreshItems,
    collapseItem,
  };
};

async function fetchTreeviewItem(
  collectionId: string,
  dataObjectId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem | undefined> {
  // BUG-COLL-APPID-ROUTE-005: route through v2 raw fetch — the v1 generated
  // client 404s on UUID-shaped collection/dataObject ids post-reset.
  // The collectionId here is the v2 collection identifier string (UUID v7
  // or numeric); the backend's `EntityIdResolver` accepts both.
  return fetchDataObjectV2(collectionId, dataObjectId)
    .then(response => mapToTreeviewItem(response, parentItem))
    .catch(error => {
      if (error instanceof ResponseError) {
        handleError(error, "fetchTreeviewItem");
      }
      return undefined;
    });
}

async function fetchChildrenOfItem(
  collectionId: number,
  parentId: number,
  parentItem?: TreeviewItem,
): Promise<TreeviewItem[] | undefined> {
  return useShepardApi(DataObjectApi)
    .value.getAllDataObjects({
      parentId,
      collectionId,
    })
    .then(response =>
      response
        .map(item => mapToTreeviewItem(item, parentItem))
        .sort((itemA, itemB) => itemA.id - itemB.id),
    )
    .catch(error => {
      if (error instanceof ResponseError) {
        handleError(error, "fetchChildrenOfItem");
      }
      return undefined;
    });
}
