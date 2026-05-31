import {
  isCollectionRouteParams,
  parseCollectionRouteParams,
  type CollectionRouteParams,
} from "~/utils/collectionRouteParams";

export function useCollectionRouteParams() {
  const router = useRouter();
  const initialRoute = router.currentRoute.value;
  const initialParams = parseCollectionRouteParams(initialRoute.params);

  if (!isCollectionRouteParams(initialParams)) {
    router.replace(collectionsPath);
  }

  const route = useRoute();
  const routeParams = ref<CollectionRouteParams>({
    // BUG-COLL-APPID-ROUTE-001: ids are strings (UUID v7 or numeric).
    // `""` is the unset sentinel — isCollectionRouteParams rejects it.
    collectionId: initialParams.collectionId ?? "",
    dataObjectId: initialParams.dataObjectId,
    timeseriesReferenceId: initialParams.timeseriesReferenceId,
    fileReferenceId: initialParams.fileReferenceId,
    structuredDataReferenceId: initialParams.structuredDataReferenceId,
  });

  watch(
    () => route.params,
    () => {
      const newParams = parseCollectionRouteParams(route.params);
      if (!isCollectionRouteParams(newParams)) {
        router.replace(collectionsPath);
        return;
      }
      routeParams.value = newParams;
    },
  );

  return { routeParams };
}
