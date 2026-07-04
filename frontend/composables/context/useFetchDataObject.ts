import type { DataObject, ResponseError } from "@dlr-shepard/backend-client";

// The DataObject domain model defines the description to be nullable.
// In the frontend we handle that here to be sure that it is an empty string
// instead of null or undefined.
// That simplifies life if we bind the description to the text editor component.
// If we decide to handle this within the component, we can remove this type.
export interface DataObjectSanitized extends DataObject {
  description: string;
}

/**
 * BUG-COLL-APPID-ROUTE-002 (2026-05-31): single DataObject fetch via the v2
 * appId-keyed endpoint `GET /v2/collections/{collectionAppId}/data-objects/
 * {dataObjectAppId}` returning `DataObjectDetailV2IO` (a superset of v1
 * `DataObjectIO`, so the existing UI shape contract holds).
 *
 * Pre-fix this composable called the generated v1 `getDataObject({
 * collectionId, dataObjectId })` expecting numeric Neo4j longs. With the
 * L2d wave-5 parser fix (`9adc9df2f`) the route now carries UUID v7 ids
 * straight through — but stringified UUIDs in a v1 numeric path produced
 * a 404, leaving the DataObject detail page in the red-toast / "Not found"
 * state described in `aidocs/agent-findings/ui-scrutinizer-2026-05-31.md`.
 *
 * Numeric route ids continue to work — the v2 GET routes via the same
 * backing `EntityIdResolver` which accepts either shape.
 */

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchDataObjectV2(
  collectionId: string,
  dataObjectId: string,
  accessToken: string,
): Promise<DataObject> {
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(collectionId)}/data-objects/` +
    `${encodeURIComponent(dataObjectId)}`;
  const resp = await fetch(url, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
  });
  if (!resp.ok) {
    throw {
      response: resp,
      message: `HTTP ${resp.status}`,
    } as unknown as ResponseError;
  }
  // DataObjectDetailV2IO extends DataObjectIO — every field on `DataObject`
  // is present, plus richer summaries the page consumes via `as unknown`.
  return (await resp.json()) as DataObject;
}

export function useFetchDataObject(collectionId: string, dataObjectId: string) {
  const isLoading = ref<boolean>(false);
  const dataObject = ref<DataObjectSanitized | undefined>(undefined);
  // UU1 — UI-404-NICE-EMPTY-STATE: distinguish 404 from other errors so the
  // detail page can render `EntityNotFound` instead of surfacing a red toast.
  const notFound = ref<boolean>(false);

  function fetchDataObject() {
    isLoading.value = true;
    notFound.value = false;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      handleError(new Error("Not authenticated"), "getDataObject");
      return;
    }

    fetchDataObjectV2(collectionId, dataObjectId, accessToken)
      .then(response => {
        dataObject.value = {
          ...response,
          description: response.description ?? "",
        };
      })
      .catch(error => {
        dataObject.value = undefined;
        const status = (error as ResponseError)?.response?.status;
        if (status === 404) {
          notFound.value = true;
          return; // suppress the red toast — page renders EntityNotFound
        }
        handleError(error, "getDataObject");
      })
      .finally(() => (isLoading.value = false));
  }

  fetchDataObject();

  onDataObjectUpdated(fetchDataObject);

  return { dataObject, isLoading, notFound };
}
