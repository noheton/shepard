import type { ResponseError } from "@dlr-shepard/backend-client";
import type { UpdatedDataObject } from "./updatedDataObject";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): DataObject PATCH routes through
 * `PATCH /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`
 * (RFC 7396 JSON Merge Patch). The generated v1 `updateDataObject` expects
 * numeric Neo4j longs in the path — post-Neo4j-reset DataObjects carry
 * UUID v7 only, so edit-dialog "Save" silently 404'd. The v2 EntityIdResolver
 * accepts UUID v7 or numeric stringified id transparently.
 *
 * Cross-reference for predecessor body shape: see
 * `BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH` in aidocs/16 — the wire shape
 * still uses `predecessorIds: long[]`; route migration is necessary but
 * not sufficient for the rework flow until a `predecessorAppIds` companion
 * lands. The PATCH here forwards the same numeric ids the v1 client sent.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useEditDataObject(
  // V2-SWEEP Wave 1: appId (UUID v7) strings or legacy numerics — both
  // stringify into the v2 path, resolved by the backend EntityIdResolver.
  collectionId: number | string,
  dataObjectId: number | string,
  isValid: Ref<boolean>,
  onSuccess: () => void,
) {
  const updatedDataObject = ref<UpdatedDataObject | undefined>(undefined);

  // BUG-COLL-APPID-ROUTE-002: useFetchDataObject now takes string ids and
  // hits v2. The numeric id stringifies cleanly for both UUID v7 (already
  // string-shaped) and legacy long form callers; the v2 EntityIdResolver
  // accepts either shape.
  const { dataObject } = useFetchDataObject(
    String(collectionId),
    String(dataObjectId),
  );
  const loading = computed(() => !dataObject && !updatedDataObject);
  watch(dataObject, newDo => {
    if (newDo) {
      // LIC1: V2-SWEEP-001-CLIENT-REGEN — the regenerated `DataObject` model
      // now exposes license / accessRights as typed top-level fields, so the
      // previous defensive `unknown`-shaped read is gone.
      updatedDataObject.value = {
        name: newDo.name,
        parentId: newDo.parentId,
        attributes: newDo.attributes ?? {},
        description: newDo.description,
        predecessorIds: newDo.predecessorIds ?? [],
        status: newDo.status ?? null,
        license: newDo.license ?? null,
        accessRights: newDo.accessRights ?? null,
      };
    }
  });

  async function saveChanges() {
    const dataObjectToSave = updatedDataObject.value;
    if (dataObjectToSave === undefined) return;
    if (isValid.value === false) return;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    const url =
      `${v2BaseUrl()}/v2/collections/` +
      `${encodeURIComponent(String(collectionId))}/data-objects/` +
      `${encodeURIComponent(String(dataObjectId))}`;
    const headers: Record<string, string> = {
      "Content-Type": "application/merge-patch+json",
      Accept: "application/json",
    };
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const body = {
      ...dataObjectToSave,
      predecessorIds: uniqueNumbersOf(
        // clean up possible remaining placeholder entries
        dataObjectToSave.predecessorIds?.filter(entry => entry !== -1) ?? [],
      ),
    };

    try {
      const resp = await fetch(url, {
        method: "PATCH",
        headers,
        body: JSON.stringify(body),
      });
      if (!resp.ok) {
        throw {
          response: resp,
          message: `HTTP ${resp.status}`,
        } as unknown as ResponseError;
      }
      emitSuccess(`Successfully updated data object "${dataObjectToSave.name}"`);
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error as ResponseError, "updateDataObject");
    }
  }

  return {
    updatedDataObject,
    loading,
    saveChanges,
  };
}
