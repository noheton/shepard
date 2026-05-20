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

export function useFetchStructuredDataReference(
  collectionId: number,
  dataObjectId: number,
  structuredDataReferenceId: number,
) {
  const structuredDataReference = ref<
    StructuredDataReferenceWithContainerMeta | undefined
  >(undefined);
  const structuredData = ref<StructuredDataMeta[]>([]);

  async function fetchStructuredDataReference() {
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
        handleError(error, "getStructuredDataReference");
        structuredDataReference.value = undefined;
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

  async function fetchReferencedStructuredDataPayload(): Promise<
    StructuredDataPayload[]
  > {
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
    if (
      structuredDataReference.value &&
      !isDeleted(structuredDataReference.value.structuredDataContainerId) &&
      structuredDataReference.value.referencedContainerAvailability ===
        "available"
    ) {
      const [referencedStructuredDataPayload, existingStructuredData] =
        await Promise.all([
          fetchReferencedStructuredDataPayload(),
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

  fetchStructuredDataReference();

  return { structuredDataReference, structuredData, refreshStructuredData: fetchStructuredDataReference };
}
