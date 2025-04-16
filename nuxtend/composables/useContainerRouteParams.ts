import {
  isContainerRouteParams,
  parseContainerRouteParams,
  type ContainerRouteParams,
} from "~/utils/containerRouteParams";

export function useContainerRouteParams() {
  const router = useRouter();
  const initialRoute = router.currentRoute.value;
  const initialParams = parseContainerRouteParams(initialRoute.params);

  if (!isContainerRouteParams(initialParams)) {
    router.replace(containersPath);
  }

  const route = useRoute();
  const routeParams = ref<ContainerRouteParams>(<ContainerRouteParams>{
    containerId: initialParams.containerId,
  });

  watch(
    () => route.params,
    () => {
      const newParams = parseContainerRouteParams(route.params);
      if (!isContainerRouteParams(newParams)) {
        router.replace(containersPath);
        return;
      }
      routeParams.value = newParams;
    },
  );

  return { routeParams };
}
