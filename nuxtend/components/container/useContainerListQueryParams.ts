import { BasicContainerAttributes } from "@dlr-shepard/backend-client";
import {
  parseContainerListQueryParams,
  type ContainerListQueryParams,
} from "./containerListQueryParams";

export function useContainerListQueryParams() {
  const route = useRoute();
  const initialParams = parseContainerListQueryParams(route.query);
  const queryParams = ref<ContainerListQueryParams>({
    ...initialParams,
    sortBy: initialParams.sortBy ?? {
      key: BasicContainerAttributes.CreatedAt,
      order: "desc",
    },
  });

  watch(
    () => route.query,
    () => {
      const newParams = parseContainerListQueryParams(route.query);
      queryParams.value = newParams;
    },
  );

  return { queryParams };
}
