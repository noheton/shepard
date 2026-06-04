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

// BUG-COLL-APPID-ROUTE-007-REFPAGE: accept the numeric ids as a plain number, a
// Ref, or a getter and resolve them at fetch time. The reference detail page's
// route params are now the v2 appId (UUID), so the NUMERIC ids these v1
// `/shepard/api/...` endpoints require only become available once the loaded v2
// entities resolve. The composable defers its first fetch until all three ids
// are present and re-fetches when they appear.
export function useFetchTimeseriesReference(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
  timeseriesReferenceIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const timeseriesReference = ref<
    TimeseriesReferenceWithContainerMeta | undefined
  >(undefined);
  // UU1 — UI-404-NICE-EMPTY-STATE: 404 → render `EntityNotFound`, not a toast.
  const notFound = ref<boolean>(false);

  function ids():
    | { collectionId: number; dataObjectId: number; timeseriesReferenceId: number }
    | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    const timeseriesReferenceId = toValue(timeseriesReferenceIdInput);
    if (collectionId == null || dataObjectId == null || timeseriesReferenceId == null)
      return undefined;
    return { collectionId, dataObjectId, timeseriesReferenceId };
  }

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

  // No-arg refresh using resolved ids — call this from onTimeReferenceUpdated
  // and other page-level triggers without re-threading the raw ids.
  function refresh() {
    const resolved = ids();
    if (resolved)
      fetchTimeseriesReference(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.timeseriesReferenceId,
      );
  }

  // Fetch once all ids are resolvable; re-fetch when they first appear (the
  // route-param-is-appId case where the numeric ids arrive after the v2 load).
  watch(ids, resolved => {
    if (resolved)
      fetchTimeseriesReference(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.timeseriesReferenceId,
      );
  }, { immediate: true });

  return {
    timeseriesReference,
    notFound,
    refresh,
  };
}
