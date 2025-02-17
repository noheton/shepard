import {
  instanceOfBasicCollectionAttributes,
  type BasicCollectionAttributes,
} from "@dlr-shepard/backend-client";
import type { LocationQueryRaw } from "vue-router";

export function useCollectionListQueryParams() {
  const route = useRoute();
  const initialParams = parseCollectionListQueryParams(route.query);
  const queryParams =
    ref<ListQueryParams<BasicCollectionAttributes>>(initialParams);

  watch(
    () => route.query,
    () => {
      const newParams = parseCollectionListQueryParams(route.query);
      queryParams.value = newParams;
    },
  );

  return { queryParams };
}

function isBasicCollectionAttributes(
  value: unknown,
): value is BasicCollectionAttributes {
  return (
    typeof value === "string" && instanceOfBasicCollectionAttributes(value)
  );
}

function parseCollectionListQueryParams(
  query: LocationQueryRaw,
): ListQueryParams<BasicCollectionAttributes> {
  const params = parseListQueryParams(query);
  if (params.sortBy && isBasicCollectionAttributes(params.sortBy.key)) {
    return {
      ...params,
      sortBy: { key: params.sortBy.key, order: params.sortBy.order },
    };
  }
  return { ...params, sortBy: undefined };
}
