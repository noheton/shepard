import type {
  Collection,
  ResponseError,
  Roles,
} from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchCollection(collectionId: number) {
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

  function fetchCollection(collectionId: number) {
    lastUsedId.value = collectionId;
    isLoading.value = true;
    isError.value = false;
    const collectionApi = useShepardApi(CollectionApi);
    collectionApi.value
      .getCollection({ collectionId })
      .then(response => {
        collection.value = response;
      })
      .catch(e => {
        collection.value = undefined;
        isError.value = true;
        handleError(e as ResponseError, "fetching collection");
      });
    collectionApi.value
      .getCollectionRoles({ collectionId })
      .then(response => {
        collectionRoles.value = response;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection roles");
      })
      .finally(() => (isLoading.value = false));
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
    fetchCollection,
  };
}

export const useFetchCollectionOfRouteParams = (
  routeParams: Ref<CollectionRouteParams>,
) => {
  // BUG-COLL-APPID-ROUTE-001: route ids are strings; cast at boundary.
  // `collection.value?.id` is a numeric long from the v1 wire shape; the
  // comparison stringifies via `!==` coercion semantics — for numeric
  // route ids the compare resolves correctly; UUID v7 ids will always
  // mismatch, forcing a refetch (which 404s cleanly on v1 paths).
  const cid = () => routeParams.value.collectionId as unknown as number;
  const {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    fetchCollection,
  } = useFetchCollection(cid());

  watch(routeParams, () => {
    if (
      routeParams.value.collectionId &&
      String(collection.value?.id ?? "") !== routeParams.value.collectionId
    ) {
      fetchCollection(cid());
    }
  });

  const refreshCollection = () => fetchCollection(cid());

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    refreshCollection,
  };
};
