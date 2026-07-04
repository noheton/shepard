import type { RouteParamsGeneric } from "vue-router";

export interface ContainerRouteParams {
  // V2-SWEEP-003-2: changed from number — routes now carry either a UUID-v7 appId
  // or a numeric string (V1-EXCEPTION for HeaderBar search). Accessors detect which
  // via /^\d+$/ and branch to v1 or v2 fetch accordingly.
  containerId: string;
}

export const isContainerRouteParams = (
  routeParams: Partial<ContainerRouteParams>,
): routeParams is ContainerRouteParams => {
  if (!routeParams.containerId) return false;
  return true;
};

/**
 * A helper function to parse the router parameter to create an instance of `ContainerRouteParams`.
 * @param routeParams - RouteParamsGeneric
 * @returns Returns `undefined` if the containerId was not present in the route params, else returns an instance of `ContainerRouteParams`
 */

export function parseContainerRouteParams(
  routeParams: RouteParamsGeneric,
): Partial<ContainerRouteParams> {
  return {
    containerId: parseContainerId(routeParams),
  };
}

function parseContainerId(routeParams: RouteParamsGeneric): string {
  const raw = routeParams.containerId;
  if (typeof raw === "string" && raw.length > 0) {
    return raw; // Return as-is — accessor's /^\d+$/ guard handles numeric vs UUID.
  }
  throw new Error("Path does not contain container id!");
}
