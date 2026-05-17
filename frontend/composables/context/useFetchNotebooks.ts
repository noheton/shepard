import {
  NotebookApi,
  type NotebookReferenceIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchNotebooks(dataObjectAppId: string) {
  const notebooks = ref<NotebookReferenceIO[]>([]);
  const isLoading = ref(false);

  function refresh() {
    isLoading.value = true;
    useV2ShepardApi(NotebookApi)
      .value.listNotebooks(dataObjectAppId)
      .then(result => {
        notebooks.value = result;
      })
      .catch(error => {
        handleError(error, "listNotebooks");
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  refresh();

  return { notebooks, isLoading, refresh };
}
