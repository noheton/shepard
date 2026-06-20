/**
 * MFFD-NDT-GRID-1 — cheap "should we render the widget?" probe.
 *
 * Collection landing pages must NOT mount the full 14x14 grid for
 * collections that have no thermography data, per
 * `feedback_basic_advanced_superset.md` -- "DO NOT add it
 * unconditionally -- it would clutter every Collection".
 *
 * The probe fetches the collection's DO list (v2 paginated) and samples
 * the first PROBE_SAMPLE_SIZE DataObjects' annotations in parallel via
 * GET /v2/semantic/annotations?subjectAppId=...&subjectKind=DataObject.
 * If any sampled DO carries `urn:shepard:mffd:section`, the probe
 * flips `hasData` to true and the parent renders the widget; the
 * widget itself then performs the full per-DO fetch.
 *
 * V2UI-MFFD-NDT-ANNO-V2: migrated from useShepardApi(DataObjectApi) /
 * getAllDataObjectAnnotations (v1, numeric ids) to
 * useV2ShepardApi(DataObjectsApi) / listDataObjects (v2, collectionAppId)
 * and useV2ShepardApi(SemanticAnnotationsApi) / listAnnotations
 * (v2, subjectAppId).
 */
import {
  DataObjectsApi,
  SemanticAnnotationsApi,
  type AnnotationV2,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";
import { annotationsContainSection } from "~/utils/mffdNdtGrid";

const PROBE_SAMPLE_SIZE = 5;

function annotationV2ToLegacy(item: AnnotationV2, fakeId: number): SemanticAnnotation {
  return {
    id: fakeId,
    name: item.propertyName ?? item.predicateLabel ?? "",
    propertyName: item.propertyName ?? item.predicateLabel ?? "",
    propertyIRI: item.propertyIri ?? item.predicateIri ?? "",
    valueName: item.valueName ?? item.objectLiteral ?? "",
    valueIRI: item.valueIri ?? item.objectIri ?? "",
    propertyRepositoryId: 0,
    valueRepositoryId: 0,
  };
}

interface ProbeCacheEntry {
  hasData: Ref<boolean | null>;
  inFlight: Promise<void> | null;
}

const cache = new Map<string, ProbeCacheEntry>();

export function useMffdNdtGridProbe(collectionAppId: Ref<string | null>) {
  const hasData = computed<boolean | null>(() => {
    const id = collectionAppId.value;
    if (id === null) return null;
    return cache.get(id)?.hasData.value ?? null;
  });

  async function probe(): Promise<void> {
    const id = collectionAppId.value;
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

    const dataObjectApi = useV2ShepardApi(DataObjectsApi);
    const annotationApi = useV2ShepardApi(SemanticAnnotationsApi);

    entry.inFlight = (async () => {
      try {
        const dos = await dataObjectApi.value.listDataObjects({
          collectionAppId: id,
          page: 0,
          pageSize: PROBE_SAMPLE_SIZE,
        });
        if (dos.length === 0) {
          entry!.hasData.value = false;
          return;
        }
        const sample = dos.slice(0, PROBE_SAMPLE_SIZE).filter(d => !!d.appId);
        const results = await Promise.allSettled(
          sample.map(d =>
            annotationApi.value.listAnnotations({
              subjectAppId: d.appId!,
              subjectKind: "DataObject",
              pageSize: 50,
            }),
          ),
        );
        let found = false;
        for (const r of results) {
          if (r.status === "fulfilled") {
            const legacyAnns = r.value.map((a, i) => annotationV2ToLegacy(a, i));
            if (annotationsContainSection(legacyAnns)) {
              found = true;
              break;
            }
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
    collectionAppId,
    () => {
      void probe();
    },
    { immediate: true },
  );

  return { hasData, probe };
}
