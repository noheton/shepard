import {
  StructuredDataContainerApi,
  StructuredDataReferenceApi,
  type ResponseError,
  type StructuredData,
  type StructuredDataPayload,
} from "@dlr-shepard/backend-client";
import type { ReferencedContainerMeta } from "~/components/context/display-components/data-references/dataReference";
import type {
  StructuredDataMeta,
  StructuredDataReferenceWithContainerMeta,
} from "~/components/context/display-components/structured-data-references/structuredDataReferenceTypes";
import { useShepardApi } from "../common/api/useShepardApi";

// BUG-COLL-APPID-ROUTE-007-REFPAGE: accept the numeric ids as a plain number, a
// Ref, or a getter and resolve them at fetch time. The reference detail page's
// route params are now the v2 appId (UUID), so the NUMERIC ids these v1
// `/shepard/api/...` endpoints require only become available once the loaded v2
// entities resolve. The composable defers its first fetch until all three ids
// are present and re-fetches when they appear.
export function useFetchStructuredDataReference(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
  structuredDataReferenceIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const structuredDataReference = ref<
    StructuredDataReferenceWithContainerMeta | undefined
  >(undefined);
  const structuredData = ref<StructuredDataMeta[]>([]);
  // UU1 — UI-404-NICE-EMPTY-STATE: 404 → render `EntityNotFound`, not a toast.
  const notFound = ref<boolean>(false);

  function ids():
    | {
        collectionId: number;
        dataObjectId: number;
        structuredDataReferenceId: number;
      }
    | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    const structuredDataReferenceId = toValue(structuredDataReferenceIdInput);
    if (
      collectionId == null ||
      dataObjectId == null ||
      structuredDataReferenceId == null
    )
      return undefined;
    return { collectionId, dataObjectId, structuredDataReferenceId };
  }

  async function fetchStructuredDataReference(
    collectionId: number,
    dataObjectId: number,
    structuredDataReferenceId: number,
  ) {
    notFound.value = false;
    useShepardApi(StructuredDataReferenceApi)
      .value.getStructuredDataReference({
        collectionId,
        dataObjectId,
        structuredDataReferenceId,
      })
      .then(async response => {
        const structuredDataContainerMeta =
          await fetchStructuredDataContainerMeta(
            response.structuredDataContainerId,
          );
        structuredDataReference.value = {
          ...response,
          ...structuredDataContainerMeta,
        };
      })
      .catch(error => {
        structuredDataReference.value = undefined;
        if ((error as ResponseError)?.response?.status === 404) {
          notFound.value = true;
          return;
        }
        handleError(error, "getStructuredDataReference");
      });
  }

  async function fetchStructuredDataContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return useShepardApi(StructuredDataContainerApi)
      .value.getStructuredDataContainer({
        structuredDataContainerId: containerId,
      })
      .then((response): ReferencedContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden" };
        handleError(error, "fetchStructuredDataContainerName");
        return { referencedContainerAvailability: "error" };
      });
  }

  async function fetchReferencedStructuredDataPayload(
    collectionId: number,
    dataObjectId: number,
    structuredDataReferenceId: number,
  ): Promise<StructuredDataPayload[]> {
    return useShepardApi(StructuredDataReferenceApi)
      .value.getStructuredDataPayload({
        collectionId,
        dataObjectId,
        structuredDataReferenceId,
      })
      .catch(error => {
        handleError(error, "getStructuredData");
        return [];
      });
  }

  async function fetchExistingStructuredDataInContainer(
    structuredDataContainerId: number,
  ): Promise<StructuredData[]> {
    return useShepardApi(StructuredDataContainerApi)
      .value.getAllStructuredDatas({ structuredDataContainerId })
      .catch(error => {
        handleError(error, "getStructuredDatas");
        return [];
      });
  }

  watch(structuredDataReference, async () => {
    const resolved = ids();
    if (
      resolved &&
      structuredDataReference.value &&
      !isDeleted(structuredDataReference.value.structuredDataContainerId) &&
      structuredDataReference.value.referencedContainerAvailability ===
        "available"
    ) {
      const [referencedStructuredDataPayload, existingStructuredData] =
        await Promise.all([
          fetchReferencedStructuredDataPayload(
            resolved.collectionId,
            resolved.dataObjectId,
            resolved.structuredDataReferenceId,
          ),
          fetchExistingStructuredDataInContainer(
            structuredDataReference.value.structuredDataContainerId,
          ),
        ]);

      const existingStructuredDataOids = existingStructuredData.map(
        structuredData => structuredData.oid,
      );
      const referencedStructuredDataWithMeta =
        referencedStructuredDataPayload.map(structuredDataPayload => {
          return {
            payload: structuredDataPayload.payload ?? "No Payload",
            ...structuredDataPayload.structuredData,
            availability: existingStructuredDataOids.includes(
              structuredDataPayload.structuredData?.oid,
            )
              ? ("available" as StructuredDataMeta["availability"])
              : ("deleted" as StructuredDataMeta["availability"]),
          } as StructuredDataMeta;
        });
      structuredData.value = referencedStructuredDataWithMeta;
    }
  });

  // No-arg refresh using resolved ids — called from page-level event handlers.
  async function refreshStructuredData() {
    const resolved = ids();
    if (resolved)
      await fetchStructuredDataReference(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.structuredDataReferenceId,
      );
  }

  // Fetch once all ids are resolvable; re-fetch when they first appear (the
  // route-param-is-appId case where the numeric ids arrive after the v2 load).
  watch(ids, resolved => {
    if (resolved)
      fetchStructuredDataReference(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.structuredDataReferenceId,
      );
  }, { immediate: true });

  return {
    structuredDataReference,
    structuredData,
    notFound,
    refreshStructuredData,
  };
}
