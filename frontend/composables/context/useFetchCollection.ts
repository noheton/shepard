import type {
  Collection,
  ResponseError,
  Roles,
} from "@dlr-shepard/backend-client";
import { CollectionApi, CollectionV2Api } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * Fetches a Collection by its numeric OGM id (v1 path) or, when
 * `collectionId` is NaN and `collectionAppId` is provided, first resolves
 * the appId to a numeric id via GET /v2/collections/{appId} and then
 * proceeds with the standard v1 role fetch.
 *
 * UX-WALK-2026-05-29-03: navigating to /collections/<uuid> no longer
 * shows "ID ERROR" — the route loader detects the UUID-shaped param,
 * stores it as `collectionAppId`, and this composable does the v2
 * resolution transparently.
 */
export function useFetchCollection(collectionId: number, collectionAppId?: string) {
  const isLoading = ref<boolean>(false);
  const isError = ref<boolean>(false);
  const lastUsedId = ref<number>(collectionId);
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

  function fetchCollectionById(numericId: number) {
    lastUsedId.value = numericId;
    isLoading.value = true;
    isError.value = false;
    const collectionApi = useShepardApi(CollectionApi);
    collectionApi.value
      .getCollection({ collectionId: numericId })
      .then(response => {
        collection.value = response;
      })
      .catch(e => {
        collection.value = undefined;
        isError.value = true;
        handleError(e as ResponseError, "fetching collection");
      });
    collectionApi.value
      .getCollectionRoles({ collectionId: numericId })
      .then(response => {
        collectionRoles.value = response;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection roles");
      })
      .finally(() => (isLoading.value = false));
  }

  /**
   * Resolves a Collection appId (UUID v7) to its numeric OGM id via
   * GET /v2/collections/{appId}, then delegates to fetchCollectionById.
   * On any error, sets isError=true so the page renders NotFoundPanel.
   */
  function fetchCollectionByAppId(appId: string) {
    isLoading.value = true;
    isError.value = false;
    const collectionV2Api = useV2ShepardApi(CollectionV2Api);
    collectionV2Api.value
      .getCollectionByAppId({ collectionAppId: appId })
      .then(resolved => {
        // The v2 endpoint returns the full CollectionIO shape which includes
        // the numeric `id`. Store the result directly (it is the same wire
        // shape as what v1 returns) and proceed to fetch roles by numeric id.
        collection.value = resolved;
        const numericId = resolved.id;
        lastUsedId.value = numericId;
        const collectionApi = useShepardApi(CollectionApi);
        return collectionApi.value
          .getCollectionRoles({ collectionId: numericId })
          .then(roles => {
            collectionRoles.value = roles;
          })
          .catch(e => {
            handleError(e as ResponseError, "fetching collection roles");
          });
      })
      .catch(e => {
        collection.value = undefined;
        isError.value = true;
        handleError(e as ResponseError, "fetching collection by appId");
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  function fetchCollection(id: number, appId?: string) {
    if (Number.isNaN(id) && appId) {
      fetchCollectionByAppId(appId);
    } else {
      fetchCollectionById(id);
    }
  }

  // Expose a stable refetch helper that re-uses the last known numeric id
  // (or the appId when the numeric id is still NaN from a prior resolution).
  const savedAppId = ref<string | undefined>(collectionAppId);

  function refetch() {
    if (!Number.isNaN(lastUsedId.value)) {
      fetchCollectionById(lastUsedId.value);
    } else if (savedAppId.value) {
      fetchCollectionByAppId(savedAppId.value);
    }
  }

  fetchCollection(collectionId, collectionAppId);

  onCollectionUpdated(() => {
    refetch();
  });

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    isLoading,
    isError,
    fetchCollection,
  };
}

export const useFetchCollectionOfRouteParams = (
  routeParams: Ref<CollectionRouteParams>,
) => {
  const {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    fetchCollection,
  } = useFetchCollection(
    routeParams.value.collectionId,
    routeParams.value.collectionAppId,
  );

  watch(routeParams, () => {
    const { collectionId: newId, collectionAppId: newAppId } = routeParams.value;
    const resolvedId = collection.value?.id;
    if (Number.isNaN(newId) && newAppId) {
      // appId route — always refetch on route param change
      fetchCollection(newId, newAppId);
    } else if (newId && resolvedId !== newId) {
      fetchCollection(newId);
    }
  });

  const refreshCollection = () =>
    fetchCollection(
      routeParams.value.collectionId,
      routeParams.value.collectionAppId,
    );

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    refreshCollection,
  };
};
