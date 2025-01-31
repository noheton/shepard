import { BasicContainerAttributes } from "@dlr-shepard/backend-client";
import { ContainerSortByOrderOptions } from "~/components/container/containerSortByAttribute";

export function useContainerListRouteParams() {
  const router = useRouter();
  const initialRoute = router.currentRoute.value;

  const initialParams = parseContainerListQueryParams(initialRoute.query);

  const route = useRoute();
  const queryParams = ref<ContainerListQueryParams>({
    ...initialParams,
    sortBy: initialParams.sortBy ?? {
      key: BasicContainerAttributes.CreatedAt,
      order: ContainerSortByOrderOptions.DESC,
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
