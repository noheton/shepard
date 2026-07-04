import {
  TemplatesApi,
  type ShepardTemplate,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export function useFetchTemplates() {
  const templates = ref<ShepardTemplate[]>([]);
  const isLoading = ref<boolean>(false);

  async function refresh(includeRetired = false) {
    isLoading.value = true;
    try {
      const page = await useV2ShepardApi(TemplatesApi).value.listTemplates({
        includeRetired,
        pageSize: 200,
      });
      templates.value = (page.items ?? []) as ShepardTemplate[];
    } catch (error) {
      templates.value = [];
      handleError(error, "fetching templates");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { templates, isLoading, refresh };
}
