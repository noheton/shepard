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
 * Predecessor body shape note: `predecessorIds` is a list of numeric ids. If
 * the predecessor target itself is post-reset and has no numeric id, the
 * body write will fail server-side — that is a separate concern tracked at
 * `BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH` (filed in aidocs/16). This route
 * migration is necessary but not sufficient for the rework UI flow until the
 * follow-up lands.
 */

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
  body: Partial<DataObject>,
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

    try {
      await patchDataObjectV2(collectionHandle, dataobjectId, {
        ...dataObject,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          dataObject.predecessorIds.filter(entry => entry !== -1) ?? [],
        ),
      });
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

    try {
      await patchDataObjectV2(collectionHandle, dataobjectId, {
        ...dataObject,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          updatedPredecessorList.filter(entry => entry !== -1) ?? [],
        ),
      });
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
