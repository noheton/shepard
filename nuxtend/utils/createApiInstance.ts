import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";

function getConfiguration(): Configuration {
  const runtimeConfig = useRuntimeConfig();
  const config = new Configuration({
    basePath: runtimeConfig.public.backendApiUrl,
  });
  return config;
}

/**
 * @param apiClass The API class to create an instance of
 * @returns An instantiated API including configuration
 */
export function createApiInstance<A extends BaseAPI>(
  apiClass: new (config: Configuration) => A,
): A {
  return new apiClass(getConfiguration());
}
