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
    const collectionApi = useShepardApi(CollectionApi);
    collectionApi.value
      .getCollection({ collectionId })
      .then(response => {
        collection.value = response;
      })
      .catch(e => {
        collection.value = undefined;
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
      fetchCollection(routeParams.value.collectionId);
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
