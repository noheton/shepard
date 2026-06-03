import { CollectionContainersApi } from "@dlr-shepard/backend-client";
import type { ContainerSummary } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchCollectionContainers(collectionAppId: Ref<string | null>) {
  const containers = ref<ContainerSummary[]>([]);
  const isLoading = ref(false);
  // CollectionContainersApi is a /v2/ client. It MUST go through the v2 helper
  // (basePath = host without /shepard/api). Using the v1 helper produced
  // /shepard/api/v2/collections/{appId}/referenced-containers → 404.
  const api = useV2ShepardApi(CollectionContainersApi);

  async function fetch(appId: string) {
    isLoading.value = true;
    try {
      containers.value = await api.value.listReferencedContainers({ collectionAppId: appId });
    } catch (e) {
      handleError(e, "listReferencedContainers");
    } finally {
      isLoading.value = false;
    }
  }

  watch(
    collectionAppId,
    appId => {
      if (appId) void fetch(appId);
    },
    { immediate: true },
  );

  return { containers, isLoading };
}
