import {
  ShepardTemplateApi,
  type ShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export function useFetchTemplates() {
  const templates = ref<ShepardTemplateIO[]>([]);
  const isLoading = ref<boolean>(false);

  async function refresh(includeRetired = false) {
    isLoading.value = true;
    try {
      templates.value = await useV2ShepardApi(ShepardTemplateApi).value.getTemplates({
        includeRetired,
      });
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
