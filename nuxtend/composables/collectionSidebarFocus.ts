import type { CollectionRouteParams } from "~/components/collection/collectionUtils";

export const useCollectionSidebarFocus = (
  onCollectionRouteChange: (newParams: CollectionRouteParams) => void,
) => {
  const { routeParams } = useCollectionRoute(newParams => {
    switchFocusOnParams();
    onCollectionRouteChange(newParams);
  });
  const activeDataObjectId = ref<number | undefined>(undefined);
  const isCollectionHeaderFocused = ref<boolean>(false);

  function switchFocusOnParams() {
    if (!routeParams.value) return;
    if (routeParams.value.dataObjectId) {
      isCollectionHeaderFocused.value = false;
      activeDataObjectId.value = routeParams.value.dataObjectId;
    } else if (routeParams.value.collectionId) {
      isCollectionHeaderFocused.value = true;
      activeDataObjectId.value = undefined;
    }
  }

  switchFocusOnParams();

  return {
    routeParams,
    activeDataObjectId,
    isCollectionHeaderFocused,
  };
};
