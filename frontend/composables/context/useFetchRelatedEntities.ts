import {
  type Collection,
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
  CollectionReferenceV2,
  DataObjectReferencePayload,
  DataObjectReferenceWithPayload,
  Predecessor,
  RelatedEntity,
  Successor,
  URIReferenceV2,
} from "~/components/context/display-components/relationships/relatedEntity";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): raw `fetch` against the v2 Collection
 * GET endpoint so post-reset Collections (UUID v7 only, no numeric long `id`)
 * resolve. Mirrors the pattern in `useFetchCollection.ts` /
 * `useFetchDataObject.ts`. Accepts either UUID v7 or numeric stringified id —
 * the backend `EntityIdResolver` handles both shapes.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchCollectionByAnyIdV2(
  collectionId: string,
): Promise<Collection> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionId)}`;
  const resp = await fetch(url, {
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      Accept: "application/json",
    },
  });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return (await resp.json()) as Collection;
}

// BUG-COLL-APPID-ROUTE-007-PAGE: numeric ids accepted as number / Ref / getter
// and resolved at fetch time, so the appId-routed DataObject detail page can
// hand a getter that only resolves once the v2 entity loads. The v1
// `/shepard/api/...` reference + predecessor endpoints below require the
// NUMERIC ids the route param (a UUID) no longer carries.
export function useRelatedEntities(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectAppIdInput?: MaybeRefOrGetter<string | undefined>,
) {
  const relatedEntities = ref<RelatedEntity[] | undefined>(undefined);

  function ids(): { collectionId: number; dataObjectId: number } | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    if (collectionId == null || dataObjectId == null) return undefined;
    return { collectionId, dataObjectId };
  }

  async function fetchURIReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<(URIReference | URIReferenceV2)[]> {
    const doAppId = toValue(dataObjectAppIdInput);
    // V2-SWEEP-004-3: use v2 surface when a UUID v7 appId is available.
    if (doAppId && doAppId.includes("-")) {
      return fetchURIReferencesV2(doAppId);
    }
    return useShepardApi(UriReferenceApi)
      .value.getAllUriReferences({ collectionId, dataObjectId })
      .catch(error => {
        handleError(error, "fetchURIReferences");
        return [];
      });
  }

  async function fetchURIReferencesV2(dataObjectAppId: string): Promise<URIReferenceV2[]> {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url = `${v2BaseUrl()}/v2/references?kind=uri&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
    try {
      const resp = await fetch(url, {
        headers: {
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          Accept: "application/json",
        },
      });
      if (!resp.ok) return [];
      const data: unknown = await resp.json();
      if (!Array.isArray(data)) return [];
      return (data as Record<string, unknown>[]).map(item => ({
        id: item.id as number,
        appId: item.appId as string,
        kind: "uri" as const,
        name: item.name as string,
        createdAt: new Date(item.createdAt as string),
        createdBy: item.createdBy as string,
        payload: {
          uri: ((item.payload as Record<string, unknown>)?.uri ?? "") as string,
          relationship: (item.payload as Record<string, unknown>)?.relationship as string | null | undefined,
        },
      }));
    } catch (e) {
      handleError(e, "fetchURIReferencesV2");
      return [];
    }
  }

  async function fetchDataObjectReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<DataObjectReferenceWithPayload[]> {
    const dataObjectReferences = await useShepardApi(DataObjectReferenceApi)
      .value.getAllDataObjectReferences({ collectionId, dataObjectId })
      .catch(error => handleError(error, "fetchDataObjectReferences"));

    if (!dataObjectReferences) return [];
    const dataObjectReferencesWithPayloads = await Promise.all(
      dataObjectReferences.map(async ref => ({
        ...ref,
        payload: await fetchDataObjectReferencePayload(
          collectionId,
          dataObjectId,
          ref,
        ),
      })),
    );

    if (
      !isDataObjectReferenceWithPayloadArray(dataObjectReferencesWithPayloads)
    ) {
      return [];
    }
    return dataObjectReferencesWithPayloads;
  }

  async function fetchCollectionReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<(CollectionReference | CollectionReferenceV2)[]> {
    const doAppId = toValue(dataObjectAppIdInput);
    // V2-SWEEP-004-2: use the v2 endpoint when a UUID v7 appId is available —
    // it returns referencedCollectionAppId in payload, enabling appId-routed links.
    // Hyphens distinguish UUID from a legacy numeric route param string.
    if (doAppId && doAppId.includes("-")) {
      return fetchCollectionReferencesV2(doAppId);
    }
    return useShepardApi(CollectionReferenceApi)
      .value.getAllCollectionReferences({ collectionId, dataObjectId })
      .catch(error => {
        handleError(error, "fetchCollectionReferences");
        return [];
      });
  }

  async function fetchCollectionReferencesV2(
    dataObjectAppId: string,
  ): Promise<CollectionReferenceV2[]> {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url = `${v2BaseUrl()}/v2/references?kind=collection&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;
    try {
      const resp = await fetch(url, {
        headers: {
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          Accept: "application/json",
        },
      });
      if (!resp.ok) return [];
      const data: unknown = await resp.json();
      if (!Array.isArray(data)) return [];
      return (data as Record<string, unknown>[]).map(item => ({
        id: item.id as number,
        appId: item.appId as string | undefined,
        kind: "collection" as const,
        name: item.name as string,
        createdAt: new Date(item.createdAt as string),
        createdBy: item.createdBy as string,
        payload: (item.payload as CollectionReferenceV2["payload"]) ?? {},
      }));
    } catch (e) {
      handleError(e, "fetchCollectionReferencesV2");
      return [];
    }
  }

  async function fetchDataObjectReferencePayload(
    collectionId: number,
    dataObjectId: number,
    reference: DataObjectReference,
  ): Promise<DataObjectReferencePayload | undefined> {
    if (isDeleted(reference.id)) return;
    const dataObject = await useShepardApi(DataObjectReferenceApi)
      .value.getDataObjectReferencePayload({
        collectionId,
        dataObjectId,
        dataObjectReferenceId: reference.id,
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 404) return;
        handleError(error, "fetchDataObjectReferencePayload");
      });
    if (!dataObject) return;
    // BUG-COLL-APPID-ROUTE-003 (2026-06-02): route through the v2 appId-keyed
    // endpoint so post-Neo4j-reset Collections (UUID v7 only, no numeric `id`)
    // resolve correctly. `dataObject.collectionId` here may be either a UUID
    // string cast as a number (post-reset) or a real numeric id; the v2
    // EntityIdResolver accepts both shapes when stringified.
    const collection = await fetchCollectionByAnyIdV2(
      String(dataObject.collectionId),
    ).catch((error: ResponseError) => {
      if (error.response?.status === 403) return undefined;
      handleError(error, "fetchDataObjectReferencePayload");
      return undefined;
    });
    if (!collection) return;
    return { ...dataObject, collection };
  }

  // V1 EXCEPTION (V2-SWEEP Wave 4): the v2 counterpart
  // `GET /v2/.../data-objects/{appId}/predecessors` returns DataObjectSummary
  // (appId,id,name,status) only — this panel renders createdAt + full
  // DataObject fields, so migrating today would lose data. Numeric ids are
  // resolved from the loaded v2 entities (never route params). Backlog:
  // PRED-V2-SHAPE in aidocs/16 (enrich the v2 shelves, then migrate).
  async function fetchPredecessors(
    collectionId: number,
    dataObjectId: number,
  ): Promise<Predecessor[]> {
    const predecessors: DataObject[] = await useShepardApi(DataObjectApi)
      .value.getAllDataObjects({
        collectionId,
        successorId: dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchPredecessors");
        return [];
      });

    return predecessors.map(pred => ({ ...pred, type: "Predecessor" }));
  }

  // V1 EXCEPTION (V2-SWEEP Wave 4): see fetchPredecessors — same
  // summary-only v2 shape gap (PRED-V2-SHAPE in aidocs/16).
  async function fetchSuccessors(
    collectionId: number,
    dataObjectId: number,
  ): Promise<Successor[]> {
    const successors: DataObject[] = await useShepardApi(DataObjectApi)
      .value.getAllDataObjects({
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
    const resolved = ids();
    if (!resolved) return;
    const { collectionId, dataObjectId } = resolved;
    const [
      uRIReferences,
      dataObjectReferences,
      collectionReferences,
      predecessors,
      successors,
    ] = await Promise.all([
      fetchURIReferences(collectionId, dataObjectId),
      fetchDataObjectReferences(collectionId, dataObjectId),
      fetchCollectionReferences(collectionId, dataObjectId),
      fetchPredecessors(collectionId, dataObjectId),
      fetchSuccessors(collectionId, dataObjectId),
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

  // Defer until both ids resolve; re-run on first resolution (appId-route case).
  watch(ids, resolved => {
    if (resolved) fetchAndMergeRelatedEntities();
  }, { immediate: true });

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
