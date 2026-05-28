/**
 * MFFD-NDT-GRID-1 — cheap "should we render the widget?" probe.
 *
 * Collection landing pages must NOT mount the full 14x14 grid for
 * collections that have no thermography data, per
 * `feedback_basic_advanced_superset.md` -- "DO NOT add it
 * unconditionally -- it would clutter every Collection".
 *
 * The probe fetches the collection's DO list (1 request) and samples
 * the first PROBE_SAMPLE_SIZE DataObjects' annotations in parallel.
 * If any sampled DO carries `urn:shepard:mffd:section`, the probe
 * flips `hasData` to true and the parent renders the widget; the
 * widget itself then performs the full per-DO fetch.
 *
 * Justification for the heuristic: in practice, OTvis campaigns
 * upload many DOs into a collection -- if thermography is present
 * at all, it's overwhelmingly likely to be in the first batch (the
 * DO list is order-by-id which roughly mirrors creation order). A
 * false negative would silently hide the widget for a collection
 * that has thermography data only beyond the first PROBE_SAMPLE_SIZE
 * DOs; the operator workaround is to scroll the DO list and confirm
 * by hand.
 *
 * Tests are not exercised at the composable level (it's a thin
 * wrapper around the API client); the pure helpers it relies on
 * (annotationsContainSection) have full unit coverage in
 * `tests/unit/mffdNdtGrid.test.ts`.
 */
import {
  DataObjectApi,
  SemanticAnnotationApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { annotationsContainSection } from "~/utils/mffdNdtGrid";

const PROBE_SAMPLE_SIZE = 5;

interface ProbeCacheEntry {
  hasData: Ref<boolean | null>;
  inFlight: Promise<void> | null;
}

const cache = new Map<number, ProbeCacheEntry>();

export function useMffdNdtGridProbe(collectionId: Ref<number | null>) {
  const hasData = computed<boolean | null>(() => {
    const id = collectionId.value;
    if (id === null) return null;
    return cache.get(id)?.hasData.value ?? null;
  });

  async function probe(): Promise<void> {
    const id = collectionId.value;
    if (id === null) return;
    let entry = cache.get(id);
    if (!entry) {
      entry = {
        hasData: ref<boolean | null>(null),
        inFlight: null,
      };
      cache.set(id, entry);
    }
    if (entry.inFlight) return entry.inFlight;
    if (entry.hasData.value !== null) return;

    const dataObjectApi = useShepardApi(DataObjectApi);
    const annotationApi = useShepardApi(SemanticAnnotationApi);

    entry.inFlight = (async () => {
      try {
        const dos = await dataObjectApi.value.getAllDataObjects({
          collectionId: id,
        });
        if (dos.length === 0) {
          entry!.hasData.value = false;
          return;
        }
        const sample = dos.slice(0, PROBE_SAMPLE_SIZE);
        const results = await Promise.allSettled(
          sample.map(d =>
            annotationApi.value.getAllDataObjectAnnotations({
              collectionId: id,
              dataObjectId: d.id,
            }),
          ),
        );
        let found = false;
        for (const r of results) {
          if (r.status === "fulfilled" && annotationsContainSection(r.value)) {
            found = true;
            break;
          }
        }
        entry!.hasData.value = found;
      } catch {
        // Treat a probe failure as "no data" -- the widget stays
        // hidden, which is the safe default. The operator can still
        // navigate to a DO and inspect annotations directly.
        entry!.hasData.value = false;
      } finally {
        entry!.inFlight = null;
      }
    })();
    return entry.inFlight;
  }

  watch(
    collectionId,
    () => {
      void probe();
    },
    { immediate: true },
  );

  return { hasData, probe };
}
