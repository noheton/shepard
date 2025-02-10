import {
  CollectionApi,
  type CollectionReference,
  CollectionReferenceApi,
  type DataObject,
  DataObjectApi,
  type DataObjectReference,
  DataObjectReferenceApi,
  instanceOfCollection,
  instanceOfDataObject,
  instanceOfDataObjectReference,
  type ResponseError,
  type URIReference,
  UriReferenceApi,
} from "@dlr-shepard/backend-client";
import type {
  DataObjectReferencePayload,
  DataObjectReferenceWithPayload,
  Predecessor,
  RelatedEntity,
  Successor,
} from "~/components/context/display-components/relationships/relatedEntity";

export function useRelatedEntities(collectionId: number, dataObjectId: number) {
  const relatedEntities = ref<RelatedEntity[] | undefined>(undefined);

  async function fetchURIReferences(): Promise<URIReference[]> {
    return createApiInstance(UriReferenceApi)
      .getAllUriReferences({ collectionId, dataObjectId })
      .catch(error => {
        handleError(error, "fetchURIReferences");
        return [];
      });
  }

  async function fetchDataObjectReferences(): Promise<
    DataObjectReferenceWithPayload[]
  > {
    const dataObjectReferences = await createApiInstance(DataObjectReferenceApi)
      .getAllDataObjectReferences({ collectionId, dataObjectId })
      .catch(error => handleError(error, "fetchDataObjectReferences"));

    if (!dataObjectReferences) return [];
    const dataObjectReferencesWithPayloads = await Promise.all(
      dataObjectReferences.map(async ref => ({
        ...ref,
        payload: await fetchDataObjectReferencePayload(ref),
      })),
    );

    if (
      !isDataObjectReferenceWithPayloadArray(dataObjectReferencesWithPayloads)
    ) {
      return [];
    }
    return dataObjectReferencesWithPayloads;
  }

  async function fetchCollectionReferences(): Promise<CollectionReference[]> {
    return createApiInstance(CollectionReferenceApi)
      .getAllCollectionReferences({ collectionId, dataObjectId })
      .catch(error => {
        handleError(error, "fetchCollectionReferences");
        return [];
      });
  }

  async function fetchDataObjectReferencePayload(
    reference: DataObjectReference,
  ): Promise<DataObjectReferencePayload | undefined> {
    if (isDeleted(reference.id)) return;
    const dataObject = await createApiInstance(DataObjectReferenceApi)
      .getDataObjectReferencePayload({
        collectionId,
        dataObjectId,
        dataObjectReferenceId: reference.id,
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 404) return;
        handleError(error, "fetchDataObjectReferencePayload");
      });
    if (!dataObject) return;
    const collection = await createApiInstance(CollectionApi)
      .getCollection({ collectionId: dataObject.collectionId })
      .catch((error: ResponseError) => {
        if (error.response.status === 403) return;
        handleError(error, "fetchDataObjectReferencePayload");
      });
    if (!collection) return;
    return { ...dataObject, collection };
  }

  async function fetchPredecessors(): Promise<Predecessor[]> {
    const predecessors: DataObject[] = await createApiInstance(DataObjectApi)
      .getAllDataObjects({
        collectionId,
        successorId: dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchPredecessors");
        return [];
      });

    return predecessors.map(pred => ({ ...pred, type: "Predecessor" }));
  }

  async function fetchSuccessors(): Promise<Successor[]> {
    const successors: DataObject[] = await createApiInstance(DataObjectApi)
      .getAllDataObjects({
        collectionId,
        predecessorId: dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchSuccessors");
        return [];
      });

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

  onDataObjectUpdated(fetchAndMergeRelatedEntities);

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
  if (!entity || typeof entity !== "object") return false;
  if (!instanceOfDataObjectReference(entity)) return false;
  if (!("payload" in entity) || !entity.payload) return true;
  if (
    "payload" in entity &&
    !!entity.payload &&
    typeof entity.payload === "object" &&
    instanceOfDataObject(entity.payload) &&
    "collection" in entity.payload &&
    !!entity.payload.collection &&
    typeof entity.payload.collection === "object" &&
    instanceOfCollection(entity.payload.collection)
  )
    return true;
  return false;
}
