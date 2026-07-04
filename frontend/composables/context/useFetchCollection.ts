import type {
  Collection,
  ResponseError,
  Roles,
} from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * BUG-COLL-APPID-ROUTE-002 (2026-05-31): the Collection fetch must use the
 * v2 appId-keyed endpoint `GET /v2/collections/{collectionAppId}`. The
 * earlier implementation hit the generated v1 `getCollection({ collectionId })`
 * which expects a numeric Neo4j long in the path — so when the route carried
 * a UUID v7 (post-L2d wave-5 parser fix `9adc9df2f`) the v1 path returned 404
 * and the Collection page showed a red "Collection could not be fetched"
 * toast despite the URL being valid.
 *
 * The Roles endpoint still lives on the v1 shelf (no v2 equivalent yet — see
 * `CollectionV2Rest` Phase B deferred work). We satisfy it by reading the
 * numeric `id` off the v2 response and calling `getCollectionRoles` with
 * that. Numeric route ids continue to work — the v2 GET routes via the same
 * backing `EntityIdResolver` which accepts either shape.
 *
 * Raw `fetch` (no generated client) mirrors `useNotificationTransports`,
 * `useFetchPayloadVersions`, and the `downloadRepExport()` helper inside
 * `pages/collections/[collectionId]/index.vue`.
 *
 * Follow-up: filed `BUG-COLL-APPID-ROUTE-003` for `useFetchDataReferences`,
 * `useFetchDataObjectMap`, `useRelatedEntities`, treeview/sidebar lookups
 * which still receive UUID-cast-as-number from page handlers and would
 * 404 against v1.
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchCollectionV2(
  collectionId: string,
  accessToken: string,
): Promise<Collection> {
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionId)}`;
  const resp = await fetch(url, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });
  if (!resp.ok) {
    // Mirror the generated client's ResponseError shape so existing
    // `handleError` consumers continue to read `.response.status`.
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return (await resp.json()) as Collection;
}

export function useFetchCollection(collectionId: string) {
  const isLoading = ref<boolean>(false);
  const isError = ref<boolean>(false);
  // UU1 — UI-404-NICE-EMPTY-STATE: distinguish 404 from other errors so the
  // detail page can render `EntityNotFound` instead of surfacing a red toast.
  const notFound = ref<boolean>(false);
  const lastUsedId = ref<string>(collectionId);
  const collection = ref<Collection | undefined>(undefined);
  const collectionRoles = ref<Roles | undefined>(undefined);

  const isAllowedToEditCollection = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.writer;
  });
  const isAllowedToEditPermissions = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.manager;
  });
  const isOwner = computed(() => {
    return collectionRoles.value?.owner;
  });

  function fetchCollection(nextId: string) {
    lastUsedId.value = nextId;
    isLoading.value = true;
    isError.value = false;
    notFound.value = false;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      isError.value = true;
      handleError(new Error("Not authenticated"), "fetching collection");
      return;
    }

    fetchCollectionV2(nextId, accessToken)
      .then(response => {
        collection.value = response;
        // Roles live on v1 only (Phase B deferred). Use the numeric `id`
        // returned in the v2 payload for the role lookup.
        const numericId = response.id;
        if (numericId != null) {
          const collectionApi = useShepardApi(CollectionApi);
          collectionApi.value
            .getCollectionRoles({ collectionId: numericId })
            .then(roles => {
              collectionRoles.value = roles;
            })
            .catch(e => {
              handleError(e as ResponseError, "fetching collection roles");
            })
            .finally(() => (isLoading.value = false));
        } else {
          isLoading.value = false;
        }
      })
      .catch(e => {
        collection.value = undefined;
        isLoading.value = false;
        const status = (e as ResponseError)?.response?.status;
        if (status === 404) {
          notFound.value = true;
          return; // suppress the red toast — page renders EntityNotFound
        }
        isError.value = true;
        handleError(e as ResponseError, "fetching collection");
      });
  }

  fetchCollection(collectionId);

  onCollectionUpdated(() => {
    fetchCollection(lastUsedId.value);
  });

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    isLoading,
    isError,
    notFound,
    fetchCollection,
  };
}

export const useFetchCollectionOfRouteParams = (
  routeParams: Ref<CollectionRouteParams>,
) => {
  // BUG-COLL-APPID-ROUTE-002: route ids are strings (UUID v7 or numeric long
  // shape); the composable signature now accepts strings end-to-end, so the
  // pre-fix `parseInt(...)` truncation cannot recur.
  const cid = () => routeParams.value.collectionId;
  const {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    notFound,
    fetchCollection,
  } = useFetchCollection(cid());

  watch(routeParams, () => {
    if (!routeParams.value.collectionId) return;
    // Compare against both wire identifiers — appId (string) and the legacy
    // numeric id (stringified) — so navigating between the two shapes does
    // not loop on refetch.
    const incoming = routeParams.value.collectionId;
    const currentAppId =
      (collection.value as unknown as { appId?: string | null })?.appId ?? "";
    const currentNumeric =
      collection.value?.id != null ? String(collection.value.id) : "";
    if (currentAppId !== incoming && currentNumeric !== incoming) {
      fetchCollection(incoming);
    }
  });

  const refreshCollection = () => fetchCollection(cid());

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    notFound,
    refreshCollection,
  };
};
