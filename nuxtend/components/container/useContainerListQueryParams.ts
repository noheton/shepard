import {
  parseContainerListQueryParams,
  type ContainerListQueryParams,
} from "./containerListQueryParams";

export function useContainerListQueryParams() {
  const route = useRoute();
  const initialParams = parseContainerListQueryParams(route.query);
  const queryParams = ref<ContainerListQueryParams>(initialParams);

  watch(
    () => route.query,
    () => {
      const newParams = parseContainerListQueryParams(route.query);
      queryParams.value = newParams;
    },
  );

  return { queryParams };
}
