import type { RouteParamsGeneric } from "vue-router";

export interface ContainerRouteParams {
  containerId: number;
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

function parseContainerId(routeParams: RouteParamsGeneric): number {
  if (
    routeParams.containerId &&
    typeof routeParams.containerId === "string" &&
    !Number.isNaN(routeParams.containerId)
  ) {
    return parseInt(routeParams.containerId);
  } else {
    throw new Error("Path does not contain container id!");
  }
}
