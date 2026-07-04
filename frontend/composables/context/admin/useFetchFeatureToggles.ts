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
      features.value = await useV2ShepardApi(AdminApi).value.listFeatureToggles();
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
