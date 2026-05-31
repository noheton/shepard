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
    owner?: string;
  };

export function parseContainerListQueryParams(
  queryParams: LocationQueryRaw,
): ContainerListQueryParams {
  const basicParams = parseListQueryParams(queryParams);
  const owner = parseOwner(queryParams);

  if (
    !basicParams.sortBy ||
    !isBasicContainerAttribute(basicParams.sortBy?.key)
  ) {
    return {
      page: basicParams.page,
      searchText: basicParams.searchText,
      selectedFilter: parseSelectedFilter(queryParams),
      owner,
    };
  }

  return {
    page: basicParams.page,
    searchText: basicParams.searchText,
    sortBy: {
      key: basicParams.sortBy.key,
      order: basicParams.sortBy.order,
    },
    selectedFilter: parseSelectedFilter(queryParams),
    owner,
  };
}

function parseOwner(queryParams: LocationQueryRaw): string | undefined {
  const raw = queryParams.owner;
  if (typeof raw === "string" && raw.trim()) return raw.trim();
  return undefined;
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
