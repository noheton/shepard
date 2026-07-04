import {
  LabJournalApi,
  type NotebookReference,
  type PagedResponse,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchNotebooks(dataObjectAppId: string) {
  const notebooks = ref<NotebookReference[]>([]);
  const isLoading = ref(false);

  function refresh() {
    isLoading.value = true;
    useV2ShepardApi(LabJournalApi)
      .value.listNotebooks({ dataObjectAppId })
      .then((result: PagedResponse) => {
        notebooks.value = (result.items ?? []) as NotebookReference[];
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
