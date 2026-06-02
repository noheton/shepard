import type { DataObject, ResponseError } from "@dlr-shepard/backend-client";

/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02): the predecessor / successor mutation
 * flow routes through the v2 appId-keyed endpoints
 * (`/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`) so
 * post-Neo4j-reset DataObjects (UUID v7 only, no numeric long `id`) resolve.
 * The body shape is unchanged — `predecessorIds: long[]` is a v1-and-v2
 * DataObjectIO field; v2 PATCH consumes the same IO. The v2 backing resolver
 * (`EntityIdResolver`) accepts UUID v7 or numeric stringified id, so legacy
 * numeric handles keep working when callers haven't been widened yet.
 *
 * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH (2026-06-02): companion
 * `predecessorAppIds` field added to the PATCH body. When the predecessor
 * DataObject being added/removed has a UUID v7 `appId` (no legacy numeric id),
 * the composable now also sends that appId in `predecessorAppIds` so the
 * backend can resolve it without a numeric shepardId.
 */

/** Extended DataObject body shape that includes the new predecessorAppIds field. */
type DataObjectPatchBody = Partial<DataObject> & {
  predecessorAppIds?: string[];
};

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function v2Fetch(
  path: string,
  init: RequestInit,
): Promise<Response> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const headers: Record<string, string> = {
    ...((init.headers as Record<string, string> | undefined) ?? {}),
    Accept: "application/json",
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const resp = await fetch(`${v2BaseUrl()}${path}`, { ...init, headers });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  return resp;
}

async function getDataObjectV2(
  collectionHandle: string | number,
  dataObjectHandle: string | number,
): Promise<DataObject> {
  const resp = await v2Fetch(
    `/v2/collections/${encodeURIComponent(String(collectionHandle))}` +
      `/data-objects/${encodeURIComponent(String(dataObjectHandle))}`,
    { method: "GET" },
  );
  return (await resp.json()) as DataObject;
}

async function patchDataObjectV2(
  collectionHandle: string | number,
  dataObjectHandle: string | number,
  body: DataObjectPatchBody,
): Promise<DataObject> {
  const resp = await v2Fetch(
    `/v2/collections/${encodeURIComponent(String(collectionHandle))}` +
      `/data-objects/${encodeURIComponent(String(dataObjectHandle))}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/merge-patch+json" },
      body: JSON.stringify(body),
    },
  );
  return (await resp.json()) as DataObject;
}

/**
 * Returns the UUID v7 appId string for a DataObject node when the
 * `predecessorDataObjectId` is itself a UUID v7 (post-reset, no numeric id).
 * The appId is looked up in `predecessorDataObject` when provided; otherwise
 * the id itself is treated as a string appId when it looks like a UUID v7.
 *
 * Returns undefined when no appId can be derived (legacy numeric-only id).
 */
function deriveAppId(
  id: number,
  predecessorDataObject?: DataObject,
): string | undefined {
  // If the caller has the DataObject and it has an appId, use it directly.
  if (predecessorDataObject?.appId) return predecessorDataObject.appId;
  // If the id is actually a stringified UUID v7 (post-reset case), use it.
  const s = String(id);
  if (/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s)) {
    return s;
  }
  return undefined;
}

/**
 * `collectionHandle` accepts either a UUID v7 string (post-reset) or a
 * numeric id (legacy / pre-reset). The page-level cast in
 * `pages/collections/[collectionId]/dataobjects/[dataObjectId]/index.vue`
 * still labels these as `number`, but the value behind the cast is the raw
 * route segment — so `String(handle)` is always the correct stringification.
 */
export function useUpdateDataObjectRelationship(
  collectionHandle: string | number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function addPredecessor(
    dataobjectId: number,
    newPredecessorDataObjectId: number,
  ) {
    loading.value = true;

    let dataObject: DataObject | undefined = undefined;
    try {
      dataObject = await getDataObjectV2(collectionHandle, dataobjectId);
    } catch (error) {
      dataObject = undefined;
      handleError(error, "getDataObject");
    }

    if (dataObject == undefined) {
      loading.value = false;
      return;
    }

    if (!dataObject.predecessorIds) {
      dataObject.predecessorIds = [];
    }

    if (dataObject.predecessorIds.includes(newPredecessorDataObjectId)) {
      handleError(
        `There already is a relationship between Data Objects with Id ${dataobjectId} and ${newPredecessorDataObjectId}`,
        "updateDataObject",
      );
      loading.value = false;
      return;
    }

    dataObject.predecessorIds.push(newPredecessorDataObjectId);

    // BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH: also send predecessorAppIds
    // when the new predecessor has a UUID v7 appId so the backend can resolve
    // it even when there is no numeric shepardId.
    const newPredAppId = deriveAppId(newPredecessorDataObjectId);
    const existingAppIds: string[] = (dataObject.predecessorIds ?? [])
      .map(id => deriveAppId(id))
      .filter((a): a is string => a !== undefined);
    const predecessorAppIds =
      newPredAppId !== undefined
        ? [...new Set([...existingAppIds, newPredAppId])]
        : existingAppIds.length > 0
          ? existingAppIds
          : undefined;

    try {
      const patchBody: DataObjectPatchBody = {
        ...dataObject,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          dataObject.predecessorIds.filter(entry => entry !== -1) ?? [],
        ),
      };
      if (predecessorAppIds && predecessorAppIds.length > 0) {
        patchBody.predecessorAppIds = predecessorAppIds;
      }
      await patchDataObjectV2(collectionHandle, dataobjectId, patchBody);
      emitSuccess("Successfully updated data object");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "updateDataObject");
    } finally {
      loading.value = false;
    }
  }

  /**
   * Adds a successor to a data object.
   *
   * There is no way of actually adding a successor, however we can add the current dataobject as a predecessor of the to-be-set successor.
   * @param dataobjectId
   * @param newSuccessorDataObjectId
   */
  async function addSuccessor(
    dataobjectId: number,
    newSuccessorDataObjectId: number,
  ) {
    addPredecessor(newSuccessorDataObjectId, dataobjectId);
  }

  /**
   * Removes a single Data Object id from the predecessor list of an dataobject.
   *
   * @param dataobjectId id of the dataobject the relationship is going out
   * @param toBeDeletedPredecessorId id of the referenced data object
   */
  async function deletePredecessor(
    dataobjectId: number,
    toBeDeletedPredecessorId: number,
  ) {
    loading.value = true;

    let dataObject: DataObject | undefined = undefined;
    try {
      dataObject = await getDataObjectV2(collectionHandle, dataobjectId);
    } catch (error) {
      dataObject = undefined;
      handleError(error, "getDataObject");
    }

    if (dataObject == undefined) {
      loading.value = false;
      return;
    }

    if (!dataObject.predecessorIds || dataObject.predecessorIds.length === 0) {
      handleError(
        `Cannot remove predecessor. DataObject with Id ${dataobjectId} does not have any set predecessors.`,
        "updateDataObject",
      );
      loading.value = false;
      return;
    }

    const updatedPredecessorList = dataObject.predecessorIds.filter(
      id => id !== toBeDeletedPredecessorId,
    );

    // BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH: rebuild predecessorAppIds from
    // the updated list, excluding the deleted predecessor's appId.
    const deletedPredAppId = deriveAppId(toBeDeletedPredecessorId);
    const updatedPredAppIds: string[] = updatedPredecessorList
      .map(id => deriveAppId(id))
      .filter((a): a is string => a !== undefined)
      .filter(a => a !== deletedPredAppId);

    try {
      const patchBody: DataObjectPatchBody = {
        ...dataObject,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          updatedPredecessorList.filter(entry => entry !== -1) ?? [],
        ),
      };
      if (updatedPredAppIds.length > 0) {
        patchBody.predecessorAppIds = updatedPredAppIds;
      }
      await patchDataObjectV2(collectionHandle, dataobjectId, patchBody);
      emitSuccess("Successfully updated data object");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "updateDataObject");
    } finally {
      loading.value = false;
    }
  }

  async function deleteSuccessor(
    dataobjectId: number,
    toBeDeletedSuccessorId: number,
  ) {
    deletePredecessor(toBeDeletedSuccessorId, dataobjectId);
  }

  return {
    addPredecessor,
    addSuccessor,
    deletePredecessor,
    deleteSuccessor,
    loading,
  };
}
