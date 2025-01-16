export const useCollectionWithChildren = (collectionId: number | undefined) => {
  const { collection, fetchCollection } = useCollection(collectionId);
  const { dataObjectsList: children, fetchDataObjectsOfCollection } =
    useDataObjectListByCollection(collectionId, -1);

  const refetchCollectionAndChildren = (collectionId: number) => {
    fetchCollection(collectionId);
    fetchDataObjectsOfCollection(collectionId, -1);
  };

  return { collection, children, refetchCollectionAndChildren };
};
