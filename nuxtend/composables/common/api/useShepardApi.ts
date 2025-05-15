import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";
import { ref, watch } from "vue";

/**
 * @param apiClass The API class to create an instance of
 * @returns A Ref of the instantiated API including updated configuration
 */
export function useShepardApi<A extends BaseAPI>(
  apiClass: new (config: Configuration) => A,
) {
  const { data } = useAuth();

  const configuration = computed(() => {
    return new Configuration({
      basePath: useRuntimeConfig().public.backendApiUrl,
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
