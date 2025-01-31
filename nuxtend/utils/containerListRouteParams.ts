import { instanceOfBasicContainerAttributes } from "@dlr-shepard/backend-client";
import type { LocationQueryRaw } from "vue-router";
import {
  instanceOfContainerSortByOrder,
  type ContainerSortByAttribute,
} from "~/components/container/containerSortByAttribute";
import {
  instanceOfContainerFilterType,
  type ContainerFilterType,
} from "~/components/container/containerTypeFilter";

export interface ContainerListQueryParams {
  page: number;
  sortBy?: ContainerSortByAttribute;
  searchText?: string;
  selectedFilter?: ContainerFilterType;
}

export function parseContainerListQueryParams(
  queryParams: LocationQueryRaw,
): ContainerListQueryParams {
  return {
    page: parsePage(queryParams),
    sortBy: parseSortBy(queryParams),
    searchText: parseSearchText(queryParams),
    selectedFilter: parseSelectedFilter(queryParams),
  };
}
function parsePage(queryParams: LocationQueryRaw): number {
  if (
    queryParams.page &&
    typeof queryParams.page === "string" &&
    !Number.isNaN(queryParams.page)
  ) {
    return parseInt(queryParams.page);
  }
  return 1;
}

function parseSortBy(
  queryParams: LocationQueryRaw,
): ContainerSortByAttribute | undefined {
  if (queryParams.sortBy && typeof queryParams.sortBy === "string") {
    try {
      const sortBy = JSON.parse(queryParams.sortBy);
      if (
        sortBy.key &&
        instanceOfBasicContainerAttributes(sortBy.key) &&
        sortBy.order &&
        instanceOfContainerSortByOrder(sortBy.order)
      )
        return { key: sortBy.key, order: sortBy.order };
    } catch {
      return undefined;
    }
  }
  return undefined;
}

function parseSearchText(queryParams: LocationQueryRaw): string | undefined {
  if (queryParams.searchText && typeof queryParams.searchText === "string") {
    return queryParams.searchText;
  }
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
