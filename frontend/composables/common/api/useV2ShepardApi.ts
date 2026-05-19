import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";
import { ref, watch } from "vue";
import { useAuthRefreshMiddleware } from "./useAuthRefreshMiddleware";

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
  const authMiddleware = useAuthRefreshMiddleware();

  const configuration = computed(() => {
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const v2Base =
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
    return new Configuration({
      basePath: v2Base,
      accessToken: data.value?.accessToken,
      middleware: [authMiddleware],
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
