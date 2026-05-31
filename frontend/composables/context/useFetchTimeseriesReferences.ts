import type {
  ResponseError,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";
import {
  TimeseriesContainerApi,
  TimeseriesReferenceApi,
} from "@dlr-shepard/backend-client";
import type { ReferencedContainerMeta } from "~/components/context/display-components/data-references/dataReference";
import { useShepardApi } from "../common/api/useShepardApi";

type TimeseriesReferenceWithContainerMeta = TimeseriesReference &
  ReferencedContainerMeta;

export function useFetchTimeseriesReference(
  collectionId: number,
  dataObjectId: number,
  timeseriesReferenceId: number,
) {
  const timeseriesReference = ref<
    TimeseriesReferenceWithContainerMeta | undefined
  >(undefined);
  // UU1 — UI-404-NICE-EMPTY-STATE: 404 → render `EntityNotFound`, not a toast.
  const notFound = ref<boolean>(false);

  async function fetchTimeseriesReference(
    collectionId: number,
    dataObjectId: number,
    timeseriesReferenceId: number,
  ) {
    notFound.value = false;
    useShepardApi(TimeseriesReferenceApi)
      .value.getTimeseriesReference({
        collectionId,
        dataObjectId,
        timeseriesReferenceId,
      })
      .then(async response => {
        const timeseriesRefMeta = await fetchTimeseriesContainerMeta(
          response.timeseriesContainerId,
        );
        if (timeseriesRefMeta.referencedContainerAvailability !== "available") {
          response.timeseries = [];
        }
        timeseriesReference.value = {
          ...response,
          ...timeseriesRefMeta,
        };
      })
      .catch(e => {
        timeseriesReference.value = undefined;
        if ((e as ResponseError)?.response?.status === 404) {
          notFound.value = true;
          return;
        }
        handleError(e as ResponseError, "fetching timeseriesReference");
      });
  }

  async function fetchTimeseriesContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return useShepardApi(TimeseriesContainerApi)
      .value.getTimeseriesContainer({ timeseriesContainerId: containerId })
      .then((response): ReferencedContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden" };
        handleError(error, "fetchTimeseriesContainerName");
        return { referencedContainerAvailability: "error" };
      });
  }

  fetchTimeseriesReference(collectionId, dataObjectId, timeseriesReferenceId);

  return {
    timeseriesReference,
    fetchTimeseriesReference,
    notFound,
  };
}
