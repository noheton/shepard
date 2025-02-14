import type { LocationQueryRaw } from "vue-router";

export interface ListQueryParams<SortByKey extends string = string> {
  page: number;
  sortBy?: SortBy<SortByKey>;
  searchText?: string;
}

export function parseListQueryParams(
  queryParams: LocationQueryRaw,
): ListQueryParams {
  return {
    page: parsePage(queryParams),
    sortBy: parseSortBy(queryParams),
    searchText: parseSearchText(queryParams),
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

function parseSortBy(queryParams: LocationQueryRaw): SortBy | undefined {
  if (queryParams.sortBy && typeof queryParams.sortBy === "string") {
    try {
      const sortBy = JSON.parse(queryParams.sortBy);
      if (
        sortBy.key &&
        typeof sortBy.key === "string" &&
        sortBy.order &&
        instanceOfSortOrderOption(sortBy.order)
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
