import {
  instanceOfBasicContainerAttributes,
  type BasicContainerAttributes,
} from "@dlr-shepard/backend-client";
import type { LocationQueryRaw } from "vue-router";
import {
  instanceOfContainerFilterType,
  type ContainerFilterType,
} from "~/components/container/containerTypeFilter";

export type ContainerListQueryParams =
  ListQueryParams<BasicContainerAttributes> & {
    selectedFilter?: ContainerFilterType;
  };

export function parseContainerListQueryParams(
  queryParams: LocationQueryRaw,
): ContainerListQueryParams {
  const basicParams = parseListQueryParams(queryParams);

  if (
    !basicParams.sortBy ||
    !isBasicContainerAttribute(basicParams.sortBy?.key)
  ) {
    return {
      page: basicParams.page,
      searchText: basicParams.searchText,
      selectedFilter: parseSelectedFilter(queryParams),
    };
  }

  return {
    page: basicParams.page,
    searchText: basicParams.searchText,
    sortBy: {
      key: basicParams.sortBy?.key,
      order: basicParams.sortBy.order,
    },
    selectedFilter: parseSelectedFilter(queryParams),
  };
}

function parseSelectedFilter(
  queryParams: LocationQueryRaw,
): ContainerFilterType | undefined {
  if (
    queryParams.selectedFilter &&
    typeof queryParams.selectedFilter === "string" &&
    instanceOfContainerFilterType(queryParams.selectedFilter)
  ) {
    return queryParams.selectedFilter;
  }
  return undefined;
}

function isBasicContainerAttribute(
  value: unknown,
): value is BasicContainerAttributes {
  return instanceOfBasicContainerAttributes(value);
}
