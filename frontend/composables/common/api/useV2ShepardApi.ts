import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";
import { ref, watch } from "vue";

/**
 * Like useShepardApi but routes to `/v2/...` endpoints which live at the
 * server root, not under `/shepard/api`.
 *
 * Derives the v2 base URL by stripping the `/shepard/api` path suffix from
 * `backendApiUrl`. e.g. `http://host/shepard/api` → `http://host`.
 */
export function useV2ShepardApi<A extends BaseAPI>(
  apiClass: new (config: Configuration) => A,
) {
  const { data } = useAuth();

  const configuration = computed(() => {
    const legacyBase = useRuntimeConfig().public.backendApiUrl as string;
    const v2Base = legacyBase.replace(/\/shepard\/api\/?$/, "");
    return new Configuration({
      basePath: v2Base,
      accessToken: data.value?.accessToken,
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
