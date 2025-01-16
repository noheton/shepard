import {
  getCollectionRouterParamsFromRoute,
  type CollectionRouteParams,
} from "~/components/collection/collectionUtils";

export function useCollectionRoute(
  onCollectionRouteChange: (newParams: CollectionRouteParams) => void,
) {
  const initialRoute = useRouter().currentRoute.value;

  const route = useRoute();
  const routeParams = ref<CollectionRouteParams>(
    getCollectionRouterParamsFromRoute(initialRoute.params),
  );

  watch(
    () => route.params,
    () => {
      const newParams = getCollectionRouterParamsFromRoute(route.params);
      routeParams.value = newParams;
      onCollectionRouteChange(newParams);
    },
  );

  return { routeParams };
}
