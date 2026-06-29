import {
  AdminApi,
  type FeatureToggle,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export function useFetchFeatureToggles() {
  const features = ref<FeatureToggle[]>([]);
  const isLoading = ref<boolean>(false);

  async function refresh() {
    isLoading.value = true;
    try {
      const page = await useV2ShepardApi(AdminApi).value.listFeatureToggles();
      features.value = (page.items ?? []) as FeatureToggle[];
    } catch (error) {
      features.value = [];
      handleError(error, "fetching feature toggles");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { features, isLoading, refresh };
}
