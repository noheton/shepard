import type {
  Collection,
  ResponseError,
  Roles,
} from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * Fetches a collection and its roles by numeric ID.
 *
 * PERF11: both fetches now go through useAsyncData so the initial load is
 * serialised into the SSR payload. Keys are keyed on collectionId so
 * navigating between collections gets fresh data via refresh().
 */
export function useFetchCollection(collectionId: number) {
  const collectionApi = useShepardApi(CollectionApi);

  const {
    data: collectionData,
    pending: collectionPending,
    refresh: refreshCollection,
  } = useAsyncData(
    `collection-${collectionId}`,
    async () => {
      try {
        return await collectionApi.value.getCollection({ collectionId });
      } catch (e) {
        handleError(e as ResponseError, "fetching collection");
        return undefined;
      }
    },
    { default: () => undefined as Collection | undefined },
  );

  const {
    data: rolesData,
    pending: rolesPending,
    refresh: refreshRoles,
  } = useAsyncData(
    `collection-roles-${collectionId}`,
    async () => {
      try {
        return await collectionApi.value.getCollectionRoles({ collectionId });
      } catch (e) {
        handleError(e as ResponseError, "fetching collection roles");
        return undefined;
      }
    },
    { default: () => undefined as Roles | undefined },
  );

  const collection = computed(() => collectionData.value);
  const collectionRoles = computed(() => rolesData.value);
  const isLoading = computed(() => collectionPending.value || rolesPending.value);

  const isAllowedToEditCollection = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.writer;
  });
  const isAllowedToEditPermissions = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.manager;
  });
  const isOwner = computed(() => {
    return collectionRoles.value?.owner;
  });

  async function fetchCollection(_id: number) {
    // For backwards-compat callers that pass a new id, refresh both keys.
    // useAsyncData keys are static per call-site, so we just refresh.
    await Promise.all([refreshCollection(), refreshRoles()]);
  }

  onCollectionUpdated(() => {
    void Promise.all([refreshCollection(), refreshRoles()]);
  });

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    isLoading,
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
  } = useFetchCollection(routeParams.value.collectionId);

  watch(routeParams, () => {
    if (
      routeParams.value.collectionId &&
      collection.value?.id !== routeParams.value.collectionId
    ) {
      void fetchCollection(routeParams.value.collectionId);
    }
  });

  const refreshCollection = () =>
    fetchCollection(routeParams.value.collectionId);

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    refreshCollection,
  };
};
