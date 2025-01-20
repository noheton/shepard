import {
  getCollectionRouterParamsFromRoute,
  isCollectionRouteParams,
  type CollectionRouteParams,
} from "~/components/collection/collectionUtils";

export function useCollectionRouteParams() {
  const router = useRouter();
  const initialRoute = router.currentRoute.value;
  const initialParams = getCollectionRouterParamsFromRoute(initialRoute.params);

  if (!isCollectionRouteParams(initialParams)) {
    router.replace(collectionsPath);
  }

  const route = useRoute();
  const routeParams = ref<CollectionRouteParams>({
    collectionId: initialParams.collectionId ?? NaN,
    dataObjectId: initialParams.dataObjectId,
  });

  watch(
    () => route.params,
    () => {
      const newParams = getCollectionRouterParamsFromRoute(route.params);
      if (!isCollectionRouteParams(newParams)) {
        router.replace(collectionsPath);
        return;
      }
      routeParams.value = newParams;
    },
  );

  return { routeParams };
}
