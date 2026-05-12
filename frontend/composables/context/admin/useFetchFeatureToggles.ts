import {
  AdminFeaturesApi,
  type FeatureToggleIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export function useFetchFeatureToggles() {
  const features = ref<FeatureToggleIO[]>([]);
  const isLoading = ref<boolean>(false);

  async function refresh() {
    isLoading.value = true;
    try {
      features.value = await useV2ShepardApi(AdminFeaturesApi).value.listFeatures();
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
