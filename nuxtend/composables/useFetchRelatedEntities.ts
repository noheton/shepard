import {
  CollectionApi,
  CollectionReferenceApi,
  DataObjectApi,
  DataObjectReferenceApi,
  instanceOfCollection,
  instanceOfDataObject,
  instanceOfDataObjectReference,
  UriReferenceApi,
  type DataObjectReference,
} from "@dlr-shepard/backend-client";
import type {
  DataObjectReferenceWithPayload,
  Predecessor,
  RelatedEntity,
  Successor,
} from "~/components/data-object/relationships/relatedEntity";

export function useRelatedEntities(collectionId: number, dataObjectId: number) {
  const relatedEntities = ref<RelatedEntity[] | undefined>(undefined);

  async function fetchURIReferences() {
    return createApiInstance(UriReferenceApi)
      .getAllUriReferences({ collectionId, dataObjectId })
      .catch(error => handleError(error, "fetchURIReferences"));
  }

  async function fetchDataObjectReferences(): Promise<
    DataObjectReferenceWithPayload[] | undefined
  > {
    const dataObjectReferences = await createApiInstance(DataObjectReferenceApi)
      .getAllDataObjectReferences({ collectionId, dataObjectId })
      .catch(error => handleError(error, "fetchDataObjectReferences"));

    if (!dataObjectReferences) return;
    const dataObjectReferencesWithPayloads = await Promise.all(
      dataObjectReferences.map(async ref => ({
        ...ref,
        payload: await fetchDataObjectReferencePayload(ref),
      })),
    );

    if (
      !isDataObjectReferenceWithPayloadArray(dataObjectReferencesWithPayloads)
    ) {
      return;
    }
    return dataObjectReferencesWithPayloads;
  }

  async function fetchCollectionReferences() {
    return createApiInstance(CollectionReferenceApi)
      .getAllCollectionReferences({ collectionId, dataObjectId })
      .catch(error => handleError(error, "fetchCollectionReferences"));
  }

  async function fetchDataObjectReferencePayload(
    reference: DataObjectReference,
  ) {
    const dataObject = await createApiInstance(DataObjectReferenceApi)
      .getDataObjectReferencePayload({
        collectionId,
        dataObjectId,
        dataObjectReferenceId: reference.id,
      })
      .catch(error => handleError(error, "fetchDataObjectReferencePayload"));
    if (!dataObject) return;
    const collection = await createApiInstance(CollectionApi)
      .getCollection({ collectionId: dataObject.collectionId })
      .catch(error => handleError(error, "fetchDataObjectReferencePayload"));
    if (!collection) return;
    return { ...dataObject, collection };
  }

  async function fetchPredecessors(): Promise<Predecessor[] | undefined> {
    const predecessors = await createApiInstance(DataObjectApi)
      .getAllDataObjects({
        collectionId,
        successorId: dataObjectId,
      })
      .catch(error => handleError(error, "fetchPredecessorsAndSuccessors"));
    if (!predecessors) return undefined;

    return predecessors.map(pred => ({ ...pred, type: "Predecessor" }));
  }

  async function fetchSuccessors(): Promise<Successor[] | undefined> {
    const successors = await createApiInstance(DataObjectApi)
      .getAllDataObjects({
        collectionId,
        predecessorId: dataObjectId,
      })
      .catch(error => handleError(error, "fetchPredecessorsAndSuccessors"));
    if (!successors) return undefined;

    return successors.map(succ => ({ ...succ, type: "Successor" }));
  }

  async function fetchAndMergeRelatedEntities() {
    const [
      uRIReferences,
      dataObjectReferences,
      collectionReferences,
      predecessors,
      successors,
    ] = await Promise.all([
      fetchURIReferences(),
      fetchDataObjectReferences(),
      fetchCollectionReferences(),
      fetchPredecessors(),
      fetchSuccessors(),
    ]);
    if (
      !uRIReferences ||
      !dataObjectReferences ||
      !collectionReferences ||
      !predecessors ||
      !successors
    ) {
      return;
    }

    const relationships = [
      ...uRIReferences,
      ...dataObjectReferences,
      ...collectionReferences,
      ...predecessors,
      ...successors,
    ].sort((a, b) => b.createdAt.valueOf() - a.createdAt.valueOf());
    relatedEntities.value = relationships;
  }

  fetchAndMergeRelatedEntities();

  return { relatedEntities };
}

function isDataObjectReferenceWithPayloadArray(
  entityArray: unknown,
): entityArray is DataObjectReferenceWithPayload[] {
  return (
    !!entityArray &&
    Array.isArray(entityArray) &&
    entityArray.every(val => isDataObjectReferenceWithPayload(val))
  );
}

function isDataObjectReferenceWithPayload(
  entity: unknown,
): entity is DataObjectReferenceWithPayload {
  return (
    !!entity &&
    typeof entity === "object" &&
    instanceOfDataObjectReference(entity) &&
    "payload" in entity &&
    !!entity.payload &&
    typeof entity.payload === "object" &&
    instanceOfDataObject(entity.payload) &&
    "collection" in entity.payload &&
    !!entity.payload.collection &&
    typeof entity.payload.collection === "object" &&
    instanceOfCollection(entity.payload.collection)
  );
}
