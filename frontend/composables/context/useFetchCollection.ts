import type {
  ResponseError,
  Roles,
} from "@dlr-shepard/backend-client";
import { CollectionApi } from "@dlr-shepard/backend-client";
import type { CollectionRouteParams } from "~/utils/collectionRouteParams";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchCollection(collectionId: number) {
  const collectionApi = useShepardApi(CollectionApi);
  const collectionRoles = ref<Roles | undefined>(undefined);

  const { data: collection, pending: isLoading, error: rawError, refresh } = useAsyncData(
    `collection-${collectionId}`,
    () => collectionApi.value.getCollection({ collectionId }),
    { server: false },
  );

  const isError = computed(() => !!rawError.value);

  collectionApi.value
    .getCollectionRoles({ collectionId })
    .then(response => {
      collectionRoles.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection roles");
    });

  const isAllowedToEditCollection = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.writer;
  });
  const isAllowedToEditPermissions = computed(() => {
    return collectionRoles.value?.owner || collectionRoles.value?.manager;
  });
  const isOwner = computed(() => {
    return collectionRoles.value?.owner;
  });

  onCollectionUpdated(() => {
    refresh();
  });

  return {
    collection,
    isAllowedToEditCollection,
    isAllowedToEditPermissions,
    isOwner,
    isLoading,
    isError,
    fetchCollection: (_id: number) => refresh(),
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
