import { DataObjectApi, type ResponseError } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchDataObjectMapByCollection(collectionId: number) {
  const { data, execute } = useAsyncData(
    `collection-dataobjects-${collectionId}`,
    () => useShepardApi(DataObjectApi).value.getAllDataObjects({ collectionId }),
    { server: false, immediate: false, default: () => [] as Array<{ id: number; name: string }> },
  );

  const dataObjectsMap = computed<Map<number, string>>(() => {
    if (!data.value) return new Map<number, string>();
    return new Map((data.value as Array<{ id: number; name: string }>).map(d => [d.id, d.name]));
  });

  async function fetchMap(): Promise<void> {
    await execute().catch(e => {
      handleError(e as ResponseError, "fetching dataobjects");
    });
  }

  return { dataObjectsMap, fetchMap };
}
