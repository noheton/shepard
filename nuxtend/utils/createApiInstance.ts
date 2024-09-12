import type { BaseAPI } from "@dlr-shepard/backend-client";
import { Configuration } from "@dlr-shepard/backend-client";

const runtimeConfig = useRuntimeConfig();

function getConfiguration(): Configuration {
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
