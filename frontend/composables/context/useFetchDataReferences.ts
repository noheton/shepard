import type {
  FileReference,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";
import type {
  DataReference,
  ReferencedContainerMeta,
} from "~/components/context/display-components/data-references/dataReference";
import { v2BaseUrl } from "../container/createV2Container";

// REFS-V2-PANELS (TS slice + file/structured migration): fetch a DataObject's
// references through the unified v2 endpoint
//   GET /v2/references?kind={timeseries|bundle|structured-data}&dataObjectAppId={appId}
// keyed by the DataObject's appId (UUID v7) — NOT the numeric Neo4j id.
//
// Why this rewrite: the v2 DataObject/Collection detail responses deliberately
// suppress the numeric `id` (@JsonIgnoreProperties({"id"}); tested invariant in
// DataObjectV2IOIdSuppressionTest / ContainerSummaryIOIdAbsentTest). The former
// v1 fetch (getAllTimeseriesReferences({collectionId:number, dataObjectId:number}))
// therefore never fired — the numeric ids were `undefined` — and the reference
// panels rendered empty. The appId-keyed v2 list endpoint resolves directly from
// the route param / loaded entity, with no numeric id needed.
//
// The page consumes `DataReference` = (v1 reference shape) & container-meta and
// maps it through dataTableElementMappingUtil.ts. We therefore flatten the v2
// `payload` onto the top level so the v1-shaped fields the util reads
// (timeseriesContainerId, timeseries, start, end, fileContainerId,
// structuredDataContainerId, …) stay top-level, carry `appId`, and tag each
// element with a stable `__refKind` discriminator the util dispatches on (the
// generated instanceOf* guards require a numeric `id` that v2 refs never carry,
// so they can no longer classify these rows — see dataTableElementMappingUtil).

/** Stable per-kind discriminator the mapping util dispatches on. */
export type DataReferenceKind = "timeseries" | "bundle" | "structured-data";

/** v2 ReferenceV2IO list element (subset of fields we read). */
interface ReferenceV2IO {
  appId?: string | null;
  name?: string;
  kind?: string;
  createdAt?: string | null;
  createdBy?: string | null;
  payload?: Record<string, unknown> | null;
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

export function useDataReferencesByDataObject(
  dataObjectAppIdInput: MaybeRefOrGetter<string | undefined>,
) {
  const dataReferences = ref<Array<DataReference> | undefined>(undefined);

  function dataObjectAppId(): string | undefined {
    const appId = toValue(dataObjectAppIdInput);
    if (appId == null || appId === "") return undefined;
    return appId;
  }

  /** Raw-fetch the v2 unified reference list for one kind. Fail-soft → []. */
  async function listReferencesOfKind(
    kind: DataReferenceKind,
    appId: string,
  ): Promise<ReferenceV2IO[]> {
    try {
      const resp = await fetch(
        `${v2BaseUrl()}/v2/references?kind=${encodeURIComponent(kind)}` +
          `&dataObjectAppId=${encodeURIComponent(appId)}`,
        { method: "GET", headers: authHeaders() },
      );
      if (!resp.ok) {
        handleError(
          new Error(`HTTP ${resp.status}`),
          `fetch ${kind} references`,
        );
        return [];
      }
      const body = (await resp.json()) as unknown;
      return Array.isArray(body) ? (body as ReferenceV2IO[]) : [];
    } catch (error) {
      handleError(error, `fetch ${kind} references`);
      return [];
    }
  }

  // ── per-kind flatten: v2 payload → v1-shaped DataReference top-level ──────

  function flattenTimeseries(io: ReferenceV2IO): DataReferenceWithKind {
    const p = io.payload ?? {};
    return {
      __refKind: "timeseries",
      id: undefined,
      appId: io.appId ?? undefined,
      name: io.name ?? "",
      createdAt: io.createdAt ? new Date(io.createdAt) : new Date(0),
      createdBy: io.createdBy ?? "—",
      start: (p.start as number) ?? 0,
      end: (p.end as number) ?? 0,
      timeseries: (p.timeseries as TimeseriesReference["timeseries"]) ?? [],
      timeseriesContainerId: (p.timeseriesContainerId as number) ?? 0,
      timeseriesContainerAppId:
        (p.timeseriesContainerAppId as string | null) ?? null,
      qualityScore: (p.qualityScore as number | undefined) ?? undefined,
    } as DataReferenceWithKind;
  }

  function flattenBundle(io: ReferenceV2IO): DataReferenceWithKind {
    const p = io.payload ?? {};
    return {
      __refKind: "bundle",
      id: undefined,
      appId: io.appId ?? undefined,
      name: io.name ?? "",
      createdAt: io.createdAt ? new Date(io.createdAt) : new Date(0),
      createdBy: io.createdBy ?? "—",
      // The page's image-bundle / thermography panes detect bundles via
      // `"fileContainerId" in r` then read `.appId`. v2 bundle payload carries
      // containerAppId (not a numeric id), so we keep the discriminator key
      // present (0) and carry the container appId for meta resolution.
      fileContainerId: 0,
      fileContainerAppId: (p.containerAppId as string | null) ?? null,
      // v1 FileReference.fileOids drives the "Files: N" meta cell; v2 bundle
      // payload exposes only the count, so synthesise an array of that length.
      fileOids: makeOidPlaceholders((p.fileCount as number | undefined) ?? 0),
    } as DataReferenceWithKind;
  }

  function flattenStructured(io: ReferenceV2IO): DataReferenceWithKind {
    const p = io.payload ?? {};
    const oids = Array.isArray(p.structuredDataOids)
      ? (p.structuredDataOids as string[])
      : [];
    return {
      __refKind: "structured-data",
      id: undefined,
      appId: io.appId ?? undefined,
      name: io.name ?? "",
      createdAt: io.createdAt ? new Date(io.createdAt) : new Date(0),
      createdBy: io.createdBy ?? "—",
      // Same key-presence detection contract as bundle above.
      structuredDataContainerId: 0,
      structuredDataContainerAppId:
        (p.structuredDataContainerAppId as string | null) ?? null,
      structuredDataOids: oids,
    } as DataReferenceWithKind;
  }

  // ── container meta resolution (v2 appId-keyed /v2/containers/{appId}) ─────

  async function fetchContainerMeta(
    containerAppId: string | null | undefined,
  ): Promise<ReferencedContainerMeta> {
    if (!containerAppId) return { referencedContainerAvailability: "deleted" };
    try {
      const resp = await fetch(
        `${v2BaseUrl()}/v2/containers/${encodeURIComponent(containerAppId)}`,
        { method: "GET", headers: authHeaders() },
      );
      if (resp.status === 403)
        return { referencedContainerAvailability: "forbidden" };
      if (resp.status === 404)
        return { referencedContainerAvailability: "deleted" };
      if (!resp.ok) {
        handleError(new Error(`HTTP ${resp.status}`), "fetch container name");
        return { referencedContainerAvailability: "error" };
      }
      const body = (await resp.json()) as { name?: string };
      return {
        referencedContainerName: body.name ?? "",
        referencedContainerAvailability: "available",
      };
    } catch (error) {
      handleError(error, "fetch container name");
      return { referencedContainerAvailability: "error" };
    }
  }

  async function addContainerMeta(
    ref: DataReferenceWithKind,
  ): Promise<DataReference> {
    let containerAppId: string | null | undefined;
    if (ref.__refKind === "timeseries") {
      containerAppId = (ref as { timeseriesContainerAppId?: string | null })
        .timeseriesContainerAppId;
    } else if (ref.__refKind === "bundle") {
      containerAppId = (ref as { fileContainerAppId?: string | null })
        .fileContainerAppId;
    } else {
      containerAppId = (ref as { structuredDataContainerAppId?: string | null })
        .structuredDataContainerAppId;
    }
    return {
      ...ref,
      ...(await fetchContainerMeta(containerAppId)),
    } as unknown as DataReference;
  }

  async function fetchAndMergeReferences() {
    const appId = dataObjectAppId();
    if (!appId) return;
    const [timeseries, bundles, structured] = await Promise.all([
      listReferencesOfKind("timeseries", appId),
      listReferencesOfKind("bundle", appId),
      listReferencesOfKind("structured-data", appId),
    ]);
    const flattened: DataReferenceWithKind[] = [
      ...timeseries.map(flattenTimeseries),
      ...bundles.map(flattenBundle),
      ...structured.map(flattenStructured),
    ];
    dataReferences.value = await Promise.all(flattened.map(addContainerMeta));
  }

  // Fetch once the appId is resolvable; re-fetch when it first appears (the
  // route-param-is-appId case where the loaded v2 entity arrives after mount).
  watch(dataObjectAppId, appId => {
    if (appId) fetchAndMergeReferences();
  }, { immediate: true });

  onDataObjectUpdated(fetchAndMergeReferences);

  return { dataReferences };
}

/** Build N placeholder OID strings so `.length` meta renders correctly. */
function makeOidPlaceholders(count: number): string[] {
  if (!count || count < 0) return [];
  return Array.from({ length: count }, (_, i) => `oid-${i}`);
}

/**
 * A flattened v2 reference carrying the stable `__refKind` discriminator the
 * mapping util dispatches on. Structurally a superset of the v1 reference
 * shapes (numeric `id` is always undefined for v2-sourced rows).
 */
type DataReferenceWithKind = (
  | Partial<TimeseriesReference>
  | Partial<FileReference>
  | Partial<StructuredDataReference>
) & {
  __refKind: DataReferenceKind;
  appId?: string;
  name: string;
  createdAt: Date;
  createdBy: string;
};
