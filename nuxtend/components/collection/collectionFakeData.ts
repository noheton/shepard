import type { Collection, DataObject } from "@dlr-shepard/backend-client";

export const pause = async (ms: number) =>
  new Promise(resolve => setTimeout(resolve, ms));

export async function getFakeCollectionById(
  collectionId: number,
): Promise<Collection> {
  return {
    name: "Test-Collection",
    id: collectionId,
    createdBy: "Mr. Test",
    createdAt: new Date(),
    description: "Just a test collection",
    dataObjectIds: [1, 2, 3],
  };
}

export async function getFakeDataObjectsOfCollection(
  collectionId: number,
): Promise<DataObject[]> {
  return [
    {
      name: "Data Object 1 of collection " + collectionId,
      id: 1,
      collectionId: collectionId,
      description: "none",
      childrenIds: [10, 11, 12],
    },
    {
      name: "Data Object 2 of collection " + collectionId,
      id: 2,
      collectionId: collectionId,
      description: "none",
      childrenIds: [20, 21],
    },
    {
      name: "Data Object 3 of collection " + collectionId,
      id: 3,
      collectionId: collectionId,
      description: "none",
      childrenIds: [30],
    },
  ];
}

export async function getFakeDataObjectById(
  collectionId: number,
  dataObjectId: number,
): Promise<DataObject> {
  if (dataObjectId === 10)
    return createFakeDataObject(collectionId, dataObjectId, 1, [111]);
  if (dataObjectId === 11)
    return createFakeDataObject(collectionId, dataObjectId, 1, [112, 113]);
  if (dataObjectId === 13)
    return createFakeDataObject(collectionId, dataObjectId, 1, undefined);
  if (dataObjectId === 20)
    return createFakeDataObject(collectionId, dataObjectId, 2, [211]);
  if (dataObjectId === 21)
    return createFakeDataObject(collectionId, dataObjectId, 2, [212]);
  if (dataObjectId === 30)
    return createFakeDataObject(collectionId, dataObjectId, 3, [311, 312]);
  return createFakeDataObject(collectionId, dataObjectId, 0, undefined);
}

async function createFakeDataObject(
  collectionId: number,
  dataObjectId: number,
  parentId: number,
  childrenIds: number[] | undefined,
): Promise<DataObject> {
  return {
    id: dataObjectId,
    name: "Data Object " + dataObjectId,
    collectionId,
    parentId,
    childrenIds,
  };
}
