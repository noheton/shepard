import { CollectionContainersApi } from "@dlr-shepard/backend-client";
import type { ContainerSummary } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchCollectionContainers(collectionAppId: Ref<string | null>) {
  const containers = ref<ContainerSummary[]>([]);
  const isLoading = ref(false);
  const api = useShepardApi(CollectionContainersApi);

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
