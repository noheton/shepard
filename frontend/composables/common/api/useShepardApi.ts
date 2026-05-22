import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";
import { ref, watch } from "vue";
import { useAuthRefreshMiddleware } from "./useAuthRefreshMiddleware";
import { useV1DeprecationMiddleware } from "./useV1DeprecationMiddleware";

/**
 * @param apiClass The API class to create an instance of
 * @returns A Ref of the instantiated API including updated configuration
 */
export function useShepardApi<A extends BaseAPI>(
  apiClass: new (config: Configuration) => A,
) {
  const { data } = useAuth();
  const authMiddleware = useAuthRefreshMiddleware();
  // V1COMPAT.0 — watches every response for X-Shepard-Legacy: true
  // so the V1DeprecationBanner becomes visible on the first v1 hit.
  // useShepardApi is the v1 path (basePath = /shepard/api/...) so
  // every call through this composable triggers the banner.
  const v1DeprecationMiddleware = useV1DeprecationMiddleware();

  const configuration = computed(() => {
    return new Configuration({
      basePath: useRuntimeConfig().public.backendApiUrl,
      accessToken: data.value?.accessToken,
      middleware: [authMiddleware, v1DeprecationMiddleware],
    });
  });

  const apiInstance = ref<A>(new apiClass(configuration.value));

  watch(
    configuration,
    newConfig => {
      apiInstance.value = new apiClass(newConfig);
    },
    { deep: true },
  );

  return apiInstance;
}
